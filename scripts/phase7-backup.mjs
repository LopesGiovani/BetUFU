#!/usr/bin/env node
import { spawn } from 'node:child_process';
import {
  createHash,
  createHmac,
  createCipheriv,
  createDecipheriv,
  randomBytes,
  timingSafeEqual,
} from 'node:crypto';
import {
  chmod,
  lstat,
  mkdir,
  mkdtemp,
  open as openFile,
  readFile,
  readdir,
  rename,
  rm,
  unlink,
  writeFile,
} from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { validatePhase7Approvals } from './validate-phase7-approvals.mjs';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const OWNER = 'br.com.betufu.phase7-backup';
const SUBSTRATES = ['betufu', 'keycloak', 'private-objects'];
const RUN = /^betufu-phase7-backup-[a-f0-9]{16}$/u;
const SNAPSHOT = /^snapshot-[a-f0-9]{16}$/u;
const digest = (bytes) => createHash('sha256').update(bytes).digest('hex');
function requireValue(value, rule) {
  if (!value) throw new Error(rule);
}

export function validatePolicy(policy) {
  const expected = {
    schema_version: 1,
    scope: 'isolated-local-proof',
    production_status: 'pending',
    rpo_seconds: 3600,
    retention_days: 30,
    encryption: 'AES-256-GCM',
    destination: 'runner-created-private-directory',
    key_custody: 'separate-private-key-file-outside-artifact-directory',
    consistency: 'quiesce-disposable-writers-before-all-snapshots',
    access: 'current-os-account-only',
    expiry: 'dry-run-only-authenticated-owned-artifacts',
    provider: 'pending',
  };
  for (const [name, value] of Object.entries(expected))
    requireValue(policy?.[name] === value, `Invalid backup policy: ${name}`);
  requireValue(
    JSON.stringify(policy.substrates) === JSON.stringify(SUBSTRATES),
    'Three substrates required',
  );
  requireValue(
    typeof policy.human_reference === 'string' &&
      policy.human_reference.length > 0,
    'Missing owner decision',
  );
  requireValue(
    Number.isInteger(policy.schedule?.interval_seconds) &&
      policy.schedule.interval_seconds > 0 &&
      policy.schedule.interval_seconds < policy.rpo_seconds,
    'Schedule must leave RPO margin',
  );
  requireValue(
    Number.isInteger(policy.schedule?.isolated_first_delay_ms) &&
      policy.schedule.isolated_first_delay_ms >= 1 &&
      policy.schedule.isolated_first_delay_ms <= 60000,
    'Invalid first timer',
  );
  return policy;
}

export function requireOwned(inspect, run, service) {
  requireValue(RUN.test(run), 'Invalid isolated run');
  const labels = inspect.Config?.Labels;
  requireValue(
    labels?.[OWNER] === run &&
      labels?.['com.docker.compose.project'] === run &&
      labels?.['com.docker.compose.service'] === service,
    'Container ownership mismatch',
  );
  for (const mount of inspect.Mounts ?? []) {
    if (mount.Type === 'volume')
      requireValue(
        mount.Name === `${run}-${service}`,
        'Shared persistent volume',
      );
    if (mount.Type === 'bind')
      requireValue(mount.RW === false, 'Writable host mount');
  }
}

function requireKey(key) {
  requireValue(
    Buffer.isBuffer(key) && key.length === 32,
    'Missing AES-256 key',
  );
}
export function seal(plaintext, key, aad) {
  requireKey(key);
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', key, iv);
  cipher.setAAD(aad);
  return Buffer.concat([
    Buffer.from('B7G1'),
    iv,
    cipher.update(plaintext),
    cipher.final(),
    cipher.getAuthTag(),
  ]);
}
export function open(bytes, key, aad) {
  requireKey(key);
  requireValue(
    Buffer.isBuffer(bytes) &&
      bytes.length >= 32 &&
      bytes.subarray(0, 4).toString() === 'B7G1',
    'Invalid encrypted artifact',
  );
  const decipher = createDecipheriv('aes-256-gcm', key, bytes.subarray(4, 16));
  decipher.setAAD(aad);
  decipher.setAuthTag(bytes.subarray(-16));
  return Buffer.concat([
    decipher.update(bytes.subarray(16, -16)),
    decipher.final(),
  ]);
}
export function signManifest(manifest, key) {
  requireKey(key);
  const { authentication: ignored, ...payload } = manifest;
  return {
    ...payload,
    authentication: createHmac('sha256', key)
      .update(JSON.stringify(payload))
      .digest('hex'),
  };
}
export function authenticateManifest(manifest, key) {
  const expected = signManifest(manifest, key).authentication;
  requireValue(
    typeof manifest.authentication === 'string' &&
      /^[a-f0-9]{64}$/u.test(manifest.authentication) &&
      timingSafeEqual(
        Buffer.from(expected, 'hex'),
        Buffer.from(manifest.authentication, 'hex'),
      ),
    'Manifest authentication failed',
  );
  return manifest;
}
export async function readPrivateKey(path) {
  const stat = await lstat(path);
  requireValue(stat.isFile() && !stat.isSymbolicLink(), 'Invalid key custody');
  if (process.platform !== 'win32')
    requireValue((stat.mode & 0o077) === 0, 'Key permissions too broad');
  const key = await readFile(path);
  requireKey(key);
  return key;
}
export function validateSubstrates(components) {
  requireValue(
    Object.keys(components).sort().join() === [...SUBSTRATES].sort().join(),
    'Missing or unexpected substrate',
  );
  for (const name of SUBSTRATES)
    requireValue(
      Buffer.isBuffer(components[name]) && components[name].length > 0,
      `Empty substrate: ${name}`,
    );
}
export function assessRpo({
  startedAt,
  lastSnapshotAt,
  now,
  maxSeconds = 3600,
}) {
  const reference = lastSnapshotAt ?? startedAt;
  requireValue(
    [startedAt, reference, now, maxSeconds].every(Number.isFinite) &&
      startedAt <= reference &&
      reference <= now &&
      maxSeconds > 0,
    'Invalid RPO clock',
  );
  const gap = (now - reference) / 1000;
  requireValue(gap <= maxSeconds, 'ALERT: last usable snapshot exceeds RPO');
  return {
    gap_seconds: gap,
    maximum_seconds: maxSeconds,
    usable_snapshot: lastSnapshotAt !== null,
  };
}
export async function scheduleOnce(milliseconds, operation) {
  requireValue(
    Number.isInteger(milliseconds) && milliseconds > 0,
    'Invalid scheduler delay',
  );
  await delay(milliseconds);
  return operation();
}
export async function withLock(directory, operation) {
  const path = join(directory, 'backup.lock');
  let handle;
  try {
    handle = await openFile(path, 'wx', 0o600);
  } catch (error) {
    if (error.code === 'EEXIST') throw new Error('Backup overlap blocked');
    throw error;
  }
  try {
    return await operation();
  } finally {
    await handle.close();
    await unlink(path);
  }
}

export async function runBackupSchedule({
  operation,
  intervalMs,
  firstDelayMs,
  once = true,
  signal,
  clock = Date.now,
  startedAt = clock(),
  maxSeconds = 3600,
}) {
  requireValue(
    Number.isInteger(intervalMs) &&
      intervalMs > 0 &&
      intervalMs < maxSeconds * 1000,
    'Invalid recurring schedule',
  );
  requireValue(
    Number.isInteger(firstDelayMs) && firstDelayMs > 0,
    'Invalid first trigger',
  );
  let lastSnapshotAt = null;
  let maximumGap = 0;
  let observedRuns = 0;
  let nextAt = clock() + firstDelayMs;
  let manifest;
  const assess = () => {
    const assessment = assessRpo({
      startedAt,
      lastSnapshotAt,
      now: clock(),
      maxSeconds,
    });
    maximumGap = Math.max(maximumGap, assessment.gap_seconds);
    return assessment;
  };
  while (!signal?.aborted) {
    assess();
    try {
      await delay(Math.max(1, nextAt - clock()), undefined, { signal });
    } catch (error) {
      if (error.name === 'AbortError' && observedRuns > 0) break;
      throw error;
    }
    assess();
    manifest = await operation();
    // A new backup must not conceal the gap since the previous usable snapshot.
    assess();
    requireValue(
      Number.isFinite(manifest?.snapshot_at),
      'Missing usable snapshot time',
    );
    lastSnapshotAt = manifest.snapshot_at;
    assess();
    observedRuns += 1;
    if (once) break;
    nextAt += intervalMs;
  }
  requireValue(observedRuns > 0, 'No scheduled backup observed');
  return {
    manifest,
    observedRuns,
    rpo: { ...assess(), maximum_observed_gap_seconds: maximumGap },
  };
}

async function regular(path) {
  const stat = await lstat(path);
  requireValue(
    stat.isFile() && !stat.isSymbolicLink(),
    'Artifact is not a regular file',
  );
}
export async function retentionCandidates(
  directory,
  run,
  key,
  now = Date.now(),
) {
  requireValue(
    RUN.test(run) && Number.isFinite(now),
    'Invalid retention scope',
  );
  const result = [];
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    requireValue(
      entry.isDirectory() &&
        !entry.isSymbolicLink() &&
        SNAPSHOT.test(entry.name),
      'Unexpected retention path',
    );
    const path = join(directory, entry.name);
    await regular(join(path, 'manifest.json'));
    const manifest = authenticateManifest(
      JSON.parse(await readFile(join(path, 'manifest.json'), 'utf8')),
      key,
    );
    requireValue(
      manifest.run_id === run &&
        manifest.snapshot_id === entry.name &&
        Number.isFinite(manifest.snapshot_at) &&
        manifest.snapshot_at <= now,
      'Retention ownership mismatch',
    );
    requireValue(
      Array.isArray(manifest.components) &&
        manifest.components.length === 3 &&
        manifest.components
          .map((c) => c.name)
          .sort()
          .join() === [...SUBSTRATES].sort().join(),
      'Retention substrates mismatch',
    );
    const names = ['manifest.json', ...SUBSTRATES.map((s) => `${s}.aesgcm`)];
    requireValue(
      (await readdir(path)).sort().join() === names.sort().join(),
      'Unowned artifact in expiry scope',
    );
    for (const c of manifest.components) {
      requireValue(c.file === `${c.name}.aesgcm`, 'Unsafe artifact path');
      await regular(join(path, c.file));
    }
    if (now - manifest.snapshot_at > 30 * 86400000) result.push(entry.name);
  }
  return result.sort();
}

async function command(
  executable,
  args,
  { input, env = process.env, cwd = ROOT } = {},
) {
  return new Promise((done, reject) => {
    const child = spawn(executable, args, {
      env,
      cwd,
      shell: false,
      windowsHide: true,
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    const output = [];
    let total = 0;
    let overflow = false;
    child.stdout.on('data', (chunk) => {
      total += chunk.length;
      if (total > 512 * 1024 * 1024) {
        overflow = true;
        child.kill();
      } else output.push(chunk);
    });
    // Docker/Java diagnostics may contain credentials. Never forward raw output.
    child.stderr.resume();
    child.stdin.on('error', () => {});
    child.on('error', () =>
      reject(new Error(`${basename(executable)} could not start`)),
    );
    child.on('close', (code) =>
      code === 0 && !overflow
        ? done(Buffer.concat(output))
        : reject(
            new Error(
              `${basename(executable)} operation failed (exit ${code ?? 'unknown'}${overflow ? ', size limit' : ''})`,
            ),
          ),
    );
    child.stdin.end(input);
  });
}
async function privateDirectory(path) {
  await chmod(path, 0o700);
  if (process.platform === 'win32') {
    const sid = (
      await command('powershell.exe', [
        '-NoProfile',
        '-Command',
        '[System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value',
      ])
    )
      .toString()
      .trim();
    requireValue(
      /^S-1-(?:\d+-)+\d+$/u.test(sid),
      'Cannot determine private account',
    );
    await command('icacls.exe', [
      path,
      '/inheritance:r',
      '/grant:r',
      `*${sid}:(OI)(CI)F`,
    ]);
  }
}

async function buildStack(run, directory) {
  const env = { ...process.env };
  for (const name of [
    'POSTGRES_PASSWORD',
    'BETUFU_DB_PASSWORD',
    'BETUFU_FLYWAY_PASSWORD',
    'KEYCLOAK_DB_PASSWORD',
    'BETUFU_REDIS_PASSWORD',
    'KC_BOOTSTRAP_ADMIN_PASSWORD',
    'BETUFU_OIDC_CLIENT_SECRET',
    'BETUFU_TECHNICAL_PASSWORD',
    'BETUFU_S3_ACCESS_KEY_ID',
    'BETUFU_S3_SECRET_ACCESS_KEY',
  ])
    env[name] = randomBytes(24).toString('hex');
  env.BETUFU_MATRICULA_HMAC_KEY_V1 = randomBytes(32).toString('base64');
  env.BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION = 'v1';
  const source = JSON.parse(
    (
      await command(
        'docker',
        [
          'compose',
          '--env-file',
          'infra/.env.example',
          '-f',
          'infra/compose.yaml',
          '--profile',
          'full',
          'config',
          '--format',
          'json',
        ],
        { env },
      )
    ).toString(),
  );
  const config = {
    name: run,
    services: {},
    volumes: {},
    networks: { default: { name: `${run}-network`, labels: { [OWNER]: run } } },
  };
  for (const name of ['postgres', 'keycloak', 'seaweedfs']) {
    const s = structuredClone(source.services[name]);
    delete s.profiles;
    delete s.container_name;
    delete s.build;
    s.labels = { [OWNER]: run };
    s.networks = { default: null };
    s.ports =
      name === 'keycloak'
        ? []
        : [
            {
              target: name === 'postgres' ? 5432 : 8333,
              host_ip: '127.0.0.1',
              protocol: 'tcp',
            },
          ];
    s.volumes = (s.volumes ?? []).filter(
      (m) =>
        m.type !== 'bind' ||
        name === 'postgres' ||
        m.target.includes('/data/import/'),
    );
    for (const m of s.volumes) {
      if (m.type === 'volume') {
        m.source = name;
        config.volumes[name] = {
          name: `${run}-${name}`,
          labels: { [OWNER]: run },
        };
      } else {
        requireValue(m.read_only === true, 'Unsafe infrastructure bind');
        if (name === 'keycloak') m.source = join(directory, 'realm.json');
      }
    }
    config.services[name] = s;
  }
  const realm = JSON.parse(
    await readFile(join(ROOT, 'infra/keycloak/realm-betufu.json'), 'utf8'),
  );
  realm.users = [
    ...(realm.users ?? []),
    {
      username: 'backup-proof-user',
      enabled: true,
      emailVerified: true,
      credentials: [
        {
          type: 'password',
          value: randomBytes(24).toString('hex'),
          temporary: false,
        },
      ],
    },
  ];
  await writeFile(join(directory, 'realm.json'), JSON.stringify(realm), {
    mode: 0o600,
    flag: 'wx',
  });
  await writeFile(join(directory, 'compose.json'), JSON.stringify(config), {
    mode: 0o600,
    flag: 'wx',
  });
  return config;
}

class IsolatedStack {
  constructor(run, directory, config) {
    this.run = run;
    this.directory = directory;
    this.config = config;
    this.ids = {};
    this.compose = [
      'compose',
      '-f',
      join(directory, 'compose.json'),
      '--project-name',
      run,
    ];
  }
  async inspect(service) {
    const id = (
      await command('docker', [...this.compose, 'ps', '--all', '-q', service])
    )
      .toString()
      .trim();
    requireValue(/^[a-f0-9]{12,64}$/u.test(id), 'Expected one owned container');
    const [info] = JSON.parse(
      (await command('docker', ['inspect', id])).toString(),
    );
    requireOwned(info, this.run, service);
    this.ids[service] = id;
    return info;
  }
  async start() {
    await command('docker', [
      ...this.compose,
      'up',
      '-d',
      '--wait',
      '--wait-timeout',
      '300',
    ]);
    for (const name of Object.keys(this.config.services))
      await this.inspect(name);
  }
  async port(service, target) {
    const info = await this.inspect(service);
    const ports = info.NetworkSettings.Ports[`${target}/tcp`];
    requireValue(
      ports?.length === 1 &&
        ports[0].HostIp === '127.0.0.1' &&
        /^\d+$/u.test(ports[0].HostPort),
      'Nonprivate port',
    );
    return ports[0].HostPort;
  }
  async exec(service, args, input) {
    await this.inspect(service);
    return command('docker', ['exec', '-i', this.ids[service], ...args], {
      input,
    });
  }
  async sql(database, sql) {
    return this.exec(
      'postgres',
      [
        'psql',
        '-X',
        '-v',
        'ON_ERROR_STOP=1',
        '-At',
        '-U',
        this.config.services.postgres.environment.POSTGRES_USER,
        '-d',
        database,
      ],
      sql,
    );
  }
  async quiesce(operation) {
    const writers = ['keycloak', 'seaweedfs'];
    try {
      for (const name of writers) {
        await this.inspect(name);
        await command('docker', ['stop', this.ids[name]]);
      }
      for (const name of writers)
        requireValue(
          !(await this.inspect(name)).State.Running,
          'Writer did not stop',
        );
      return await operation();
    } finally {
      for (const name of writers) {
        await this.inspect(name);
        await command('docker', ['start', this.ids[name]]);
      }
    }
  }
  async cleanup() {
    // Validate the entire removal scope before the first destructive operation.
    const ids = [];
    for (const name of Object.keys(this.config.services)) {
      const result = (
        await command('docker', [...this.compose, 'ps', '--all', '-q', name])
      )
        .toString()
        .trim();
      if (result) {
        await this.inspect(name);
        ids.push(this.ids[name]);
      }
    }
    const volumes = [];
    for (const name of Object.keys(this.config.volumes)) {
      const full = `${this.run}-${name}`;
      const found = (
        await command('docker', [
          'volume',
          'ls',
          '--format',
          '{{.Name}}',
          '--filter',
          `label=${OWNER}=${this.run}`,
        ])
      )
        .toString()
        .trim()
        .split(/\r?\n/u);
      if (!found.includes(full)) continue;
      const [info] = JSON.parse(
        (await command('docker', ['volume', 'inspect', full])).toString(),
      );
      requireValue(
        info.Name === full &&
          info.Labels?.[OWNER] === this.run &&
          info.Labels?.['com.docker.compose.project'] === this.run &&
          info.Labels?.['com.docker.compose.volume'] === name,
        'Volume ownership mismatch',
      );
      volumes.push(full);
    }
    const networks = (
      await command('docker', [
        'network',
        'ls',
        '--format',
        '{{.ID}}',
        '--filter',
        `label=${OWNER}=${this.run}`,
      ])
    )
      .toString()
      .trim()
      .split(/\r?\n/u)
      .filter(Boolean);
    for (const id of networks) {
      const [info] = JSON.parse(
        (await command('docker', ['network', 'inspect', id])).toString(),
      );
      requireValue(
        info.Name === `${this.run}-network` &&
          info.Labels?.[OWNER] === this.run,
        'Network ownership mismatch',
      );
    }
    if (ids.length) await command('docker', ['rm', '-f', ...ids]);
    for (const name of volumes) await command('docker', ['volume', 'rm', name]);
    for (const id of networks) await command('docker', ['network', 'rm', id]);
  }
}

async function migrateAndSeed(stack) {
  const target = join(ROOT, 'backend/target');
  const jars = (await readdir(target)).filter((n) =>
    /^betufu-backend-.*\.jar$/u.test(n),
  );
  requireValue(jars.length === 1, 'Build backend before isolated backup');
  const javaDir = join(stack.directory, 'java');
  await mkdir(javaDir, { mode: 0o700 });
  await command('jar', ['xf', join(target, jars[0]), 'BOOT-INF/lib'], {
    cwd: javaDir,
  });
  const pg = stack.config.services.postgres.environment;
  const source = `import org.flywaydb.core.Flyway;
    class BackupSchema { public static void main(String[] args) {
      var f=Flyway.configure().dataSource(System.getenv("BACKUP_DB_URL"),System.getenv("BACKUP_DB_USER"),System.getenv("BACKUP_DB_PASSWORD"))
        .initSql("SET betufu.runtime_role = '"+System.getenv("BACKUP_RUNTIME_ROLE")+"'")
        .locations("filesystem:"+System.getenv("BACKUP_MIGRATIONS")).load();
      f.migrate(); f.validate();
    } }`;
  await writeFile(join(javaDir, 'BackupSchema.java'), source, {
    mode: 0o600,
    flag: 'wx',
  });
  await command(
    'java',
    [
      '-cp',
      join(javaDir, 'BOOT-INF/lib/*'),
      join(javaDir, 'BackupSchema.java'),
    ],
    {
      cwd: javaDir,
      env: {
        ...process.env,
        BACKUP_DB_URL: `jdbc:postgresql://127.0.0.1:${await stack.port('postgres', 5432)}/${pg.BETUFU_DB_NAME}`,
        BACKUP_DB_USER: pg.BETUFU_FLYWAY_USER,
        BACKUP_DB_PASSWORD: pg.BETUFU_FLYWAY_PASSWORD,
        BACKUP_RUNTIME_ROLE: pg.BETUFU_DB_USER,
        BACKUP_MIGRATIONS: join(
          ROOT,
          'backend/src/main/resources/db/migration',
        ),
      },
    },
  );
  await stack.sql(
    pg.BETUFU_DB_NAME,
    `INSERT INTO local_identity(identity_id,issuer,subject)
    VALUES ('00000000-0000-0000-0000-000000000701','https://isolated.invalid','backup-proof');
    INSERT INTO chat_message(message_id,channel_id,author_identity_id,body)
    SELECT gen_random_uuid(),channel_id,'00000000-0000-0000-0000-000000000701','backup-original-fact'
    FROM chat_channel WHERE kind='GLOBAL';`,
  );
  const count = Number(
    (
      await stack.sql(
        pg.KEYCLOAK_DB_NAME,
        "SELECT count(*) FROM user_entity WHERE username='backup-proof-user';",
      )
    )
      .toString()
      .trim(),
  );
  requireValue(count === 1, 'Keycloak imported user missing');
}

function s3Headers(method, url, body, access, secret) {
  const timestamp = new Date().toISOString().replace(/[:-]|\.\d{3}/gu, '');
  const day = timestamp.slice(0, 8);
  const sha = digest(body);
  const scope = `${day}/us-east-1/s3/aws4_request`;
  const canonical = `${method}\n${url.pathname}\n\nhost:${url.host}\nx-amz-content-sha256:${sha}\nx-amz-date:${timestamp}\n\nhost;x-amz-content-sha256;x-amz-date\n${sha}`;
  const hmac = (k, v) => createHmac('sha256', k).update(v).digest();
  const signing = hmac(
    hmac(hmac(hmac(`AWS4${secret}`, day), 'us-east-1'), 's3'),
    'aws4_request',
  );
  const signature = createHmac('sha256', signing)
    .update(`AWS4-HMAC-SHA256\n${timestamp}\n${scope}\n${digest(canonical)}`)
    .digest('hex');
  return {
    'x-amz-date': timestamp,
    'x-amz-content-sha256': sha,
    Authorization: `AWS4-HMAC-SHA256 Credential=${access}/${scope}, SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature=${signature}`,
  };
}
async function seedObject(stack) {
  const env = stack.config.services.seaweedfs.environment;
  const url = new URL(
    `http://127.0.0.1:${await stack.port('seaweedfs', 8333)}/${env.S3_BUCKET}/backup-proof.txt`,
  );
  const body = Buffer.from('private-object-backup-proof');
  const request = async (method) => {
    const data = method === 'PUT' ? body : Buffer.alloc(0);
    const response = await fetch(url, {
      method,
      headers: s3Headers(
        method,
        url,
        data,
        env.AWS_ACCESS_KEY_ID,
        env.AWS_SECRET_ACCESS_KEY,
      ),
      body: method === 'PUT' ? data : undefined,
      signal: AbortSignal.timeout(30000),
    });
    requireValue(response.ok, 'Private object request failed');
    return Buffer.from(await response.arrayBuffer());
  };
  await request('PUT');
  requireValue((await request('GET')).equals(body), 'Private object differs');
  const anonymous = await fetch(url, { signal: AbortSignal.timeout(30000) });
  requireValue(
    [401, 403].includes(anonymous.status),
    'Object unexpectedly public',
  );
  return { sha256: digest(body), bytes: body.length, key: 'backup-proof.txt' };
}

async function collectSnapshot(stack, object) {
  const pg = stack.config.services.postgres.environment;
  const snapshotAt = Date.now();
  const betufu = pg.BETUFU_DB_NAME;
  const kc = pg.KEYCLOAK_DB_NAME;
  const flyway = JSON.parse(
    (
      await stack.sql(
        betufu,
        "SELECT json_agg(json_build_object('version',version,'checksum',checksum,'success',success) ORDER BY installed_rank) FROM flyway_schema_history;",
      )
    ).toString(),
  );
  requireValue(
    flyway.length > 0 &&
      flyway.every((row) => row.success) &&
      flyway.some((row) => row.version === '39'),
    'Flyway V39 backup prerequisite missing',
  );
  const lastFact = JSON.parse(
    (
      await stack.sql(
        betufu,
        "SELECT json_build_object('chat_cursor',max(change_cursor),'chat_count',count(*)) FROM chat_message;",
      )
    ).toString(),
  );
  requireValue(lastFact.chat_count > 0, 'Backup history empty');
  const dump = (db) =>
    stack.exec('postgres', [
      'pg_dump',
      '-U',
      pg.POSTGRES_USER,
      '-d',
      db,
      '--format=custom',
    ]);
  const components = {
    betufu: await dump(betufu),
    keycloak: await dump(kc),
    'private-objects': await command('docker', [
      'cp',
      `${stack.ids.seaweedfs}:/data/.`,
      '-',
    ]),
  };
  validateSubstrates(components);
  const schemas = {};
  for (const db of [betufu, kc])
    schemas[db] = digest(
      await stack.exec('postgres', [
        'pg_dump',
        '-U',
        pg.POSTGRES_USER,
        '-d',
        db,
        '--schema-only',
      ]),
    );
  return {
    components,
    metadata: {
      snapshot_at: snapshotAt,
      flyway,
      schema_sha256: schemas,
      last_fact: lastFact,
      private_object: object,
      consistency: {
        method: 'quiesced-disposable-writers',
        writers: ['keycloak', 'seaweedfs'],
        application_writers: 'not-deployed-in-isolated-proof',
        ended_at: Date.now(),
      },
      versions: {
        postgres: (await stack.sql(betufu, 'SHOW server_version;'))
          .toString()
          .trim(),
        images: Object.fromEntries(
          Object.entries(stack.config.services).map(([n, s]) => [n, s.image]),
        ),
      },
    },
  };
}
async function verifySavedSnapshot(stack, path, key, manifest) {
  authenticateManifest(manifest, key);
  for (const component of manifest.components) {
    await regular(join(path, component.file));
    const cipher = await readFile(join(path, component.file));
    requireValue(
      digest(cipher) === component.cipher_sha256,
      'Encrypted component corrupted',
    );
    const plain = open(
      cipher,
      key,
      Buffer.from(
        `${manifest.run_id}/${manifest.snapshot_id}/${component.name}`,
      ),
    );
    requireValue(
      plain.length === component.plain_bytes &&
        digest(plain) === component.plain_sha256,
      'Plain component differs',
    );
    if (component.name === 'private-objects') {
      const listing = (
        await stack.exec('postgres', ['tar', '-tf', '-'], plain)
      ).toString();
      requireValue(
        listing.includes('.dat') && listing.includes('.idx'),
        'Private object store archive incomplete',
      );
    } else {
      const listing = (
        await stack.exec('postgres', ['pg_restore', '--list'], plain)
      ).toString();
      const expected =
        component.name === 'betufu'
          ? ['chat_message', 'flyway_schema_history', 'local_identity']
          : ['user_entity', 'realm', 'credential'];
      for (const table of expected)
        requireValue(
          listing.includes(`TABLE DATA public ${table} `),
          `Dump missing table: ${table}`,
        );
    }
  }
}
async function saveSnapshot(stack, artifactDirectory, key, object) {
  return stack.quiesce(async () => {
    const { components, metadata } = await collectSnapshot(stack, object);
    const id = `snapshot-${randomBytes(8).toString('hex')}`;
    const path = join(artifactDirectory, id);
    await mkdir(path, { mode: 0o700 });
    const entries = [];
    for (const name of SUBSTRATES) {
      const bytes = seal(
        components[name],
        key,
        Buffer.from(`${stack.run}/${id}/${name}`),
      );
      const file = `${name}.aesgcm`;
      await writeFile(join(path, file), bytes, { mode: 0o600, flag: 'wx' });
      entries.push({
        name,
        file,
        cipher_sha256: digest(bytes),
        cipher_bytes: bytes.length,
        plain_sha256: digest(components[name]),
        plain_bytes: components[name].length,
      });
    }
    const manifest = signManifest(
      {
        schema_version: 1,
        run_id: stack.run,
        snapshot_id: id,
        ...metadata,
        access: 'current-os-account-only',
        encryption: 'AES-256-GCM',
        components: entries,
        completed_at: Date.now(),
      },
      key,
    );
    await verifySavedSnapshot(stack, path, key, manifest);
    await writeFile(
      join(path, 'manifest.pending'),
      JSON.stringify(manifest, null, 2) + '\n',
      { mode: 0o600, flag: 'wx' },
    );
    await rename(join(path, 'manifest.pending'), join(path, 'manifest.json'));
    return manifest;
  });
}

export async function runIsolatedBackup(policy, { once = true, signal } = {}) {
  validatePolicy(policy);
  const run = `betufu-phase7-backup-${randomBytes(8).toString('hex')}`;
  const directory = await mkdtemp(join(tmpdir(), `${run}-`));
  await privateDirectory(directory);
  await writeFile(
    join(directory, 'owner.json'),
    JSON.stringify({ run_id: run }),
    { mode: 0o600, flag: 'wx' },
  );
  const work = join(directory, 'work');
  const snapshots = join(directory, 'snapshots');
  const keys = join(directory, 'keys');
  for (const path of [work, snapshots, keys])
    await mkdir(path, { mode: 0o700 });
  const keyPath = join(keys, 'backup.key');
  await writeFile(keyPath, randomBytes(32), { mode: 0o600, flag: 'wx' });
  const key = await readPrivateKey(keyPath);
  const config = await buildStack(run, work);
  const stack = new IsolatedStack(run, work, config);
  const started = Date.now();
  let report;
  console.log(`[phase7-backup] Starting disposable run ${run}`);
  try {
    await stack.start();
    await migrateAndSeed(stack);
    const object = await seedObject(stack);
    const scheduledAt = Date.now();
    console.log(
      '[phase7-backup] Real substrates ready; waiting for automatic timer',
    );
    const { manifest, rpo, observedRuns } = await runBackupSchedule({
      firstDelayMs: policy.schedule.isolated_first_delay_ms,
      intervalMs: policy.schedule.interval_seconds * 1000,
      startedAt: started,
      maxSeconds: policy.rpo_seconds,
      once,
      signal,
      operation: () =>
        withLock(directory, () => saveSnapshot(stack, snapshots, key, object)),
    });
    const expired = await retentionCandidates(snapshots, run, key);
    report = {
      schema_version: 1,
      scope: policy.scope,
      run_id: run,
      status: 'passed',
      production_status: 'pending',
      trigger: {
        type: 'automatic-node-timer',
        scheduled_at: scheduledAt,
        observed_snapshot_at: manifest.snapshot_at,
        first_delay_ms: policy.schedule.isolated_first_delay_ms,
        interval_seconds: policy.schedule.interval_seconds,
        observed_runs: observedRuns,
      },
      snapshot_id: manifest.snapshot_id,
      components: manifest.components.map((c) => ({
        name: c.name,
        cipher_sha256: c.cipher_sha256,
        cipher_bytes: c.cipher_bytes,
      })),
      rpo,
      retention: {
        days: policy.retention_days,
        mode: 'dry-run-only',
        expired_owned_snapshots: expired,
        deleted: 0,
      },
      access: policy.access,
      restore_status: 'not-executed-plan-07-29',
      limitations: [
        'Isolated local scheduler; no long-running production RPO guarantee.',
        'Full restore and later suppression reconciliation require 07-29.',
      ],
    };
  } catch (error) {
    await writeFile(
      join(directory, 'alert.json'),
      JSON.stringify({
        run_id: run,
        status: 'failed',
        at: new Date().toISOString(),
        message: 'Backup failed; no usable snapshot certified',
      }),
      { mode: 0o600 },
    );
    throw error;
  } finally {
    await stack.cleanup();
  }
  requireValue(
    JSON.parse(await readFile(join(directory, 'owner.json'), 'utf8')).run_id ===
      run &&
      dirname(work) === directory &&
      basename(work) === 'work',
    'Work cleanup ownership mismatch',
  );
  await rm(work, { recursive: true });
  report.cleanup = 'only-owned-disposable-containers-volumes-network-and-work';
  await writeFile(
    join(directory, 'report.json'),
    JSON.stringify(report, null, 2) + '\n',
    { mode: 0o600, flag: 'wx' },
  );
  console.log(
    `[phase7-backup] Passed; private report: ${join(directory, 'report.json')}`,
  );
  console.log(
    `[phase7-backup] Snapshot gap ${report.rpo.gap_seconds}s; encrypted three substrates; restore still pending`,
  );
  return { directory, report };
}

async function main(args) {
  requireValue(
    args.length === 4 &&
      args[0] === '--policy' &&
      args[1] === 'infra/backup/phase7-backup-policy.json' &&
      args[2] === '--isolated' &&
      ['--schedule-once', '--schedule'].includes(args[3]),
    'Only approved isolated backup command supported',
  );
  await validatePhase7Approvals({ plan: '07-28', stage: 'implementation' });
  const abort = new AbortController();
  const stop = () => abort.abort();
  process.once('SIGINT', stop);
  process.once('SIGTERM', stop);
  try {
    await runIsolatedBackup(
      JSON.parse(await readFile(join(ROOT, args[1]), 'utf8')),
      { once: args[3] === '--schedule-once', signal: abort.signal },
    );
  } finally {
    process.removeListener('SIGINT', stop);
    process.removeListener('SIGTERM', stop);
  }
}
if (
  process.argv[1] &&
  resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  main(process.argv.slice(2)).catch((error) => {
    console.error(`[phase7-backup] ALERT: ${error.message}`);
    process.exitCode = 1;
  });
}
