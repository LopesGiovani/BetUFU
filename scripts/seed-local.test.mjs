import test from 'node:test';
import assert from 'node:assert/strict';
import { seedLocal, sanitizeSqlError } from './seed-local.mjs';

const ENV = {
  KC_BOOTSTRAP_ADMIN_USERNAME: 'local-admin',
  KC_BOOTSTRAP_ADMIN_PASSWORD: 'local-password',
  POSTGRES_USER: 'postgres',
  BETUFU_DB_NAME: 'betufu_local',
  BETUFU_MATRICULA_HMAC_KEY_V1: Buffer.alloc(32, 7).toString('base64'),
};

const FIXTURE = {
  partidas: [{
    origem_id: '393614', origem_url: 'https://laufu.jliga.club/jogos/393614-direito-x-fisioterapia',
    campeonato_id: '9774', data: '2026-02-26', horario: '22:00', data_hora_iso: '2026-02-26T22:00:00-03:00',
    time_mandante_nome: 'DIREITO', time_visitante_nome: 'FISIOTERAPIA',
  }],
};

function response(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async json() {
      if (status === 201 || body === null || body === undefined) {
        throw new SyntaxError('Unexpected end of JSON input');
      }
      return body;
    },
  };
}

function fakeFetchFactory() {
  const users = new Map();
  const calls = [];
  return {
    calls,
    fetchImpl: async (url, init = {}) => {
      calls.push({ url, method: init.method ?? 'GET' });
      if (url.endsWith('/realms/master/protocol/openid-connect/token')) return response({ access_token: 'opaque-test-token' });
      const match = /\/users\?username=([^&]+)/u.exec(url);
      if (match) {
        const username = decodeURIComponent(match[1]);
        const existing = users.get(username);
        return response(existing ? [existing] : []);
      }
      if (url.endsWith('/users') && init.method === 'POST') {
        const body = JSON.parse(init.body);
        const user = { id: `subject-${users.size + 1}`, username: body.username };
        users.set(body.username, user);
        return response(null, 201);
      }
      throw new Error(`URL inesperada no mock: ${url}`);
    },
  };
}

test('falha fechado quando o container PostgreSQL está offline', async () => {
  const keycloak = fakeFetchFactory();
  const commands = [];
  await assert.rejects(
    seedLocal({
      profile: 'daily', env: ENV, fixture: FIXTURE, fetchImpl: keycloak.fetchImpl,
      runCommand: async (command, args) => { commands.push({ command, args }); throw new Error('docker indisponível'); },
    }),
    /docker indisponível/u,
  );
  assert.equal(commands.length, 1);
  assert.equal(commands[0].command, 'docker');
  assert.equal(keycloak.calls.filter((call) => call.method === 'POST' && call.url.endsWith('/users')).length, 6);
});

test('duas execuções convergem para o mesmo SQL sem recriar usuários', async () => {
  const keycloak = fakeFetchFactory();
  const sqlRuns = [];
  const runCommand = async (command, args, input = '') => {
    if (args.includes('ps') && args.includes('postgres')) return 'abcdef123456';
    if (args.includes('psql')) sqlRuns.push(input);
    return '';
  };
  const first = await seedLocal({ profile: 'daily', env: ENV, fixture: FIXTURE, fetchImpl: keycloak.fetchImpl, runCommand });
  const second = await seedLocal({ profile: 'daily', env: ENV, fixture: FIXTURE, fetchImpl: keycloak.fetchImpl, runCommand });
  assert.deepEqual(first, { profile: 'daily', matches: 1, personas: 6 });
  assert.deepEqual(second, first);
  assert.equal(sqlRuns.length, 2);
  assert.equal(sqlRuns[0], sqlRuns[1]);
  assert.equal(keycloak.calls.filter((call) => call.method === 'POST' && call.url.endsWith('/users')).length, 6);
  assert.match(sqlRuns[0], /ON CONFLICT DO NOTHING/u);
  assert.doesNotMatch(sqlRuns[0], /BetUfuTest@2026|opaque-test-token|202600001/u);
});

test('prova regressão HTTP 201: cria usuário com corpo vazio sem disparar Unexpected end of JSON input', async () => {
  const keycloak = fakeFetchFactory();
  const result = await seedLocal({
    profile: 'daily',
    env: ENV,
    fixture: FIXTURE,
    fetchImpl: keycloak.fetchImpl,
    runCommand: async (command, args) => (args.includes('ps') ? 'abcdef123456' : ''),
  });
  assert.equal(result.personas, 6);
  assert.equal(keycloak.calls.filter((call) => call.method === 'POST' && call.url.endsWith('/users')).length, 6);
});

test('propaga erro de execução do psql com diagnóstico mas sanitiza DETAIL, segredos e hashes hex', async () => {
  const keycloak = fakeFetchFactory();
  const rawPsqlStderr = [
    'psql:-:196: ERROR: check constraint "sports_athlete_link_proposal_status_check" violated',
    'DETAIL: Failing row contains (c1901dd1-0000-5000-8000-000000000001, 202600001, dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE=).',
    'LINE 1: query mentioning sha256 a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2',
  ].join('\n');

  await assert.rejects(
    seedLocal({
      profile: 'daily',
      env: ENV,
      fixture: FIXTURE,
      fetchImpl: keycloak.fetchImpl,
      runCommand: async (command, args) => {
        if (args.includes('ps') && args.includes('postgres')) return 'abcdef123456';
        if (args.includes('psql')) throw new Error(`docker exec falhou com exit code 1: ${rawPsqlStderr}`);
        return '';
      },
    }),
    (err) => {
      assert.match(err.message, /docker exec falhou com exit code 1/u);
      assert.match(err.message, /ERROR: check constraint "sports_athlete_link_proposal_status_check" violated/u);
      assert.doesNotMatch(err.message, /DETAIL: Failing row contains/u);
      assert.doesNotMatch(err.message, /202600001/u);
      assert.doesNotMatch(err.message, /dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE=/u);
      assert.doesNotMatch(err.message, /a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2/u);
      assert.match(err.message, /\[REDACTED_HASH\]/u);
      return true;
    },
  );
});

test('sanitizeSqlError remove linhas DETAIL, mascara segredos do env e redige hashes SHA-256', () => {
  const input = [
    'psql:-:1: ERROR: relation "xyz" does not exist',
    'DETAIL: Key (token)=(local-password) already exists.',
    'LINE 1: query with a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2 and secret local-password',
  ].join('\n');

  const sanitized = sanitizeSqlError(input, ENV);
  assert.match(sanitized, /ERROR: relation "xyz" does not exist/u);
  assert.doesNotMatch(sanitized, /DETAIL:/u);
  assert.doesNotMatch(sanitized, /local-password/u);
  assert.match(sanitized, /\[REDACTED:KC_BOOTSTRAP_ADMIN_PASSWORD\]/u);
  assert.match(sanitized, /\[REDACTED_HASH\]/u);
});
