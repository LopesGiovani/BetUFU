import assert from 'node:assert/strict';
import { test } from 'node:test';
import { randomBytes } from 'node:crypto';
import {
  mkdtemp,
  mkdir,
  readFile,
  writeFile,
  symlink,
  rm,
} from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import {
  validatePolicy,
  requireOwned,
  seal,
  open,
  assessRpo,
  validateSubstrates,
  scheduleOnce,
  withLock,
  retentionCandidates,
  authenticateManifest,
  signManifest,
  readPrivateKey,
  runBackupSchedule,
} from './phase7-backup.mjs';

const policy = JSON.parse(
  await readFile('infra/backup/phase7-backup-policy.json'),
);
const run = 'betufu-phase7-backup-0123456789abcdef';
const owner = 'br.com.betufu.phase7-backup';
const key = randomBytes(32);
const aad = Buffer.from('snapshot-1/betufu');
const fixture = Buffer.from('private history must survive exactly');
const validSet = () =>
  Object.fromEntries(policy.substrates.map((s) => [s, fixture]));

test('approved local policy stays distinct from public-pilot readiness', () => {
  assert.equal(validatePolicy(policy).production_status, 'pending');
});
for (const [field, value] of [
  ['scope', 'production'],
  ['rpo_seconds', 3601],
  ['retention_days', 29],
  ['encryption', 'none'],
  ['key_custody', 'alongside-backup'],
  ['consistency', 'live-uncoordinated'],
  ['access', 'public'],
  ['destination', '/owner/seeds'],
  ['expiry', 'delete-everything'],
])
  test(`policy rejects unsafe ${field}`, () => {
    assert.throws(() => validatePolicy({ ...policy, [field]: value }));
  });
test('schedule must leave room inside RPO', () => {
  assert.throws(() =>
    validatePolicy({
      ...policy,
      schedule: { ...policy.schedule, interval_seconds: 3600 },
    }),
  );
});
test('one missing substrate fails even with two good dumps', () => {
  const set = validSet();
  delete set.keycloak;
  assert.throws(() => validateSubstrates(set));
});
test('empty private object backup fails', () => {
  assert.throws(() =>
    validateSubstrates({ ...validSet(), 'private-objects': Buffer.alloc(0) }),
  );
});
test('unrecognized substrate fails', () => {
  assert.throws(() =>
    validateSubstrates({ ...validSet(), 'realm-export-only': fixture }),
  );
});
test('authenticated cipher roundtrips exact bytes and hides plaintext', () => {
  const encrypted = seal(fixture, key, aad);
  assert.ok(!encrypted.includes(fixture));
  assert.deepEqual(open(encrypted, key, aad), fixture);
});
test('ciphertext tampering is detected', () => {
  const bytes = seal(fixture, key, aad);
  bytes[bytes.length - 1] ^= 1;
  assert.throws(() => open(bytes, key, aad));
});
test('wrong key and changed metadata fail authentication', () => {
  const bytes = seal(fixture, key, aad);
  assert.throws(() => open(bytes, randomBytes(32), aad));
  assert.throws(() => open(bytes, key, Buffer.from('different')));
});
test('nonce differs per encryption', () => {
  assert.notDeepEqual(seal(fixture, key, aad), seal(fixture, key, aad));
});
test('missing or short encryption key fails closed', () => {
  assert.throws(() => seal(fixture, undefined, aad));
  assert.throws(() => seal(fixture, Buffer.alloc(16), aad));
});
test('RPO measures age of snapshot, not completion or scheduler label', () => {
  assert.equal(
    assessRpo({ startedAt: 0, lastSnapshotAt: 1000, now: 3601000 }).gap_seconds,
    3600,
  );
  assert.throws(() =>
    assessRpo({ startedAt: 0, lastSnapshotAt: 1000, now: 3601001 }),
  );
});
test('missing backup after one hour fails', () => {
  assert.throws(() =>
    assessRpo({ startedAt: 0, lastSnapshotAt: null, now: 3600001 }),
  );
});
test('future snapshot and invalid clocks fail', () => {
  assert.throws(() =>
    assessRpo({ startedAt: 0, lastSnapshotAt: 100, now: 99 }),
  );
  assert.throws(() =>
    assessRpo({ startedAt: NaN, lastSnapshotAt: null, now: 100 }),
  );
});
test('scheduler callback occurs after a real timer, never immediately', async () => {
  let ticked = false;
  const start = Date.now();
  const pending = scheduleOnce(25, async () => {
    ticked = true;
    return 'saved';
  });
  assert.equal(ticked, false);
  assert.equal(await pending, 'saved');
  assert.ok(Date.now() - start >= 20);
});
test('scheduled failure propagates instead of reporting success', async () => {
  await assert.rejects(
    scheduleOnce(1, async () => {
      throw new Error('failed substrate');
    }),
    /failed substrate/,
  );
});
test('recurring scheduler observes two ticks and preserves maximum gap', async () => {
  const abort = new AbortController();
  let calls = 0;
  const result = await runBackupSchedule({
    intervalMs: 10,
    firstDelayMs: 1,
    once: false,
    signal: abort.signal,
    operation: async () => {
      calls++;
      if (calls === 2) abort.abort();
      return { snapshot_at: Date.now() };
    },
  });
  assert.equal(result.observedRuns, 2);
  assert.ok(result.rpo.maximum_observed_gap_seconds >= result.rpo.gap_seconds);
});
test('a fresh backup cannot hide an overdue previous gap', async () => {
  let now = 0;
  await assert.rejects(
    runBackupSchedule({
      intervalMs: 1,
      firstDelayMs: 1,
      startedAt: 0,
      clock: () => now,
      operation: async () => {
        now = 3600001;
        return { snapshot_at: now };
      },
    }),
    /exceeds RPO/,
  );
});
test('failed recurring backup and missing snapshot never certify success', async () => {
  await assert.rejects(
    runBackupSchedule({
      intervalMs: 2,
      firstDelayMs: 1,
      operation: async () => {
        throw new Error('missing dump');
      },
    }),
    /missing dump/,
  );
  await assert.rejects(
    runBackupSchedule({
      intervalMs: 2,
      firstDelayMs: 1,
      operation: async () => ({}),
    }),
    /Missing usable snapshot/,
  );
});
test('owned container requires exact run and isolated mounts', () => {
  const inspect = {
    Config: {
      Labels: {
        [owner]: run,
        'com.docker.compose.project': run,
        'com.docker.compose.service': 'postgres',
      },
    },
    Mounts: [{ Type: 'volume', Name: `${run}-postgres` }],
  };
  requireOwned(inspect, run, 'postgres');
  assert.throws(() => requireOwned(inspect, 'owner-local', 'postgres'));
  assert.throws(() =>
    requireOwned(
      {
        ...inspect,
        Mounts: [{ Type: 'volume', Name: 'betufu-local-postgres-data' }],
      },
      run,
      'postgres',
    ),
  );
  assert.throws(() => requireOwned(inspect, run, 'keycloak'));
});
test('manifest signature protects expiry and component metadata', () => {
  const m = signManifest({ run_id: run, snapshot_at: 1, components: [] }, key);
  authenticateManifest(m, key);
  assert.throws(() => authenticateManifest({ ...m, snapshot_at: 0 }, key));
  assert.throws(() => authenticateManifest(m, randomBytes(32)));
});

async function workspace(t) {
  const root = await mkdtemp(join(tmpdir(), 'betufu-backup-unit-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  return root;
}
test('overlap lock rejects second execution and releases after failure', async (t) => {
  const root = await workspace(t);
  await assert.rejects(
    withLock(root, async () => {
      await assert.rejects(
        withLock(root, async () => {}),
        /overlap/,
      );
      throw new Error('copy failure');
    }),
    /copy failure/,
  );
  assert.equal(await withLock(root, async () => 42), 42);
});
test('key reader rejects absent, symlinked or malformed custody', async (t) => {
  const root = await workspace(t);
  await assert.rejects(readPrivateKey(join(root, 'missing')));
  await writeFile(join(root, 'bad'), 'not-a-key', { mode: 0o600 });
  await assert.rejects(readPrivateKey(join(root, 'bad')));
  await writeFile(join(root, 'key'), key, { mode: 0o600 });
  assert.deepEqual(await readPrivateKey(join(root, 'key')), key);
  if (process.platform !== 'win32') {
    await symlink(join(root, 'key'), join(root, 'link'));
    await assert.rejects(readPrivateKey(join(root, 'link')));
  }
});
test('retention only lists authentic exact owned expired snapshots', async (t) => {
  const root = await workspace(t);
  const now = 31 * 86400000;
  await mkdir(join(root, 'snapshots'));
  const old = signManifest(
    {
      run_id: run,
      snapshot_id: 'snapshot-0000000000000001',
      snapshot_at: 0,
      components: policy.substrates.map((name) => ({
        name,
        file: `${name}.aesgcm`,
      })),
    },
    key,
  );
  const recent = signManifest(
    { ...old, snapshot_id: 'snapshot-0000000000000002', snapshot_at: now },
    key,
  );
  for (const m of [old, recent]) {
    const dir = join(root, 'snapshots', m.snapshot_id);
    await mkdir(dir);
    await writeFile(join(dir, 'manifest.json'), JSON.stringify(m));
    for (const c of m.components) await writeFile(join(dir, c.file), 'cipher');
  }
  assert.deepEqual(
    await retentionCandidates(join(root, 'snapshots'), run, key, now),
    [old.snapshot_id],
  );
  assert.ok(
    await readFile(join(root, 'snapshots', old.snapshot_id, 'manifest.json')),
  );
});
test('retention refuses another run', async (t) => {
  const root = await workspace(t);
  const id = 'snapshot-0000000000000001';
  const dir = join(root, id);
  await mkdir(dir);
  await writeFile(
    join(dir, 'manifest.json'),
    JSON.stringify(
      signManifest(
        { run_id: 'owner', snapshot_id: id, snapshot_at: 0, components: [] },
        key,
      ),
    ),
  );
  await assert.rejects(retentionCandidates(root, run, key, 31 * 86400000));
});
