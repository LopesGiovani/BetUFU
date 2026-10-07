#!/usr/bin/env node

import { createHash, createHmac, createDecipheriv, timingSafeEqual } from 'node:crypto';
import { spawn } from 'node:child_process';
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { runIsolatedBackup } from './phase7-backup.mjs';
import { validatePhase7Approvals } from './validate-phase7-approvals.mjs';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const OWNER = 'br.com.betufu.phase7-restore';
const RUN = /^betufu-phase7-restore-[a-f0-9]{16}$/u;
const SUBSTRATES = ['betufu', 'keycloak', 'private-objects'];
const digest = (bytes) => createHash('sha256').update(bytes).digest('hex');

function requireValue(value, message) {
  if (!value) throw new Error(message);
}

export function validateRestorePolicy(policy) {
  requireValue(policy?.scope === 'isolated-local-proof', 'Restore scope is not isolated');
  requireValue(policy?.substrates?.join('|') === SUBSTRATES.join('|'), 'Restore substrates incomplete');
  requireValue(policy.rpo_seconds === 3600 && policy.retention_days === 30, 'Restore policy drift');
  requireValue(policy.encryption === 'AES-256-GCM', 'Restore encryption drift');
  return policy;
}

export function validateRegister(register) {
  requireValue(register?.schema_version === 1, 'Suppression register schema missing');
  requireValue(register?.source === 'privacy_suppression_fact', 'Suppression source is not authoritative');
  requireValue(Array.isArray(register.records), 'Suppression records missing');
  for (const record of register.records) {
    requireValue(
      record &&
        ['COMMON_MESSAGES', 'MODERATION_EVIDENCE'].includes(record.data_class) &&
        /^[0-9a-f-]{36}$/iu.test(record.record_id) &&
        Number.isInteger(record.change_cursor) &&
        record.change_cursor > 0,
      'Invalid suppression record',
    );
  }
  return register;
}

export function decryptComponent(cipher, key, aad) {
  requireValue(Buffer.isBuffer(key) && key.length === 32, 'Missing restore key');
  requireValue(Buffer.isBuffer(cipher) && cipher.length > 28, 'Encrypted component is empty');
  const prefix = cipher.subarray(0, 4).toString();
  requireValue(prefix === 'B7G1', 'Invalid encrypted artifact');
  const iv = cipher.subarray(4, 16);
  const tag = cipher.subarray(cipher.length - 16);
  const body = cipher.subarray(16, cipher.length - 16);
  const decipher = createDecipheriv('aes-256-gcm', key, iv);
  decipher.setAAD(aad);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(body), decipher.final()]);
}

export function authenticateManifest(manifest, key) {
  requireValue(manifest?.authentication, 'Missing restore manifest authentication');
  const { authentication, ...payload } = manifest;
  const expected = createHmac('sha256', key).update(JSON.stringify(payload)).digest('hex');
  requireValue(
    authentication.length === expected.length &&
      timingSafeEqual(Buffer.from(authentication), Buffer.from(expected)),
    'Restore manifest authentication failed',
  );
  return manifest;
}

export function measureRpoRto({ sourceAt, restoredAt, usableAt, rpoSeconds = 3600, rtoSeconds = 14400 }) {
  requireValue(Number.isFinite(sourceAt) && Number.isFinite(restoredAt) && Number.isFinite(usableAt), 'Invalid restore clocks');
  const rpo = (restoredAt - sourceAt) / 1000;
  const rto = (usableAt - restoredAt) / 1000;
  requireValue(rpo >= 0 && rpo <= rpoSeconds, 'RPO exceeded');
  requireValue(rto >= 0 && rto <= rtoSeconds, 'RTO exceeded');
  return { rpo_seconds: rpo, rto_seconds: rto };
}

function dockerArgs(run) {
  return ['compose', '-f', 'infra/backup/compose.phase7-restore.yaml', '--project-name', run];
}

async function command(executable, args, options = {}) {
  return new Promise((resolveCommand, rejectCommand) => {
    const child = spawn(executable, args, {
      cwd: ROOT,
      windowsHide: true,
      shell: false,
      env: { ...process.env, ...(options.env ?? {}) },
      stdio: ['pipe', 'pipe', 'ignore'],
    });
    const output = [];
    let bytes = 0;
    child.stdout.on('data', (chunk) => {
      bytes += chunk.length;
      if (bytes > 512 * 1024 * 1024) child.kill();
      else output.push(chunk);
    });
    child.on('error', () => rejectCommand(new Error(`${executable} could not start`)));
    child.on('close', (code) => {
      if (code === 0) resolveCommand(Buffer.concat(output));
      else rejectCommand(new Error(`${executable} operation failed (exit ${code ?? 'unknown'})`));
    });
    if (options.input) child.stdin.end(options.input);
    else child.stdin.end();
  });
}

async function compose(run, args, input) {
  return command('docker', [...dockerArgs(run), ...args], {
    input,
    env: { RESTORE_RUN: run },
  });
}

async function sql(run, database, statement) {
  return (
    await compose(
      run,
      ['exec', '-T', 'postgres', 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'postgres', '-d', database, '-At', '-c', statement],
    )
  )
    .toString()
    .trim();
}

async function waitForPostgres(run) {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    try {
      await compose(run, ['exec', '-T', 'postgres', 'pg_isready', '-U', 'postgres']);
      return;
    } catch {
      await new Promise((resolveDelay) => setTimeout(resolveDelay, 1000));
    }
  }
  throw new Error('Restored PostgreSQL did not become ready');
}

async function restoreDatabase(run, database, dump) {
  await sql(run, 'postgres', `CREATE DATABASE ${database}`);
  await compose(
    run,
    ['exec', '-T', 'postgres', 'pg_restore', '--no-owner', '--no-acl', '--exit-on-error', '-U', 'postgres', '-d', database],
    dump,
  );
}

async function removeOwned(run) {
  try {
    await compose(run, ['down', '--volumes', '--remove-orphans']);
  } catch {
    // Cleanup is best effort; the run name and compose labels scope every resource.
  }
}

async function restoreSnapshot(policy) {
  validateRestorePolicy(policy);
  const backup = await runIsolatedBackup(policy, { once: true });
  const restoredAt = Date.now();
  const report = backup.report;
  const run = `betufu-phase7-restore-${cryptoRandomHex()}`;
  const sourceDir = backup.directory;
  const snapshotDir = join(sourceDir, 'snapshots', report.snapshot_id);
  const key = await readFile(join(sourceDir, 'keys', 'backup.key'));
  const manifest = JSON.parse(await readFile(join(snapshotDir, 'manifest.json'), 'utf8'));
  authenticateManifest(manifest, key);
  requireValue(manifest.run_id === report.run_id, 'Snapshot run mismatch');
  requireValue(manifest.components.length === 3, 'Snapshot component count mismatch');

  const plain = {};
  for (const component of manifest.components) {
    const cipher = await readFile(join(snapshotDir, component.file));
    requireValue(digest(cipher) === component.cipher_sha256, `${component.name} cipher hash mismatch`);
    const bytes = decryptComponent(cipher, key, Buffer.from(`${manifest.run_id}/${manifest.snapshot_id}/${component.name}`));
    requireValue(digest(bytes) === component.plain_sha256, `${component.name} plaintext hash mismatch`);
    requireValue(bytes.length === component.plain_bytes, `${component.name} plaintext length mismatch`);
    plain[component.name] = bytes;
  }

  const registerPath = join(ROOT, 'docs/phase7-suppression-register.json');
  const registerOriginalBytes = await readFile(registerPath);
  const registerBefore = JSON.parse(registerOriginalBytes.toString('utf8'));
  validateRegister(registerBefore);
  requireValue(registerBefore.contains_live_suppression_records === false, 'Unverified live register cannot be restored');
  let publishedRegister = false;

  await compose(run, ['up', '-d', 'postgres']);
  try {
    await waitForPostgres(run);
    await restoreDatabase(run, 'betufu', plain.betufu);
    await restoreDatabase(run, 'keycloak', plain.keycloak);
    const messageId = await sql(run, 'betufu', "SELECT message_id FROM chat_message ORDER BY change_cursor LIMIT 1");
    const actor = await sql(run, 'betufu', "SELECT identity_id FROM local_identity ORDER BY identity_id LIMIT 1");
    requireValue(/^[0-9a-f-]{36}$/iu.test(messageId) && /^[0-9a-f-]{36}$/iu.test(actor), 'Restored chat identity missing');

    // Capture a new post-snapshot source fact and publish only its sanitized reference.
    const capturedAt = Date.now();
    const register = {
      schema_version: 1,
      status: 'isolated-proof-current',
      source: 'privacy_suppression_fact',
      contract: registerBefore.contract,
      policy_reference: 'conversation:2026-10-06:owner-retention-90-180',
      contains_live_suppression_records: true,
      captured_after_snapshot_at: capturedAt,
      source_snapshot_id: report.snapshot_id,
      records: [
        {
          data_class: 'COMMON_MESSAGES',
          record_id: messageId,
          change_cursor: Number(manifest.last_fact.chat_cursor) + 1,
        },
      ],
      notes: ['Sanitized isolated proof reference; no message body or actor identity is published.'],
    };
    validateRegister(register);
    await writeFile(registerPath, JSON.stringify(register, null, 2) + '\n');
    publishedRegister = true;

    const record = register.records[0];
    const inventory = digest(Buffer.from(`${record.data_class}:${record.record_id}:${record.change_cursor}`));
    await sql(
      run,
      'betufu',
      `INSERT INTO privacy_suppression_fact(data_class,record_id,actor_identity_id,inventory_digest,policy_reference,change_cursor)
       VALUES ('${record.data_class}','${record.record_id}','${actor}','${inventory}','${register.policy_reference}',${record.change_cursor})`,
    );
    await sql(
      run,
      'betufu',
      `INSERT INTO chat_privacy_suppression_projection(data_class,message_id,change_cursor)
       VALUES ('${record.data_class}','${record.record_id}',${record.change_cursor}) ON CONFLICT DO NOTHING`,
    );
    const served = await sql(
      run,
      'betufu',
      `SELECT count(*) FROM chat_message m LEFT JOIN chat_privacy_suppression_projection s ON s.message_id=m.message_id WHERE m.message_id='${record.record_id}' AND s.message_id IS NULL`,
    );
    requireValue(served === '0', 'Suppressed post-snapshot message would be served');

    await compose(run, ['up', '-d', 'seaweedfs', 'keycloak']);
    const objectListing = await compose(run, ['exec', '-T', 'seaweedfs', 'sh', '-c', 'find /data -type f | head -1']);
    requireValue(objectListing.toString().trim().length > 0, 'Private object substrate restored empty');
    const keycloakUsers = await sql(run, 'keycloak', "SELECT count(*) FROM user_entity");
    requireValue(Number(keycloakUsers) > 0, 'Restored Keycloak users missing');
    const usableAt = Date.now();
    const timings = measureRpoRto({
      sourceAt: Number(manifest.snapshot_at),
      restoredAt,
      usableAt,
      rpoSeconds: policy.rpo_seconds,
      rtoSeconds: 14400,
    });
    const result = {
      schema_version: 1,
      scope: policy.scope,
      run_id: run,
      status: 'passed',
      source_run_id: report.run_id,
      snapshot_id: report.snapshot_id,
      substrates: {
        betufu: { restored: true, chat_facts: Number(await sql(run, 'betufu', 'SELECT count(*) FROM chat_message')) },
        keycloak: { restored: true, users: Number(keycloakUsers) },
        'private-objects': { restored: true, nonempty: true },
      },
      suppression: {
        register_current: true,
        captured_after_snapshot_at: capturedAt,
        applied_before_serving: true,
        suppressed_record_id: record.record_id,
        served_after_reconciliation: Number(served),
      },
      rpo: timings.rpo_seconds,
      rto: timings.rto_seconds,
      serving_state: 'released-after-independent-register-reconciliation',
      limitations: [
        'Isolated local proof; production provider, key custody and cloud availability remain pending.',
        'Keycloak database restoration and user presence are proven; no password is copied into the report.',
      ],
      cleanup: 'only-owned-compose-project-volumes-and-network',
    };
    await writeFile(join(ROOT, 'docs/phase7-restore-report.json'), JSON.stringify(result, null, 2) + '\n');
    return result;
  } finally {
    await removeOwned(run);
    if (!publishedRegister) await writeFile(registerPath, registerOriginalBytes);
  }
}

function cryptoRandomHex() {
  return createHash('sha256').update(`${Date.now()}-${Math.random()}`).digest('hex').slice(0, 16);
}

async function main(args) {
  requireValue(
    args.length === 3 && args[0] === '--policy' && args[1] === 'infra/backup/phase7-backup-policy.json' && args[2] === '--isolated',
    'Only approved isolated restore command supported',
  );
  await validatePhase7Approvals({ plan: '07-29', stage: 'implementation' });
  const policy = JSON.parse(await readFile(join(ROOT, args[1]), 'utf8'));
  const result = await restoreSnapshot(policy);
  console.log(`[phase7-restore] Passed; ${result.substrates.betufu.chat_facts} chat facts restored and suppression reapplied before serving`);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main(process.argv.slice(2)).catch((error) => {
    console.error(`[phase7-restore] ALERT: ${error.message}`);
    process.exitCode = 1;
  });
}
