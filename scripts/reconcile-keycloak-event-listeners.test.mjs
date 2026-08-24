import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
  equivalentEventsConfig,
  main,
  parseArguments,
  reconcileKeycloakEventListeners,
  withRequiredListeners,
} from './reconcile-keycloak-event-listeners.mjs';

const BASE_ARGS = ['--base-url', 'http://127.0.0.1:8081', '--realm', 'betufu-local'];
const BASE_ENV = {
  KC_BOOTSTRAP_ADMIN_USERNAME: 'admin-user',
  KC_BOOTSTRAP_ADMIN_PASSWORD: 'password-never-log',
};
const TOKEN = 'token-never-log';

function jsonResponse(document, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async json() {
      return structuredClone(document);
    },
  };
}

function noContent(status = 204) {
  return { ok: status >= 200 && status < 300, status };
}

function createKeycloakDouble(initialConfig, { postWriteTransform = (value) => value } = {}) {
  let config = structuredClone(initialConfig);
  const calls = [];
  let putCount = 0;
  const fetchImpl = async (url, init = {}) => {
    calls.push({ url: String(url), init });
    if (String(url).endsWith('/realms/master/protocol/openid-connect/token')) {
      return jsonResponse({ access_token: TOKEN });
    }
    if (!String(url).endsWith('/admin/realms/betufu-local/events/config')) {
      return noContent(404);
    }
    if (init.method === 'GET') return jsonResponse(config);
    if (init.method === 'PUT') {
      putCount += 1;
      const written = JSON.parse(init.body);
      config = structuredClone(postWriteTransform(written, putCount));
      return noContent();
    }
    return noContent(405);
  };
  return {
    fetchImpl,
    calls,
    get config() { return structuredClone(config); },
    get putCount() { return putCount; },
  };
}

function createPostWriteFailureDouble(initialConfig, failConfirmation) {
  let config = structuredClone(initialConfig);
  let getCount = 0;
  let putCount = 0;
  const fetchImpl = async (url, init = {}) => {
    if (String(url).endsWith('/realms/master/protocol/openid-connect/token')) {
      return jsonResponse({ access_token: TOKEN });
    }
    if (init.method === 'PUT') {
      putCount += 1;
      config = JSON.parse(init.body);
      return noContent();
    }
    if (init.method === 'GET') {
      getCount += 1;
      if (putCount === 1 && getCount === 2) return failConfirmation(init);
      return jsonResponse(config);
    }
    return noContent(405);
  };
  return {
    fetchImpl,
    get config() { return structuredClone(config); },
    get putCount() { return putCount; },
  };
}

function outputCapture() {
  const lines = [];
  return { lines, log: (line) => lines.push(String(line)) };
}

describe('reconcile-keycloak-event-listeners', () => {
  it('faz dry-run por padrao sem executar PUT', async () => {
    const server = createKeycloakDouble({ eventsEnabled: false, eventsListeners: ['custom-a'] });
    const output = outputCapture();

    const result = await reconcileKeycloakEventListeners({
      argv: BASE_ARGS,
      env: BASE_ENV,
      fetchImpl: server.fetchImpl,
      log: output.log,
    });

    assert.deepEqual(result, { status: 'DRY_RUN', changed: true });
    assert.equal(server.putCount, 0);
    assert.deepEqual(server.config.eventsListeners, ['custom-a']);
    assert.deepEqual(output.lines, [
      '[reconcile-keycloak-event-listeners] DRY_RUN realm=betufu-local action=ADD_REQUIRED_LISTENERS',
    ]);
  });

  it('aplica uma vez, preserva todos os campos e faz uniao estavel dos listeners', async () => {
    const original = {
      eventsEnabled: false,
      eventsExpiration: 1234,
      enabledEventTypes: ['LOGIN'],
      eventsListeners: ['custom-a', 'jboss-logging', 'custom-b'],
      adminEventsEnabled: true,
      adminEventsDetailsEnabled: false,
    };
    const server = createKeycloakDouble(original);
    const output = outputCapture();

    const result = await reconcileKeycloakEventListeners({
      argv: [...BASE_ARGS, '--apply'],
      env: BASE_ENV,
      fetchImpl: server.fetchImpl,
      log: output.log,
    });

    assert.deepEqual(result, { status: 'APPLIED', changed: true });
    assert.equal(server.putCount, 1);
    assert.deepEqual(server.config, {
      ...original,
      eventsListeners: ['custom-a', 'jboss-logging', 'custom-b', 'betufu-password-event-listener'],
    });
    assert.deepEqual(output.lines, ['[reconcile-keycloak-event-listeners] APPLIED realm=betufu-local']);
  });

  it('na segunda execucao informa UNCHANGED e nao faz novo PUT', async () => {
    const server = createKeycloakDouble({ eventsEnabled: false, eventsListeners: ['custom-a'] });
    const firstOutput = outputCapture();
    const secondOutput = outputCapture();

    await reconcileKeycloakEventListeners({
      argv: [...BASE_ARGS, '--apply'], env: BASE_ENV, fetchImpl: server.fetchImpl, log: firstOutput.log,
    });
    const firstPutCount = server.putCount;
    const result = await reconcileKeycloakEventListeners({
      argv: [...BASE_ARGS, '--apply'], env: BASE_ENV, fetchImpl: server.fetchImpl, log: secondOutput.log,
    });

    assert.equal(firstPutCount, 1);
    assert.equal(server.putCount, 1);
    assert.deepEqual(result, { status: 'UNCHANGED', changed: false });
    assert.deepEqual(secondOutput.lines, ['[reconcile-keycloak-event-listeners] UNCHANGED realm=betufu-local']);
  });

  it('preserva listeners existentes, mantém a ordem e elimina duplicatas', () => {
    const preimage = {
      eventsEnabled: true,
      eventsListeners: ['custom-a', 'custom-a', 'betufu-password-event-listener'],
      customField: { nested: true },
    };
    assert.deepEqual(withRequiredListeners(preimage), {
      eventsEnabled: true,
      eventsListeners: ['custom-a', 'betufu-password-event-listener', 'jboss-logging'],
      customField: { nested: true },
    });
    assert.deepEqual(preimage.eventsListeners, ['custom-a', 'custom-a', 'betufu-password-event-listener']);
  });

  it('aceita reordenacao de listeners pelo Keycloak sem afrouxar os demais campos', () => {
    const expected = {
      eventsEnabled: false,
      eventsExpiration: 1234,
      eventsListeners: ['jboss-logging', 'betufu-password-event-listener'],
    };
    const reordered = {
      ...expected,
      eventsListeners: ['betufu-password-event-listener', 'jboss-logging'],
    };
    assert.equal(equivalentEventsConfig(expected, reordered), true);
    assert.equal(equivalentEventsConfig(expected, { ...reordered, eventsEnabled: true }), false);
    assert.equal(equivalentEventsConfig(expected, { ...reordered, eventsListeners: ['jboss-logging'] }), false);
  });

  for (const status of [401, 403, 500]) {
    it(`falha fechado quando a leitura recebe HTTP ${status}`, async () => {
      const fetchImpl = async (url) => {
        if (String(url).includes('/protocol/openid-connect/token')) return jsonResponse({ access_token: TOKEN });
        return noContent(status);
      };
      await assert.rejects(
        reconcileKeycloakEventListeners({ argv: BASE_ARGS, env: BASE_ENV, fetchImpl, log: () => {} }),
        (error) => error.code === 'HTTP_STATUS' && error.message.includes(`HTTP ${status}`),
      );
    });
  }

  it('falha fechado em timeout', async () => {
    const fetchImpl = async (url, init = {}) => {
      if (String(url).includes('/protocol/openid-connect/token')) return jsonResponse({ access_token: TOKEN });
      return new Promise((resolve, reject) => {
        init.signal.addEventListener(
          'abort',
          () => reject(new DOMException('server included password-never-log', 'AbortError')),
          { once: true },
        );
      });
    };
    await assert.rejects(
      reconcileKeycloakEventListeners({
        argv: [...BASE_ARGS, '--timeout-ms', '100'], env: BASE_ENV, fetchImpl, log: () => {},
      }),
      (error) => error.code === 'HTTP_TIMEOUT' && !error.message.includes(BASE_ENV.KC_BOOTSTRAP_ADMIN_PASSWORD),
    );
  });

  it('falha fechado quando a Admin API devolve JSON invalido', async () => {
    const fetchImpl = async (url) => {
      if (String(url).includes('/protocol/openid-connect/token')) return jsonResponse({ access_token: TOKEN });
      return {
        ok: true,
        status: 200,
        async json() { throw new SyntaxError('invalid password-never-log'); },
      };
    };
    await assert.rejects(
      reconcileKeycloakEventListeners({ argv: BASE_ARGS, env: BASE_ENV, fetchImpl, log: () => {} }),
      (error) => error.code === 'HTTP_JSON_INVALID' && !error.message.includes(BASE_ENV.KC_BOOTSTRAP_ADMIN_PASSWORD),
    );
  });

  it('tenta rollback do preimage e falha se a confirmacao pos-PUT divergir', async () => {
    const original = {
      eventsEnabled: false,
      eventsListeners: ['custom-a'],
      adminEventsEnabled: true,
    };
    const server = createKeycloakDouble(original, {
      postWriteTransform: (written, putCount) => (putCount === 1
        ? { ...written, eventsEnabled: true }
        : written),
    });

    await assert.rejects(
      reconcileKeycloakEventListeners({
        argv: [...BASE_ARGS, '--apply'], env: BASE_ENV, fetchImpl: server.fetchImpl, log: () => {},
      }),
      (error) => error.code === 'POST_WRITE_VERIFICATION_FAILED'
        && error.message.includes('primary=POST_WRITE_DIVERGENCE')
        && error.message.includes('rollback=confirmed'),
    );
    assert.equal(server.putCount, 2);
    assert.deepEqual(server.config, original);
  });

  for (const scenario of [
    {
      name: 'HTTP 500',
      primaryCode: 'HTTP_STATUS',
      failConfirmation: () => noContent(500),
    },
    {
      name: 'JSON invalido',
      primaryCode: 'HTTP_JSON_INVALID',
      failConfirmation: () => ({
        ok: true,
        status: 200,
        async json() { throw new SyntaxError('password-never-log'); },
      }),
    },
    {
      name: 'falha de transporte',
      primaryCode: 'HTTP_TRANSPORT',
      failConfirmation: () => { throw new Error('token-never-log'); },
    },
    {
      name: 'timeout',
      primaryCode: 'HTTP_TIMEOUT',
      failConfirmation: (init) => new Promise((resolve, reject) => {
        init.signal.addEventListener(
          'abort',
          () => reject(new DOMException('password-never-log', 'AbortError')),
          { once: true },
        );
      }),
      timeoutMs: 100,
    },
  ]) {
    it(`restaura o preimage quando a confirmacao pos-PUT sofre ${scenario.name}`, async () => {
      const original = {
        eventsEnabled: false,
        eventsListeners: ['custom-a'],
        adminEventsEnabled: true,
      };
      const server = createPostWriteFailureDouble(original, scenario.failConfirmation);
      const args = scenario.timeoutMs
        ? [...BASE_ARGS, '--timeout-ms', String(scenario.timeoutMs), '--apply']
        : [...BASE_ARGS, '--apply'];

      await assert.rejects(
        reconcileKeycloakEventListeners({
          argv: args, env: BASE_ENV, fetchImpl: server.fetchImpl, log: () => {},
        }),
        (error) => error.code === 'POST_WRITE_VERIFICATION_FAILED'
          && error.message.includes(`primary=${scenario.primaryCode}`)
          && error.message.includes('rollback=confirmed')
          && !error.message.includes(BASE_ENV.KC_BOOTSTRAP_ADMIN_PASSWORD)
          && !error.message.includes(TOKEN),
      );
      assert.equal(server.putCount, 2);
      assert.deepEqual(server.config, original);
    });
  }

  it('exige HTTPS fora de loopback e aceita HTTP apenas em loopback literal', () => {
    assert.throws(
      () => parseArguments(['--base-url', 'http://keycloak.example.test:8080', '--realm', 'betufu-local']),
      (error) => error.code === 'BASE_URL_TLS_REQUIRED',
    );
    assert.doesNotThrow(
      () => parseArguments(['--base-url', 'http://127.0.0.1:8080', '--realm', 'betufu-local']),
    );
    assert.doesNotThrow(
      () => parseArguments(['--base-url', 'http://[::1]:8080', '--realm', 'betufu-local']),
    );
    assert.doesNotThrow(
      () => parseArguments(['--base-url', 'https://keycloak.example.test', '--realm', 'betufu-local']),
    );
  });

  it('configura fetch para recusar redirects', async () => {
    const fetchImpl = async (url, init = {}) => {
      assert.equal(init.redirect, 'error');
      if (String(url).includes('/protocol/openid-connect/token')) {
        return jsonResponse({ access_token: TOKEN });
      }
      throw new TypeError('redirect blocked');
    };

    await assert.rejects(
      reconcileKeycloakEventListeners({ argv: BASE_ARGS, env: BASE_ENV, fetchImpl, log: () => {} }),
      (error) => error.code === 'HTTP_TRANSPORT',
    );
  });

  it('nao imprime usuario, senha, token nem corpo de erro', async () => {
    const output = [];
    const fetchImpl = async () => ({
      ok: false,
      status: 500,
      async json() {
        return { token: TOKEN, password: BASE_ENV.KC_BOOTSTRAP_ADMIN_PASSWORD };
      },
    });

    const exitCode = await main({
      argv: BASE_ARGS,
      env: BASE_ENV,
      fetchImpl,
      log: (line) => output.push(String(line)),
      errorLog: (line) => output.push(String(line)),
    });

    const rendered = output.join('\n');
    assert.equal(exitCode, 1);
    assert.doesNotMatch(rendered, /admin-user|password-never-log|token-never-log/u);
    assert.match(rendered, /HTTP 500/u);
  });

  it('recusa credenciais em argumento e valida argumentos publicos', () => {
    assert.throws(
      () => parseArguments([...BASE_ARGS, '--password=secret-never-echo']),
      (error) => error.code === 'ARGUMENT_UNKNOWN' && !error.message.includes('secret-never-echo'),
    );
    assert.throws(
      () => parseArguments(['--base-url', 'https://user:secret@example.test', '--realm', 'betufu-local']),
      (error) => error.code === 'BASE_URL_USERINFO' && !error.message.includes('secret'),
    );
  });
});
