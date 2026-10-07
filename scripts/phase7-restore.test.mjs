import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createHmac, randomBytes } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import {
  authenticateManifest,
  decryptComponent,
  measureRpoRto,
  validateRegister,
  validateRestorePolicy,
} from './phase7-restore.mjs';

const policy = JSON.parse(await readFile('infra/backup/phase7-backup-policy.json'));
const register = JSON.parse(await readFile('docs/phase7-suppression-register.json'));

test('restore policy remains isolated and covers every substrate', () => {
  assert.equal(validateRestorePolicy(policy).scope, 'isolated-local-proof');
  assert.throws(() => validateRestorePolicy({ ...policy, rpo_seconds: 3601 }));
  assert.throws(() => validateRestorePolicy({ ...policy, substrates: ['betufu'] }));
});

test('suppression register rejects missing or malformed current facts', () => {
  assert.equal(validateRegister(register).schema_version, 1);
  assert.throws(() => validateRegister({ ...register, records: [{ data_class: 'UNKNOWN' }] }));
  assert.throws(() => validateRegister({ ...register, source: 'restored-database' }));
});

test('AES-GCM restore decrypts exact bytes and rejects tampering', () => {
  const key = randomBytes(32);
  const plain = Buffer.from('restored append-only fact');
  const iv = randomBytes(12);
  const cipher = (async () => {
    const { createCipheriv } = await import('node:crypto');
    const c = createCipheriv('aes-256-gcm', key, iv);
    c.setAAD(Buffer.from('run/snapshot/betufu'));
    return Buffer.concat([Buffer.from('B7G1'), iv, c.update(plain), c.final(), c.getAuthTag()]);
  })();
  return cipher.then((encrypted) => {
    assert.deepEqual(
      decryptComponent(encrypted, key, Buffer.from('run/snapshot/betufu')),
      plain,
    );
    encrypted[encrypted.length - 1] ^= 1;
    assert.throws(() => decryptComponent(encrypted, key, Buffer.from('run/snapshot/betufu')));
  });
});

test('manifest authentication covers snapshot metadata', () => {
  const key = randomBytes(32);
  const payload = { run_id: 'betufu-phase7-restore-0123456789abcdef', snapshot_id: 'snapshot-0123456789abcdef' };
  const authentication = createHmac('sha256', key).update(JSON.stringify(payload)).digest('hex');
  assert.equal(authenticateManifest({ ...payload, authentication }, key).run_id, payload.run_id);
  assert.throws(() => authenticateManifest({ ...payload, authentication, snapshot_id: 'snapshot-deadbeefdeadbeef' }, key));
});

test('RPO and RTO are measured to a usable reconciled state', () => {
  assert.deepEqual(
    measureRpoRto({ sourceAt: 1000, restoredAt: 2000, usableAt: 4000 }),
    { rpo_seconds: 1, rto_seconds: 2 },
  );
  assert.throws(() => measureRpoRto({ sourceAt: 0, restoredAt: 3_601_000, usableAt: 3_602_000 }));
  assert.throws(() => measureRpoRto({ sourceAt: 0, restoredAt: 1000, usableAt: 14_402_000 }));
});
