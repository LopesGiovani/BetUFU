import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { EventEmitter } from 'node:events';
import {
  cp,
  mkdir,
  mkdtemp,
  readFile,
  rm,
  writeFile,
} from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { after, test } from 'node:test';
import { fileURLToPath } from 'node:url';

import {
  APPROVED_JAR_SHA256,
  APPROVED_JAR_SIZE,
  CookieJar,
  RuntimePrerequisiteContractError,
  assertExpectedRuntimeArtifact,
  assertNoBrowserTokenExposure,
  assertRedirectPolicy,
  assertRuntimeEvidence,
  buildProxyRequestHeaders,
  childHasExited,
  handleProxyRequest,
  parseAndAssertXsrfSetCookie,
  requestLocal,
  runRuntimeProof,
  sanitizeError,
  validateStaticContract,
  waitForBackendReady,
} from './validate-runtime-prerequisites.mjs';
import * as runtimePrerequisites from './validate-runtime-prerequisites.mjs';

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(scriptsDirectory, '..');
const validatorPath = join(scriptsDirectory, 'validate-runtime-prerequisites.mjs');
const temporaryRoots = [];

const COPY_PATHS = Object.freeze([
  'backend/pom.xml',
  'backend/src/main/java',
  'backend/src/main/resources/application.yaml',
  'backend/src/main/resources/application-dev.yaml',
  'backend/src/test/java/br/com/betufu/platform/security/CsrfSpaIT.java',
  'frontend/src/app',
  'infra/.env.example',
  'infra/keycloak/realm-betufu.json',
]);

const VALID_UUID = '123e4567-e89b-42d3-a456-426614174000';
const VALID_HASH = APPROVED_JAR_SHA256;
const VALID_SIZE = APPROVED_JAR_SIZE;
const VALID_NONCE = 'c2d4a9b8-0f41-4dca-87f5-e307cbbcdaf8';
const VALID_PROXY_NONCE = '413c1f95-01ef-4ac6-a521-a3a08b2a20c6';
const VALID_SENTINEL_NONCE = 'f73bdf56-93df-4cff-9d93-e342e2eacb34';
const VALID_STARTED_AT = '2026-08-13T03:45:00.000Z';

const APPROVED_ARTIFACT = Object.freeze({
  sha256: APPROVED_JAR_SHA256,
  size: APPROVED_JAR_SIZE,
});

const APPROVED_HTTP_XSRF_COOKIE =
  'XSRF-TOKEN=approved-xsrf-value; Path=/; SameSite=Lax';
const APPROVED_HTTPS_XSRF_COOKIE = APPROVED_HTTP_XSRF_COOKIE + '; Secure';
const APPROVED_ROTATED_XSRF_COOKIE =
  'XSRF-TOKEN=rotated-xsrf-value; Path=/; SameSite=Lax';
const APPROVED_XSRF_TOMBSTONE =
  'XSRF-TOKEN=; Expires=Thu, 01 Jan 1970 00:00:10 GMT; ' +
  'Path=/; SameSite=Lax';
const KEYCLOAK_LOGIN_URL =
  'http://localhost:8081/realms/betufu-local/login-actions/authenticate?session_code=opaque';
const CLIENT_AUTHORIZATION_URL =
  'http://localhost:4200/oauth2/authorization/keycloak';
const KEYCLOAK_AUTHORIZATION_URL =
  'http://localhost:8081/realms/betufu-local/protocol/openid-connect/auth?client_id=betufu-bff&state=opaque';
const CLIENT_CALLBACK_URL =
  'http://localhost:4200/login/oauth2/code/keycloak?code=opaque&state=opaque';
const CLIENT_ROOT_URL = 'http://localhost:4200/';
const CLIENT_LIVENESS_URL = 'http://localhost:4200/actuator/health/liveness';

function callbackResponse(setCookieHeaders, overrides = {}) {
  return {
    url: overrides.url ?? CLIENT_CALLBACK_URL,
    status: overrides.status ?? 302,
    headers: {
      location: overrides.location ?? '/',
      'set-cookie': setCookieHeaders,
    },
    body: '',
  };
}

function jarAfterCallback(setCookieHeaders) {
  const jar = new CookieJar();
  jar.apply([APPROVED_HTTP_XSRF_COOKIE], 'http://localhost:4200/login');
  jar.apply(setCookieHeaders, CLIENT_CALLBACK_URL);
  return jar;
}

function fakeHttpRequestFactory({
  status = 200,
  headers = {},
  body = '',
  onRequest,
} = {}) {
  return (requestOptions) => {
    onRequest?.(requestOptions);
    const request = new EventEmitter();
    request.write = () => true;
    request.destroy = (error) => queueMicrotask(() => request.emit('error', error));
    request.end = () => queueMicrotask(() => {
      const response = new EventEmitter();
      response.statusCode = status;
      response.headers = headers;
      response.destroy = (error) => queueMicrotask(() => response.emit('error', error));
      request.emit('response', response);
      if (body !== '') {
        response.emit('data', Buffer.from(body));
      }
      response.emit('end');
    });
    return request;
  };
}

after(async () => {
  await Promise.all(temporaryRoots.map((root) => rm(root, { force: true, recursive: true })));
});

async function createFixture() {
  const root = await mkdtemp(join(tmpdir(), 'betufu-runtime-prerequisites-'));
  temporaryRoots.push(root);
  for (const relativePath of COPY_PATHS) {
    const source = join(projectRoot, relativePath);
    const target = join(root, relativePath);
    await mkdir(dirname(target), { recursive: true });
    await cp(source, target, { recursive: true });
  }
  return root;
}

async function mutate(root, relativePath, mutation) {
  const path = join(root, relativePath);
  const original = await readFile(path, 'utf8');
  const changed = mutation(original);
  assert.notEqual(changed, original, 'fixture deve realmente mudar');
  await writeFile(path, changed, 'utf8');
}

async function mutateJson(root, relativePath, mutation) {
  const path = join(root, relativePath);
  const original = JSON.parse(await readFile(path, 'utf8'));
  const changed = structuredClone(original);
  mutation(changed);
  assert.notDeepEqual(changed, original, 'fixture JSON deve realmente mudar');
  await writeFile(path, JSON.stringify(changed, null, 2) + '\n', 'utf8');
}

async function expectRule(root, rule) {
  await assert.rejects(
    validateStaticContract(root),
    (error) => error instanceof RuntimePrerequisiteContractError && error.rule === rule,
  );
}

function validEvidence() {
  return {
    status: 'ok',
    loginFlow: 'authorization-code-hosted',
    statuses: {
      missingCookie: 403,
      missingHeader: 403,
      divergent: 403,
      firstPut: 200,
      secondPut: 200,
    },
    identityIds: [VALID_UUID, VALID_UUID],
    csrf: {
      different: true,
      tokenAHash: '1'.repeat(64),
      tokenBHash: '2'.repeat(64),
      cookieAApproved: true,
      cookieBApproved: true,
      httpCookie: {
        name: 'XSRF-TOKEN',
        hostOnly: true,
        path: '/',
        httpOnly: false,
        sameSite: 'Lax',
        secure: false,
      },
      httpsCookie: {
        name: 'XSRF-TOKEN',
        hostOnly: true,
        path: '/',
        httpOnly: false,
        sameSite: 'Lax',
        secure: true,
      },
    },
    redis: {
      connectionObserved: false,
      sentinelOccupied: true,
      connectionAttempts: 0,
      cleanupComplete: true,
    },
    browserExposure: {
      authorizationHeader: false,
      oidcToken: false,
      surface: 'http',
      urlToken: false,
      bodyToken: false,
      setCookieToken: false,
      cookieJarOriginPartitioned: true,
    },
    java: {
      specificationVersion: '25',
      vendor: 'Eclipse Adoptium',
    },
    ownedProcesses: {
      backend: {
        pid: 4242,
        nonce: VALID_NONCE,
        port: 8080,
        startedAt: VALID_STARTED_AT,
      },
      proxy: {
        pid: process.pid,
        nonce: VALID_PROXY_NONCE,
        port: 4200,
        startedAt: VALID_STARTED_AT,
      },
      redisSentinel: {
        pid: process.pid,
        nonce: VALID_SENTINEL_NONCE,
        host: '127.0.0.1',
        port: 6379,
        startedAt: VALID_STARTED_AT,
      },
    },
    proxyEvidence: {
      untrustedForwardedHeadersOverridden: true,
      trustedHttpsProducedSecureCookie: true,
    },
    jarHashBefore: VALID_HASH,
    jarHashAfter: VALID_HASH,
    jarSizeBefore: VALID_SIZE,
    jarSizeAfter: VALID_SIZE,
    jarSha256: VALID_HASH,
    jarSize: VALID_SIZE,
    identityUuid: VALID_UUID,
    cleanup: {
      proxy: true,
      backend: true,
      redisSentinel: true,
    },
  };
}

function fakeAdapter(overrides = {}) {
  const calls = [];
  const adapter = {
    async prepare() {
      calls.push('prepare');
      return { jarArtifact: { ...APPROVED_ARTIFACT } };
    },
    async startRedisSentinel() {
      calls.push('startRedisSentinel');
      return {
        connectionObserved: false,
        nonce: VALID_SENTINEL_NONCE,
        startedAt: VALID_STARTED_AT,
      };
    },
    async preflight() {
      calls.push('preflight');
    },
    async startBackend() {
      calls.push('startBackend');
      return { name: 'backend' };
    },
    async waitForBackend() {
      calls.push('waitForBackend');
    },
    async startProxy() {
      calls.push('startProxy');
      return { name: 'proxy' };
    },
    async exercise() {
      calls.push('exercise');
      const evidence = validEvidence();
      delete evidence.jarHashAfter;
      return evidence;
    },
    async finish() {
      calls.push('finish');
      return { ...APPROVED_ARTIFACT };
    },
    async stopProxy() {
      calls.push('stopProxy');
    },
    async stopBackend() {
      calls.push('stopBackend');
    },
    async stopRedisSentinel() {
      calls.push('stopRedisSentinel');
    },
    ...overrides,
  };
  return { adapter, calls };
}

function attachFakeRedisSentinel(
  adapter,
  calls,
  { connectionObserved = false, stopError } = {},
) {
  const sentinel = {
    connectionObserved,
    nonce: VALID_SENTINEL_NONCE,
    startedAt: VALID_STARTED_AT,
  };
  adapter.startRedisSentinel = async () => {
    calls.push('startRedisSentinel');
    return sentinel;
  };
  adapter.stopRedisSentinel = async (handle) => {
    calls.push('stopRedisSentinel');
    assert.equal(handle, sentinel);
    if (stopError !== undefined) {
      throw stopError;
    }
  };
  return sentinel;
}

async function withTestDeadline(promise, timeoutMilliseconds = 500) {
  let timeout;
  const deadline = new Promise((resolvePromise, rejectPromise) => {
    timeout = setTimeout(() => rejectPromise(new Error('TEST_DEADLINE_EXCEEDED')), timeoutMilliseconds);
  });
  try {
    return await Promise.race([promise, deadline]);
  } finally {
    clearTimeout(timeout);
  }
}

test('aceita o contrato canonico da Task 2', async () => {
  await validateStaticContract(projectRoot);
});

test('aceita uma fixture isolada equivalente ao checkout', async () => {
  const root = await createFixture();
  await validateStaticContract(root);
});

for (const [label, mutateRealm] of [
  ['firstName ausente', (realm) => { delete realm.users[0].firstName; }],
  ['firstName vazio', (realm) => { realm.users[0].firstName = '   '; }],
  ['lastName ausente', (realm) => { delete realm.users[0].lastName; }],
  ['lastName vazio', (realm) => { realm.users[0].lastName = '   '; }],
  ['acao obrigatoria explicita', (realm) => {
    realm.users[0].requiredActions = ['UPDATE_PROFILE'];
  }],
  ['senha temporaria', (realm) => {
    realm.users[0].credentials[0].temporary = true;
  }],
  ['verificacao de email obrigatoria', (realm) => { realm.verifyEmail = true; }],
]) {
  test('rejeita usuario tecnico Keycloak com ' + label, async () => {
    const root = await createFixture();
    await mutateJson(root, 'infra/keycloak/realm-betufu.json', mutateRealm);
    await expectRule(root, 'KEYCLOAK_TECHNICAL_USER');
  });
}

test('rejeita fallback em variavel datasource', async () => {
  const root = await createFixture();
  const placeholder = '$' + '{BETUFU_DB_JDBC_URL}';
  const fallback = '$' + '{BETUFU_DB_JDBC_URL:' + LOCAL_FAKE_URL + '}';
  await mutate(
    root,
    'backend/src/main/resources/application.yaml',
    (source) => source.replace(placeholder, fallback),
  );
  await expectRule(root, 'DATASOURCE_PLACEHOLDER');
});

const LOCAL_FAKE_URL = 'jdbc:postgresql://fallback.invalid:5432/unsafe';

test('rejeita URL local embutida em application.yaml', async () => {
  const root = await createFixture();
  await mutate(
    root,
    'backend/src/main/resources/application.yaml',
    (source) => source + '\nunsafe: jdbc:postgresql://127.0.0.1:5432/betufu_local\n',
  );
  await expectRule(root, 'DATASOURCE_LOCAL_LEAK');
});

test('rejeita SameSite divergente', async () => {
  const root = await createFixture();
  await mutate(
    root,
    'backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java',
    (source) => source.replace('.sameSite("Lax")', '.sameSite("Strict")'),
  );
  await expectRule(root, 'CSRF_COOKIE');
});

test('rejeita Secure fixado no repository', async () => {
  const root = await createFixture();
  await mutate(
    root,
    'backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java',
    (source) => source.replace('.sameSite("Lax")', '.sameSite("Lax").secure(true)'),
  );
  await expectRule(root, 'CSRF_COOKIE');
});

for (const [label, method] of [
  ['HttpOnly', '.httpOnly(true)'],
  ['Max-Age', '.maxAge(java.time.Duration.ofHours(1))'],
  ['Partitioned', '.partitioned(true)'],
]) {
  test('rejeita atributo extra ' + label + ' no cookie', async () => {
    const root = await createFixture();
    await mutate(
      root,
      'backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java',
      (source) => source.replace('.sameSite("Lax")', '.sameSite("Lax")' + method),
    );
    await expectRule(root, 'CSRF_COOKIE');
  });
}

test('rejeita segundo customizer que sobrescreveria os atributos aprovados', async () => {
  const root = await createFixture();
  await mutate(
    root,
    'backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java',
    (source) => source.replace(
      '        return repository;',
      '        repository.setCookieCustomizer(cookie -> cookie.path("/unsafe"));\n'
        + '        return repository;',
    ),
  );
  await expectRule(root, 'CSRF_COOKIE');
});

test('rejeita bypass CSRF', async () => {
  const root = await createFixture();
  await mutate(
    root,
    'backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java',
    (source) => source.replace(
      '.spa()\n                        .csrfTokenRepository(csrfTokenRepository)',
      '.spa()\n                        .ignoringRequestMatchers("/api/v1/**")\n'
        + '                        .csrfTokenRepository(csrfTokenRepository)',
    ),
  );
  await expectRule(root, 'CSRF_BYPASS');
});

test('rejeita endpoint CSRF no codigo de producao', async () => {
  const root = await createFixture();
  const path = join(
    root,
    'backend/src/main/java/br/com/betufu/platform/security/UnsafeCsrfController.java',
  );
  await writeFile(
    path,
    'class UnsafeCsrfController { @GetMapping("/api/v1/session/csrf") void csrf() {} }\n',
    'utf8',
  );
  await expectRule(root, 'CSRF_ENDPOINT');
});

test('rejeita leitura manual de cookie no Angular de producao', async () => {
  const root = await createFixture();
  const path = join(root, 'frontend/src/app/unsafe-xsrf.ts');
  await writeFile(path, 'export const unsafe = document.cookie;\n', 'utf8');
  await expectRule(root, 'ANGULAR_MANUAL_XSRF');
});

test('rejeita header XSRF manual fora da configuracao Angular', async () => {
  const root = await createFixture();
  const path = join(root, 'frontend/src/app/unsafe-xsrf.ts');
  await writeFile(path, "export const unsafe = 'X-XSRF-TOKEN';\n", 'utf8');
  await expectRule(root, 'ANGULAR_MANUAL_XSRF');
});

test('comentario nao satisfaz o contrato do repository', async () => {
  const root = await createFixture();
  await mutate(
    root,
    'backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java',
    (source) => source.replace(
      'CookieCsrfTokenRepository.withHttpOnlyFalse()',
      'null /* CookieCsrfTokenRepository.withHttpOnlyFalse() */',
    ),
  );
  await expectRule(root, 'CSRF_REPOSITORY');
});

test('erro contratual nao inclui conteudo sensivel da fixture', async () => {
  const root = await createFixture();
  const canary = 'SENSITIVE_CANARY_VALUE';
  await mutate(
    root,
    'backend/src/main/resources/application.yaml',
    (source) => source.replace('$' + '{BETUFU_DB_USER}', canary),
  );
  let message = '';
  try {
    await validateStaticContract(root);
  } catch (error) {
    message = sanitizeError(error);
  }
  assert.doesNotMatch(message, /SENSITIVE_CANARY_VALUE/u);
});

test('sanitiza falha interna desconhecida', () => {
  assert.equal(
    sanitizeError(new Error('SENSITIVE_CANARY_VALUE')),
    'INTERNAL_ERROR: falha interna durante a validacao.',
  );
});

test('CLI rejeita argumento desconhecido sem executar runtime', () => {
  const result = spawnSync(process.execPath, [validatorPath, '--unknown'], {
    cwd: projectRoot,
    encoding: 'utf8',
    windowsHide: true,
  });
  assert.equal(result.status, 2);
  assert.match(result.stderr, /^Uso: node scripts\/validate-runtime-prerequisites\.mjs/mu);
});

test('aceita evidencia runtime completa', () => {
  assert.equal(assertRuntimeEvidence(validEvidence()), true);
});

test('ancoras aprovadas do JAR possuem formato auditavel', () => {
  assert.match(APPROVED_JAR_SHA256, /^[0-9A-F]{64}$/u);
  assert.equal(Number.isSafeInteger(APPROVED_JAR_SIZE), true);
  assert.equal(APPROVED_JAR_SIZE > 0, true);
});

test('aceita somente o SHA e tamanho aprovados do JAR', () => {
  assert.doesNotThrow(() => assertExpectedRuntimeArtifact(
    APPROVED_ARTIFACT,
    APPROVED_ARTIFACT,
  ));
});

for (const [label, actual, expectedRule] of [
  [
    'hash esperado nao ancorado',
    APPROVED_ARTIFACT,
    'RUNTIME_ARTIFACT_ANCHOR',
  ],
  [
    'hash observado divergente',
    { ...APPROVED_ARTIFACT, sha256: '0'.repeat(64) },
    'RUNTIME_ARTIFACT_DRIFT',
  ],
  [
    'tamanho esperado nao ancorado',
    APPROVED_ARTIFACT,
    'RUNTIME_ARTIFACT_ANCHOR',
  ],
  [
    'tamanho observado divergente',
    { ...APPROVED_ARTIFACT, size: APPROVED_JAR_SIZE - 1 },
    'RUNTIME_ARTIFACT_DRIFT',
  ],
]) {
  test('rejeita artefato runtime: ' + label, () => {
    let expected = APPROVED_ARTIFACT;
    if (label === 'hash esperado nao ancorado') {
      expected = { ...APPROVED_ARTIFACT, sha256: 'F'.repeat(64) };
    } else if (label === 'tamanho esperado nao ancorado') {
      expected = { ...APPROVED_ARTIFACT, size: APPROVED_JAR_SIZE + 1 };
    }
    assert.throws(
      () => assertExpectedRuntimeArtifact(actual, expected),
      (error) => error.rule === expectedRule,
    );
  });
}

test('aceita Set-Cookie XSRF browser-like exato em HTTP e HTTPS', () => {
  assert.doesNotThrow(() => parseAndAssertXsrfSetCookie(
    [APPROVED_HTTP_XSRF_COOKIE],
    'http://localhost:4200/login',
  ));
  assert.doesNotThrow(() => parseAndAssertXsrfSetCookie(
    [APPROVED_HTTPS_XSRF_COOKIE],
    'https://localhost:4200/login',
    true,
  ));
});

for (const [label, header, url, expectedSecure = false, rule = 'RUNTIME_XSRF_COOKIE'] of [
  ['HttpOnly', APPROVED_HTTP_XSRF_COOKIE + '; HttpOnly', 'http://localhost:4200/'],
  ['Domain', APPROVED_HTTP_XSRF_COOKIE + '; Domain=localhost', 'http://localhost:4200/'],
  ['Path ausente', 'XSRF-TOKEN=value; SameSite=Lax', 'http://localhost:4200/'],
  ['Path divergente', 'XSRF-TOKEN=value; Path=/api; SameSite=Lax', 'http://localhost:4200/'],
  ['SameSite ausente', 'XSRF-TOKEN=value; Path=/', 'http://localhost:4200/'],
  ['SameSite divergente', 'XSRF-TOKEN=value; Path=/; SameSite=None', 'http://localhost:4200/'],
  ['valor com espaco', 'XSRF-TOKEN=abc xyz; Path=/; SameSite=Lax', 'http://localhost:4200/', false, 'RUNTIME_COOKIE'],
  ['valor com aspas', 'XSRF-TOKEN="abc"; Path=/; SameSite=Lax', 'http://localhost:4200/'],
  ['Secure inesperado em HTTP', APPROVED_HTTPS_XSRF_COOKIE, 'http://localhost:4200/'],
  ['Secure com valor', APPROVED_HTTP_XSRF_COOKIE + '; Secure=0', 'https://localhost:4200/', true, 'RUNTIME_COOKIE'],
  ['Secure ausente em HTTPS', APPROVED_HTTP_XSRF_COOKIE, 'https://localhost:4200/', true],
]) {
  test('rejeita Set-Cookie XSRF nao browser-like: ' + label, () => {
    assert.throws(
      () => parseAndAssertXsrfSetCookie([header], url, expectedSecure),
      (error) => error.rule === rule,
    );
  });
}

const APPROVED_CALLBACK_XSRF_HEADERS = Object.freeze([
  'JSESSIONID=old-session; Path=/; HttpOnly',
  APPROVED_XSRF_TOMBSTONE,
  'JSESSIONID=new-session; Path=/; HttpOnly',
  APPROVED_ROTATED_XSRF_COOKIE,
]);

test('atribui a rotacao XSRF ao callback OAuth2 unico e preserva a ordem tombstone para B', () => {
  const jar = jarAfterCallback(APPROVED_CALLBACK_XSRF_HEADERS);
  const cookieB = runtimePrerequisites.assertOAuthXsrfRotation(
    [callbackResponse(APPROVED_CALLBACK_XSRF_HEADERS)],
    jar,
    'approved-xsrf-value',
  );

  assert.equal(cookieB.value, 'rotated-xsrf-value');
  assert.deepEqual(
    jar.cookies
      .filter((cookie) => cookie.name === 'XSRF-TOKEN')
      .map((cookie) => cookie.value),
    ['rotated-xsrf-value'],
  );
});

for (const [label, history] of [
  ['callback ausente', [{ url: CLIENT_ROOT_URL, status: 403, headers: {}, body: '' }]],
  [
    'callback duplicado',
    [
      callbackResponse(APPROVED_CALLBACK_XSRF_HEADERS),
      callbackResponse(APPROVED_CALLBACK_XSRF_HEADERS),
    ],
  ],
  [
    'callback de erro',
    [callbackResponse(APPROVED_CALLBACK_XSRF_HEADERS, {
      url: 'http://localhost:4200/login/oauth2/code/keycloak?error=access_denied&state=opaque',
    })],
  ],
  [
    'callback com destino nao raiz',
    [callbackResponse(APPROVED_CALLBACK_XSRF_HEADERS, { location: '/login?error' })],
  ],
  [
    'callback com status nao canonico',
    [callbackResponse(APPROVED_CALLBACK_XSRF_HEADERS, { status: 303 })],
  ],
  [
    'resposta posterior ao callback reemite XSRF',
    [
      callbackResponse(APPROVED_CALLBACK_XSRF_HEADERS),
      {
        url: CLIENT_ROOT_URL,
        status: 403,
        headers: { 'set-cookie': [APPROVED_ROTATED_XSRF_COOKIE] },
        body: '',
      },
    ],
  ],
  [
    'callback excede o limite total de Set-Cookie',
    [callbackResponse([
      ...Array.from(
        { length: 63 },
        (_, index) => 'aux' + index + '=value; Path=/',
      ),
      APPROVED_XSRF_TOMBSTONE,
      APPROVED_ROTATED_XSRF_COOKIE,
    ])],
  ],
]) {
  test('rejeita causalidade XSRF ambigua: ' + label, () => {
    const jar = jarAfterCallback(APPROVED_CALLBACK_XSRF_HEADERS);
    assert.throws(
      () => runtimePrerequisites.assertOAuthXsrfRotation(
        history,
        jar,
        'approved-xsrf-value',
      ),
      (error) => error.rule === 'RUNTIME_XSRF_ROTATION',
    );
  });
}

for (const [label, headers] of [
  ['tombstone ausente', [APPROVED_ROTATED_XSRF_COOKIE]],
  ['cookie B ausente', [APPROVED_XSRF_TOMBSTONE]],
  [
    'tombstone duplicado',
    [APPROVED_XSRF_TOMBSTONE, APPROVED_XSRF_TOMBSTONE, APPROVED_ROTATED_XSRF_COOKIE],
  ],
  [
    'cookie B duplicado',
    [APPROVED_XSRF_TOMBSTONE, APPROVED_ROTATED_XSRF_COOKIE, APPROVED_ROTATED_XSRF_COOKIE],
  ],
  ['ordem invertida', [APPROVED_ROTATED_XSRF_COOKIE, APPROVED_XSRF_TOMBSTONE]],
  [
    'tombstone com valor',
    ['XSRF-TOKEN=not-empty; Expires=Thu, 01 Jan 1970 00:00:10 GMT; Path=/; SameSite=Lax', APPROVED_ROTATED_XSRF_COOKIE],
  ],
  [
    'tombstone quoted',
    ['XSRF-TOKEN=""; Expires=Thu, 01 Jan 1970 00:00:10 GMT; Path=/; SameSite=Lax', APPROVED_ROTATED_XSRF_COOKIE],
  ],
  [
    'tombstone sem expiracao',
    ['XSRF-TOKEN=; Path=/; SameSite=Lax', APPROVED_ROTATED_XSRF_COOKIE],
  ],
  [
    'tombstone com expiracao futura',
    ['XSRF-TOKEN=; Expires=Thu, 01 Jan 2099 00:00:10 GMT; Path=/; SameSite=Lax', APPROVED_ROTATED_XSRF_COOKIE],
  ],
  [
    'tombstone com expiracao divergente',
    ['XSRF-TOKEN=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; SameSite=Lax', APPROVED_ROTATED_XSRF_COOKIE],
  ],
  [
    'tombstone com atributo extra',
    [APPROVED_XSRF_TOMBSTONE + '; Priority=High', APPROVED_ROTATED_XSRF_COOKIE],
  ],
  [
    'tombstone com Max-Age redundante',
    [APPROVED_XSRF_TOMBSTONE + '; Max-Age=0', APPROVED_ROTATED_XSRF_COOKIE],
  ],
  [
    'tombstone com Domain',
    [APPROVED_XSRF_TOMBSTONE + '; Domain=localhost', APPROVED_ROTATED_XSRF_COOKIE],
  ],
  [
    'tombstone Secure',
    [APPROVED_XSRF_TOMBSTONE + '; Secure', APPROVED_ROTATED_XSRF_COOKIE],
  ],
  [
    'tombstone HttpOnly',
    [APPROVED_XSRF_TOMBSTONE + '; HttpOnly', APPROVED_ROTATED_XSRF_COOKIE],
  ],
  [
    'tombstone Partitioned',
    [APPROVED_XSRF_TOMBSTONE + '; Partitioned', APPROVED_ROTATED_XSRF_COOKIE],
  ],
  [
    'tombstone com Path divergente',
    ['XSRF-TOKEN=; Expires=Thu, 01 Jan 1970 00:00:10 GMT; Path=/api; SameSite=Lax', APPROVED_ROTATED_XSRF_COOKIE],
  ],
  [
    'tombstone com SameSite divergente',
    ['XSRF-TOKEN=; Expires=Thu, 01 Jan 1970 00:00:10 GMT; Path=/; SameSite=Strict', APPROVED_ROTATED_XSRF_COOKIE],
  ],
  [
    'cookie B igual ao A',
    [APPROVED_XSRF_TOMBSTONE, APPROVED_HTTP_XSRF_COOKIE],
  ],
  [
    'cookie B quoted',
    [APPROVED_XSRF_TOMBSTONE, 'XSRF-TOKEN="rotated-xsrf-value"; Path=/; SameSite=Lax'],
  ],
  [
    'cookie B expirado',
    [APPROVED_XSRF_TOMBSTONE, APPROVED_ROTATED_XSRF_COOKIE + '; Max-Age=0'],
  ],
]) {
  test('rejeita sequencia de rotacao XSRF: ' + label, () => {
    const validJar = jarAfterCallback(APPROVED_CALLBACK_XSRF_HEADERS);
    assert.throws(
      () => runtimePrerequisites.assertOAuthXsrfRotation(
        [callbackResponse(headers)],
        validJar,
        'approved-xsrf-value',
      ),
      (error) => error.rule === 'RUNTIME_XSRF_ROTATION'
        || error.rule === 'RUNTIME_XSRF_COOKIE',
    );
  });
}

test('rejeita jar que nao termina com um unico cookie B no escopo cliente', () => {
  const jar = jarAfterCallback(APPROVED_CALLBACK_XSRF_HEADERS);
  jar.apply(
    ['XSRF-TOKEN=alternate; Path=/api; SameSite=Lax'],
    CLIENT_CALLBACK_URL,
  );

  assert.throws(
    () => runtimePrerequisites.assertOAuthXsrfRotation(
      [callbackResponse(APPROVED_CALLBACK_XSRF_HEADERS)],
      jar,
      'approved-xsrf-value',
    ),
    (error) => error.rule === 'RUNTIME_XSRF_ROTATION',
  );
});

test('liveness sem Set-Cookie XSRF preserva exatamente o cookie B', () => {
  const jar = jarAfterCallback(APPROVED_CALLBACK_XSRF_HEADERS);
  const cookieB = runtimePrerequisites.assertOAuthXsrfRotation(
    [callbackResponse(APPROVED_CALLBACK_XSRF_HEADERS)],
    jar,
    'approved-xsrf-value',
  );
  const liveness = {
    url: CLIENT_LIVENESS_URL,
    status: 200,
    headers: { 'set-cookie': ['JSESSIONID=new-session; Path=/; HttpOnly'] },
    body: '',
  };
  jar.apply(liveness.headers['set-cookie'], liveness.url);

  assert.equal(
    runtimePrerequisites.assertXsrfContinuity(liveness, jar, cookieB).value,
    'rotated-xsrf-value',
  );
});

for (const [label, responseHeaders, mutateJar, status = 200] of [
  ['reemitir o mesmo B', [APPROVED_ROTATED_XSRF_COOKIE], () => {}],
  [
    'substituir B',
    ['XSRF-TOKEN=third-value; Path=/; SameSite=Lax'],
    (jar, headers) => jar.apply(headers, CLIENT_LIVENESS_URL),
  ],
  [
    'remover B',
    [APPROVED_XSRF_TOMBSTONE],
    (jar, headers) => jar.apply(headers, CLIENT_LIVENESS_URL),
  ],
  ['alterar o jar sem header XSRF', [], (jar) => jar.remove('XSRF-TOKEN')],
  ['retornar status nao 200', [], () => {}, 503],
  [
    'exceder o limite total de Set-Cookie',
    Array.from({ length: 65 }, (_, index) => 'aux' + index + '=value; Path=/'),
    () => {},
  ],
]) {
  test('rejeita quebra de continuidade XSRF no liveness: ' + label, () => {
    const jar = jarAfterCallback(APPROVED_CALLBACK_XSRF_HEADERS);
    const cookieB = runtimePrerequisites.assertOAuthXsrfRotation(
      [callbackResponse(APPROVED_CALLBACK_XSRF_HEADERS)],
      jar,
      'approved-xsrf-value',
    );
    mutateJar(jar, responseHeaders);
    const response = {
      url: CLIENT_LIVENESS_URL,
      status,
      headers: { 'set-cookie': responseHeaders },
      body: '',
    };

    assert.throws(
      () => runtimePrerequisites.assertXsrfContinuity(response, jar, cookieB),
      (error) => error.rule === 'RUNTIME_XSRF_CONTINUITY',
    );
  });
}

test('erro de rotacao XSRF nao revela valor sensivel', () => {
  const canary = 'SENSITIVE_ROTATION_CANARY';
  let failure;
  try {
    runtimePrerequisites.assertOAuthXsrfRotation(
      [callbackResponse([
        'XSRF-TOKEN=' + canary + '; Expires=Thu, 01 Jan 1970 00:00:10 GMT; Path=/; SameSite=Lax',
        APPROVED_ROTATED_XSRF_COOKIE,
      ])],
      jarAfterCallback(APPROVED_CALLBACK_XSRF_HEADERS),
      'approved-xsrf-value',
    );
  } catch (error) {
    failure = error;
  }

  assert.equal(failure?.rule, 'RUNTIME_XSRF_ROTATION');
  assert.doesNotMatch(sanitizeError(failure), /SENSITIVE_ROTATION_CANARY/u);
});

for (const transition of [
  {
    label: 'authorization cliente para authorization Keycloak',
    currentUrl: CLIENT_AUTHORIZATION_URL,
    nextUrl: KEYCLOAK_AUTHORIZATION_URL,
    status: 302,
    method: 'GET',
    hasBody: false,
  },
  {
    label: 'authorization Keycloak para formulario hospedado',
    currentUrl: KEYCLOAK_AUTHORIZATION_URL,
    nextUrl: KEYCLOAK_LOGIN_URL,
    status: 302,
    method: 'GET',
    hasBody: false,
  },
  {
    label: 'formulario hospedado para callback cliente',
    currentUrl: KEYCLOAK_LOGIN_URL,
    nextUrl: CLIENT_CALLBACK_URL,
    status: 302,
    method: 'POST',
    hasBody: true,
    sensitivePostUrl: KEYCLOAK_LOGIN_URL,
  },
  {
    label: 'callback cliente para raiz',
    currentUrl: CLIENT_CALLBACK_URL,
    nextUrl: CLIENT_ROOT_URL,
    status: 302,
    method: 'GET',
    hasBody: false,
  },
]) {
  test('aceita transicao OAuth2 exata: ' + transition.label, () => {
    assert.equal(assertRedirectPolicy(transition), true);
  });
}

for (const diagnostic of [
  {
    label: 'destino interno do Keycloak ainda nao classificado',
    currentUrl: KEYCLOAK_AUTHORIZATION_URL,
    nextUrl:
      'http://localhost:8081/realms/betufu-local/login-actions/restart/SENSITIVE_PATH_CANARY?value=SENSITIVE_QUERY_CANARY',
    method: 'GET',
    hasBody: false,
    expected:
      'RUNTIME_REDIRECT: salto OAuth2 nao classificado; atual=keycloak-authorization; destino=keycloak-login-action-other; metodo=GET; status=302.',
  },
  {
    label: 'destino interno do cliente ainda nao classificado',
    currentUrl: CLIENT_CALLBACK_URL,
    nextUrl:
      'http://localhost:4200/login/SENSITIVE_PATH_CANARY?reason=SENSITIVE_QUERY_CANARY',
    method: 'GET',
    hasBody: false,
    expected:
      'RUNTIME_REDIRECT: salto OAuth2 nao classificado; atual=client-callback; destino=client-login-other; metodo=GET; status=302.',
  },
  {
    label: 'endpoint atual do Keycloak ainda nao classificado',
    currentUrl:
      'http://localhost:8081/realms/betufu-local/protocol/SENSITIVE_PATH_CANARY?value=SENSITIVE_QUERY_CANARY',
    nextUrl: CLIENT_CALLBACK_URL,
    method: 'GET',
    hasBody: false,
    expected:
      'RUNTIME_REDIRECT: salto OAuth2 nao classificado; atual=keycloak-protocol-other; destino=client-callback; metodo=GET; status=302.',
  },
  {
    label: 'metodo nao aprovado sem ecoar seu valor',
    currentUrl: CLIENT_CALLBACK_URL,
    nextUrl: 'http://localhost:4200/?value=SENSITIVE_QUERY_CANARY',
    method: 'GET\nSENSITIVE_METHOD_CANARY',
    hasBody: false,
    expected:
      'RUNTIME_REDIRECT: salto OAuth2 nao classificado; atual=client-callback; destino=client-root-other; metodo=OTHER; status=302.',
  },
]) {
  test('diagnostica salto OAuth2 sem revelar dados: ' + diagnostic.label, () => {
    assert.throws(
      () => assertRedirectPolicy({ status: 302, ...diagnostic }),
      (error) => {
        const sanitized = sanitizeError(error);
        return error.rule === 'RUNTIME_REDIRECT'
          && sanitized === diagnostic.expected
          && !sanitized.includes('SENSITIVE_PATH_CANARY')
          && !sanitized.includes('SENSITIVE_QUERY_CANARY')
          && !sanitized.includes('SENSITIVE_METHOD_CANARY')
          && !sanitized.includes('http://')
          && !sanitized.includes('?');
      },
    );
  });
}

test('diagnostica transicao conhecida fora da ordem sem revelar URLs', () => {
  assert.throws(
    () => assertRedirectPolicy({
      status: 302,
      currentUrl: CLIENT_AUTHORIZATION_URL,
      nextUrl: CLIENT_ROOT_URL,
      method: 'GET',
      hasBody: false,
    }),
    (error) => sanitizeError(error) ===
      'RUNTIME_REDIRECT: transicao OAuth2 fora da politica; atual=client-authorization; destino=client-root; metodo=GET; status=302.',
  );
});

test('classifica callback OAuth2 para login error como falha de login', () => {
  assert.throws(
    () => assertRedirectPolicy({
      status: 302,
      currentUrl: CLIENT_CALLBACK_URL,
      nextUrl: 'http://localhost:4200/login?error',
      method: 'GET',
      hasBody: false,
    }),
    (error) => error.rule === 'RUNTIME_LOGIN'
      && sanitizeError(error) === 'RUNTIME_LOGIN: callback OAuth2 terminou no handler de falha.',
  );
});

test('classifica callback OAuth2 com erro do provider como falha de login', () => {
  const providerFailureCallback =
    'http://localhost:4200/login/oauth2/code/keycloak?error=access_denied&state=opaque';
  assert.throws(
    () => assertRedirectPolicy({
      status: 302,
      currentUrl: providerFailureCallback,
      nextUrl: 'http://localhost:4200/login?error',
      method: 'GET',
      hasBody: false,
    }),
    (error) => error.rule === 'RUNTIME_LOGIN'
      && sanitizeError(error) === 'RUNTIME_LOGIN: callback OAuth2 terminou no handler de falha.'
      && !sanitizeError(error).includes('access_denied')
      && !sanitizeError(error).includes('opaque'),
  );
});

for (const status of [301, 303]) {
  test('aceita status ' + status + ' no POST hospedado para o callback', () => {
    assert.equal(assertRedirectPolicy({
      status,
      currentUrl: KEYCLOAK_LOGIN_URL,
      nextUrl: CLIENT_CALLBACK_URL,
      method: 'POST',
      hasBody: true,
      sensitivePostUrl: KEYCLOAK_LOGIN_URL,
    }), true);
  });
}

for (const [label, currentUrl, nextUrl, method, hasBody] of [
  ['authorization cliente para raiz', CLIENT_AUTHORIZATION_URL, CLIENT_ROOT_URL, 'GET', false],
  ['authorization cliente para callback', CLIENT_AUTHORIZATION_URL, CLIENT_CALLBACK_URL, 'GET', false],
  ['authorization Keycloak para raiz', KEYCLOAK_AUTHORIZATION_URL, CLIENT_ROOT_URL, 'GET', false],
  ['callback cliente para authorization cliente', CLIENT_CALLBACK_URL, CLIENT_AUTHORIZATION_URL, 'GET', false],
  ['callback cliente para Keycloak', CLIENT_CALLBACK_URL, KEYCLOAK_AUTHORIZATION_URL, 'GET', false],
]) {
  test('rejeita transicao OAuth2 fora de ordem: ' + label, () => {
    assert.throws(
      () => assertRedirectPolicy({ status: 302, currentUrl, nextUrl, method, hasBody }),
      (error) => error.rule === 'RUNTIME_REDIRECT',
    );
  });
}

for (const [label, currentUrl, nextUrl, method, hasBody] of [
  ['authorization cliente com POST', CLIENT_AUTHORIZATION_URL, KEYCLOAK_AUTHORIZATION_URL, 'POST', true],
  ['formulario hospedado com GET', KEYCLOAK_LOGIN_URL, CLIENT_CALLBACK_URL, 'GET', false],
  ['formulario hospedado sem body', KEYCLOAK_LOGIN_URL, CLIENT_CALLBACK_URL, 'POST', false],
  ['callback cliente com POST', CLIENT_CALLBACK_URL, CLIENT_ROOT_URL, 'POST', true],
]) {
  test('rejeita metodo ou body divergente na transicao OAuth2: ' + label, () => {
    assert.throws(
      () => assertRedirectPolicy({ status: 302, currentUrl, nextUrl, method, hasBody }),
      (error) => error.rule === 'RUNTIME_REDIRECT',
    );
  });
}

for (const [label, callbackQuery] of [
  ['code ausente', 'state=opaque'],
  ['code vazio', 'code=&state=opaque'],
  ['code duplicado', 'code=opaque&code=other&state=opaque'],
  ['code duplicado percent-encoded', 'code=opaque&%63ode=other&state=opaque'],
  ['state ausente', 'code=opaque'],
  ['state vazio', 'code=opaque&state='],
  ['state duplicado', 'code=opaque&state=opaque&state=other'],
  ['state duplicado percent-encoded', 'code=opaque&state=opaque&%73tate=other'],
  ['code misturado com error', 'code=opaque&state=opaque&error=access_denied'],
  ['code misturado com error percent-encoded', 'code=opaque&state=opaque&%65rror=access_denied'],
  ['code misturado com error_description', 'code=opaque&state=opaque&error_description=denied'],
  ['code misturado com error_uri', 'code=opaque&state=opaque&error_uri=https%3A%2F%2Fexample.invalid'],
]) {
  test('rejeita callback OAuth2 invalido: ' + label, () => {
    const callbackUrl = 'http://localhost:4200/login/oauth2/code/keycloak?' + callbackQuery;
    assert.throws(
      () => assertRedirectPolicy({
        status: 302,
        currentUrl: KEYCLOAK_LOGIN_URL,
        nextUrl: callbackUrl,
        method: 'POST',
        hasBody: true,
        sensitivePostUrl: KEYCLOAK_LOGIN_URL,
      }),
      (error) => error.rule === 'RUNTIME_REDIRECT'
        && !sanitizeError(error).includes('opaque')
        && !sanitizeError(error).includes('other'),
    );
  });
}

test('aceita callback OAuth2 de sucesso com metadados legitimos adicionais', () => {
  assert.equal(assertRedirectPolicy({
    status: 302,
    currentUrl: KEYCLOAK_LOGIN_URL,
    nextUrl: CLIENT_CALLBACK_URL + '&iss=http%3A%2F%2Flocalhost%3A8081%2Frealms%2Fbetufu-local&session_state=opaque-session',
    method: 'POST',
    hasBody: true,
    sensitivePostUrl: KEYCLOAK_LOGIN_URL,
  }), true);
});

for (const failureUrl of [
  'http://localhost:4200/login',
  'http://localhost:4200/login?error=',
  'http://localhost:4200/login?error&extra=1',
  'http://localhost:4200/login?error&error',
]) {
  test('rejeita variante nao canonica do redirect de falha OAuth2', () => {
    assert.throws(
      () => assertRedirectPolicy({
        status: 302,
        currentUrl: CLIENT_CALLBACK_URL,
        nextUrl: failureUrl,
        method: 'GET',
        hasBody: false,
      }),
      (error) => error.rule === 'RUNTIME_REDIRECT',
    );
  });
}

for (const status of [307, 308]) {
  test('rejeita redirect ' + status + ' cross-origin que preservaria credenciais', () => {
    assert.throws(
      () => assertRedirectPolicy({
        status,
        currentUrl: KEYCLOAK_LOGIN_URL,
        nextUrl: CLIENT_CALLBACK_URL,
        method: 'POST',
        hasBody: true,
        sensitivePostUrl: KEYCLOAK_LOGIN_URL,
      }),
      (error) => error.rule === 'RUNTIME_REDIRECT_CREDENTIALS',
    );
  });
}

for (const status of [300, 304, 305, 306]) {
  test('rejeita status de redirect nao canonico ' + status, () => {
    assert.throws(
      () => assertRedirectPolicy({
        status,
        currentUrl: KEYCLOAK_LOGIN_URL,
        nextUrl: CLIENT_CALLBACK_URL,
        method: 'POST',
        hasBody: true,
        sensitivePostUrl: KEYCLOAK_LOGIN_URL,
      }),
      (error) => error.rule === 'RUNTIME_REDIRECT',
    );
  });
}

test('rejeita nome de token OIDC percent-encoded na URL browser-facing', () => {
  assert.throws(
    () => assertNoBrowserTokenExposure([
      {
        url: 'http://localhost:4200/callback?%2561ccess_token=opaque',
        body: '',
      },
    ], { names: () => [] }),
    (error) => error.rule === 'RUNTIME_BROWSER_EXPOSURE',
  );
});

test('rejeita token OIDC form-urlencoded no body browser-facing', () => {
  assert.throws(
    () => assertNoBrowserTokenExposure([
      {
        url: 'http://localhost:4200/callback',
        body: 'status=ok&access_token=opaque',
        headers: {},
      },
    ], { names: () => [] }),
    (error) => error.rule === 'RUNTIME_BROWSER_EXPOSURE',
  );
});

test('rejeita token OIDC em Set-Cookie mesmo sem permanecer no jar final', () => {
  assert.throws(
    () => assertNoBrowserTokenExposure([
      {
        url: 'http://localhost:4200/callback',
        body: '',
        headers: { 'set-cookie': ['access_token=opaque; Path=/'] },
      },
    ], { names: () => [] }),
    (error) => error.rule === 'RUNTIME_BROWSER_EXPOSURE',
  );
});

test('rejeita token OIDC escondido no valor de outro Set-Cookie', () => {
  assert.throws(
    () => assertNoBrowserTokenExposure([{
      url: 'http://localhost:4200/callback',
      body: '',
      headers: { 'set-cookie': ['debug=access_token%3Dopaque; Path=/'] },
    }], { names: () => [], cookies: [] }),
    (error) => error.rule === 'RUNTIME_BROWSER_EXPOSURE',
  );
});

test('rejeita token OIDC em input HTML e Authorization JSON', () => {
  for (const body of [
    '<input name="access_token" value="opaque">',
    '<input name=access_token value=opaque>',
    '<input name=authorization value="Bearer opaque">',
    '<input name="authoriz&#97;tion" value="Bearer opaque">',
    '<input name="access&#95;token" value="opaque">',
    '{"authorization":"Bearer opaque"}',
    'window.access_token = "opaque";',
    '%ZZ&%61ccess_token=opaque',
  ]) {
    assert.throws(
      () => assertNoBrowserTokenExposure([{
        url: 'http://localhost:4200/callback',
        body,
        headers: {},
      }], { names: () => [] }),
      (error) => error.rule === 'RUNTIME_BROWSER_EXPOSURE',
    );
  }
});

test('rejeita redirect com dot segment mesmo quando normaliza para callback aprovado', () => {
  for (const rawLocation of [
    '/unexpected/../login/oauth2/code/keycloak?code=opaque&state=opaque',
    '/unexpected/%2e%2e/login/oauth2/code/keycloak?code=opaque&state=opaque',
  ]) {
    assert.throws(
      () => assertRedirectPolicy({
        status: 302,
        currentUrl: KEYCLOAK_LOGIN_URL,
        nextUrl: new URL(rawLocation, CLIENT_CALLBACK_URL).href,
        rawLocation,
        method: 'GET',
        hasBody: false,
      }),
      (error) => error.rule === 'RUNTIME_REDIRECT',
    );
  }
});

test('reconhece processo encerrado por exit code ou signal code', () => {
  assert.equal(childHasExited({ exitCode: 0, signalCode: null }), true);
  assert.equal(childHasExited({ exitCode: null, signalCode: 'SIGTERM' }), true);
  assert.equal(childHasExited({ exitCode: null, signalCode: null }), false);
});

test('rejeita senha tecnica refletida em body, Location ou Set-Cookie', () => {
  const password = 'S3cr&<t';
  for (const response of [
    {
      url: 'http://localhost:4200/callback',
      body: 'falha=S3cr&amp;&lt;t',
      headers: {},
    },
    {
      url: 'http://localhost:4200/callback',
      body: '',
      headers: { location: '/erro?detail=' + encodeURIComponent(password) },
    },
    {
      url: 'http://localhost:4200/callback',
      body: '',
      headers: { 'set-cookie': ['flash=' + encodeURIComponent(password) + '; Path=/'] },
    },
  ]) {
    assert.throws(
      () => assertNoBrowserTokenExposure(
        [response],
        { names: () => [] },
        [password],
      ),
      (error) => error.rule === 'RUNTIME_BROWSER_EXPOSURE'
        && !error.message.includes(password),
    );
  }
});

test('requestLocal converte falha assíncrona do scanner em rejeição contratual', async () => {
  await assert.rejects(
    requestLocal('http://localhost:4200/login', {
      requestFactory: fakeHttpRequestFactory({
        headers: { 'set-cookie': ['access_token=opaque; Path=/'] },
      }),
    }),
    (error) => error.rule === 'RUNTIME_BROWSER_EXPOSURE',
  );
});

test('requestLocal converte falha assíncrona do cookie jar em rejeição contratual', async () => {
  await assert.rejects(
    requestLocal('http://localhost:4200/login', {
      jar: new CookieJar(),
      requestFactory: fakeHttpRequestFactory({
        headers: { 'set-cookie': ['session=value; Path=/; Path=/other'] },
      }),
    }),
    (error) => error.rule === 'RUNTIME_COOKIE',
  );
});

test('readiness não engole violação browser-facing nem repete o request', async () => {
  let attempts = 0;
  await assert.rejects(
    waitForBackendReady(
      { child: { exitCode: null }, spawnOutcome: Promise.resolve() },
      undefined,
      100,
      async () => {
        attempts += 1;
        throw new RuntimePrerequisiteContractError(
          'RUNTIME_BROWSER_EXPOSURE',
          'resposta proibida.',
        );
      },
    ),
    (error) => error.rule === 'RUNTIME_BROWSER_EXPOSURE',
  );
  assert.equal(attempts, 1);
});

test('cookie jar envia JSESSIONID Secure no callback HTTP localhost aprovado', () => {
  const jar = new CookieJar();
  jar.apply([
    'JSESSIONID=app-session; Path=/; SameSite=Lax; Secure; HttpOnly',
  ], 'http://localhost:4200/login');
  assert.match(jar.header('http://localhost:4200/callback'), /JSESSIONID=app-session/u);
  assert.equal(jar.header('http://localhost:8081/realms/betufu-local/'), '');
});

test('cookie jar envia AUTH_SESSION_ID Secure somente no escopo Keycloak localhost aprovado', () => {
  const jar = new CookieJar();
  jar.apply([
    'AUTH_SESSION_ID=opaque; Version=1; Path=/realms/betufu-local/; '
      + 'SameSite=None; Secure; HttpOnly',
  ], 'http://localhost:8081/realms/betufu-local/protocol/openid-connect/auth');

  assert.equal(jar.header(KEYCLOAK_LOGIN_URL), 'AUTH_SESSION_ID=opaque');
  assert.equal(jar.header('http://localhost:8081/admin/'), '');
  assert.equal(jar.header('http://localhost:4200/'), '');
  assert.equal(jar.header('http://127.0.0.1:8081/realms/betufu-local/'), '');
  assert.equal(jar.header('http://localhost:9999/realms/betufu-local/'), '');
});

test('cookie jar normaliza valor quoted de cookie geral do Keycloak', () => {
  const jar = new CookieJar();
  jar.apply([
    'AUTH_SESSION_ID="opaque.node"; Version=1; Path=/realms/betufu-local/; '
      + 'SameSite=None; Secure; HttpOnly',
  ], 'http://localhost:8081/realms/betufu-local/protocol/openid-connect/auth');

  assert.equal(jar.header(KEYCLOAK_LOGIN_URL), 'AUTH_SESSION_ID=opaque.node');
});

for (const [label, header] of [
  ['aspas sem fechamento', 'AUTH_SESSION_ID="opaque; Path=/'],
  ['aspas apenas no final', 'AUTH_SESSION_ID=opaque"; Path=/'],
  ['espaco interno', 'AUTH_SESSION_ID="opaque value"; Path=/'],
  ['barra invertida interna', 'AUTH_SESSION_ID="opaque\\value"; Path=/'],
]) {
  test('rejeita cookie geral quoted malformado: ' + label, () => {
    const jar = new CookieJar();
    assert.throws(
      () => jar.apply([header], KEYCLOAK_LOGIN_URL),
      (error) => error.rule === 'RUNTIME_COOKIE',
    );
  });
}

test('requestLocal transporta automaticamente o cookie Secure ao POST Keycloak aprovado', async () => {
  const jar = new CookieJar();
  let capturedRequest;
  jar.apply([
    'AUTH_SESSION_ID=opaque; Version=1; Path=/realms/betufu-local/; '
      + 'SameSite=None; Secure; HttpOnly',
  ], 'http://localhost:8081/realms/betufu-local/protocol/openid-connect/auth');

  await requestLocal(KEYCLOAK_LOGIN_URL, {
    method: 'POST',
    body: 'username=technical&password=redacted',
    jar,
    requestFactory: fakeHttpRequestFactory({
      onRequest: (requestOptions) => {
        capturedRequest = requestOptions;
      },
    }),
  });

  assert.equal(capturedRequest?.headers.Cookie, 'AUTH_SESSION_ID=opaque');
});

test('rejeita redirect para origem fora da allowlist sem incluir segredo no erro', () => {
  const canary = 'SENSITIVE_REDIRECT_CANARY';
  let failure;
  try {
    assertRedirectPolicy({
      status: 302,
      currentUrl: KEYCLOAK_LOGIN_URL,
      nextUrl: 'http://attacker.invalid/capture?password=' + canary,
      method: 'POST',
      hasBody: true,
      sensitivePostUrl: KEYCLOAK_LOGIN_URL,
    });
  } catch (error) {
    failure = error;
  }
  assert.equal(failure?.rule, 'RUNTIME_ORIGIN');
  assert.doesNotMatch(sanitizeError(failure), /SENSITIVE_REDIRECT_CANARY/u);
});

test('rejeita path de redirect nao aprovado mesmo em origem loopback aprovada', () => {
  assert.throws(
    () => assertRedirectPolicy({
      status: 302,
      currentUrl: KEYCLOAK_LOGIN_URL,
      nextUrl: 'http://localhost:4200/unexpected?code=opaque&state=opaque',
      method: 'POST',
      hasBody: true,
      sensitivePostUrl: KEYCLOAK_LOGIN_URL,
    }),
    (error) => error.rule === 'RUNTIME_REDIRECT',
  );
});

test('erro de cookie nao identifica o valor sensivel recebido', () => {
  const canary = 'SENSITIVE_COOKIE_CANARY';
  let failure;
  try {
    parseAndAssertXsrfSetCookie([
      'XSRF-TOKEN=' + canary + '; Path=/; SameSite=Lax; HttpOnly',
    ], 'http://localhost:4200/');
  } catch (error) {
    failure = error;
  }
  assert.equal(failure?.rule, 'RUNTIME_XSRF_COOKIE');
  assert.doesNotMatch(sanitizeError(failure), /SENSITIVE_COOKIE_CANARY/u);
});

test('proxy encaminha XSRF e substitui forwarded headers do cliente', () => {
  const headers = buildProxyRequestHeaders({
    accept: 'application/json',
    authorization: 'Bearer must-not-cross',
    cookie: 'XSRF-TOKEN=approved-xsrf-value',
    'x-forwarded-for': '198.51.100.10',
    'x-forwarded-host': 'attacker.invalid:9443',
    'x-forwarded-port': '9443',
    'x-forwarded-proto': 'https',
    'x-xsrf-token': 'approved-xsrf-value',
  });

  assert.equal(headers.cookie, 'XSRF-TOKEN=approved-xsrf-value');
  assert.equal(headers['x-xsrf-token'], 'approved-xsrf-value');
  assert.equal(headers.authorization, undefined);
  assert.equal(headers.host, 'localhost:4200');
  assert.equal(headers['x-forwarded-host'], 'localhost:4200');
  assert.equal(headers['x-forwarded-port'], '4200');
  assert.equal(headers['x-forwarded-for'], '127.0.0.1');
  assert.equal(headers['x-forwarded-proto'], 'http');
});

test('proxy sempre reconstrói a porta pública aprovada', () => {
  const headers = buildProxyRequestHeaders({
    host: 'localhost',
    'x-forwarded-host': 'localhost',
    'x-forwarded-port': '80',
  });

  assert.equal(headers.host, 'localhost:4200');
  assert.equal(headers['x-forwarded-host'], 'localhost:4200');
  assert.equal(headers['x-forwarded-port'], '4200');
});

function fakeProxyExchange() {
  const incoming = new EventEmitter();
  incoming.headers = { host: 'localhost:4200' };
  incoming.method = 'GET';
  incoming.url = '/api/v1/tracer/identity/current';
  incoming.unpipe = () => {};
  incoming.pipe = () => {};

  const outgoing = new EventEmitter();
  outgoing.destroyed = false;
  outgoing.headersSent = false;
  outgoing.writableEnded = false;
  outgoing.writeHead = (statusCode) => {
    outgoing.statusCode = statusCode;
    outgoing.headersSent = true;
  };
  outgoing.end = () => {
    outgoing.writableEnded = true;
  };
  outgoing.destroy = () => {
    outgoing.destroyed = true;
  };

  const upstream = new EventEmitter();
  upstream.destroyed = false;
  upstream.setTimeout = () => {};
  upstream.destroy = () => {
    upstream.destroyed = true;
  };

  handleProxyRequest(incoming, outgoing, {
    requestFactory: () => upstream,
    upstreamRequests: new Set(),
  });
  return { incoming, outgoing, upstream };
}

test('proxy trata erro do request de entrada sem excecao nao capturada', () => {
  const { incoming, outgoing, upstream } = fakeProxyExchange();
  assert.doesNotThrow(() => incoming.emit('error', new Error('client reset')));
  assert.equal(upstream.destroyed, true);
  assert.equal(outgoing.statusCode, 502);
  assert.equal(outgoing.writableEnded, true);
});

test('proxy trata erro da resposta de saida sem escrever novamente', () => {
  const { outgoing, upstream } = fakeProxyExchange();
  assert.doesNotThrow(() => outgoing.emit('error', new Error('client reset')));
  assert.equal(upstream.destroyed, true);
  assert.equal(outgoing.headersSent, false);
});

test('proxy trata erro do stream upstream depois dos headers', () => {
  const { outgoing, upstream } = fakeProxyExchange();
  const response = new EventEmitter();
  response.statusCode = 200;
  response.headers = {};
  response.destroyed = false;
  response.destroy = () => {
    response.destroyed = true;
  };
  response.unpipe = () => {};
  response.pipe = () => {};
  upstream.emit('response', response);

  assert.doesNotThrow(() => response.emit('error', new Error('upstream reset')));
  assert.equal(upstream.destroyed, true);
  assert.equal(response.destroyed, true);
  assert.equal(outgoing.destroyed, true);
});

test('rejeita drift de UUID no runtime', () => {
  const evidence = validEvidence();
  evidence.identityIds[1] = '223e4567-e89b-42d3-a456-426614174000';
  assert.throws(
    () => assertRuntimeEvidence(evidence),
    (error) => error.rule === 'RUNTIME_IDENTITY',
  );
});

for (const [label, mutateEvidence, expectedRule] of [
  [
    'hash JAR igual entre inicio e fim mas fora da ancora',
    (evidence) => {
      evidence.jarHashBefore = '0'.repeat(64);
      evidence.jarHashAfter = '0'.repeat(64);
    },
    'RUNTIME_JAR_DRIFT',
  ],
  [
    'tamanho JAR igual entre inicio e fim mas fora da ancora',
    (evidence) => {
      evidence.jarSizeBefore -= 1;
      evidence.jarSizeAfter -= 1;
    },
    'RUNTIME_JAR_DRIFT',
  ],
  [
    'sentinel Redis nao ocupou a porta',
    (evidence) => {
      evidence.redis.sentinelOccupied = false;
    },
    'RUNTIME_REDIS',
  ],
  [
    'backend tentou conectar ao sentinel Redis',
    (evidence) => {
      evidence.redis.connectionAttempts = 1;
    },
    'RUNTIME_REDIS',
  ],
  [
    'cleanup do sentinel Redis nao terminou',
    (evidence) => {
      evidence.redis.cleanupComplete = false;
    },
    'RUNTIME_REDIS',
  ],
  [
    'Java nao e exatamente 25',
    (evidence) => {
      evidence.java.specificationVersion = '25.0.1';
    },
    'RUNTIME_JAVA',
  ],
  [
    'vendor Java ausente',
    (evidence) => {
      evidence.java.vendor = '';
    },
    'RUNTIME_JAVA',
  ],
  [
    'PID de backend ausente',
    (evidence) => {
      delete evidence.ownedProcesses.backend.pid;
    },
    'RUNTIME_METADATA',
  ],
  [
    'nonce de proxy invalido',
    (evidence) => {
      evidence.ownedProcesses.proxy.nonce = 'not-a-uuid';
    },
    'RUNTIME_METADATA',
  ],
  [
    'backend e proxy reutilizaram o mesmo nonce',
    (evidence) => {
      evidence.ownedProcesses.proxy.nonce = evidence.ownedProcesses.backend.nonce;
    },
    'RUNTIME_METADATA',
  ],
  [
    'instante de inicio invalido',
    (evidence) => {
      evidence.ownedProcesses.backend.startedAt = 'not-an-instant';
    },
    'RUNTIME_METADATA',
  ],
  [
    'porta do backend divergente',
    (evidence) => {
      evidence.ownedProcesses.backend.port = 9999;
    },
    'RUNTIME_METADATA',
  ],
  [
    'metadata da sentinela Redis ausente',
    (evidence) => {
      delete evidence.ownedProcesses.redisSentinel;
    },
    'RUNTIME_METADATA',
  ],
  [
    'cookie HTTP deixou de ser host-only',
    (evidence) => {
      evidence.csrf.httpCookie.hostOnly = false;
    },
    'RUNTIME_COOKIE',
  ],
  [
    'jar deixou de ser particionado por origem',
    (evidence) => {
      evidence.browserExposure.cookieJarOriginPartitioned = false;
    },
    'RUNTIME_BROWSER_EXPOSURE',
  ],
  [
    'forwarded headers do cliente nao foram substituidos',
    (evidence) => {
      evidence.proxyEvidence.untrustedForwardedHeadersOverridden = false;
    },
    'RUNTIME_PROXY',
  ],
  [
    'HTTPS confiavel nao produziu cookie Secure',
    (evidence) => {
      evidence.proxyEvidence.trustedHttpsProducedSecureCookie = false;
    },
    'RUNTIME_PROXY',
  ],
  [
    'cookie HTTP marcou Secure',
    (evidence) => {
      evidence.csrf.httpCookie.secure = true;
    },
    'RUNTIME_COOKIE',
  ],
  [
    'cookie HTTPS omitiu Secure',
    (evidence) => {
      evidence.csrf.httpsCookie.secure = false;
    },
    'RUNTIME_COOKIE',
  ],
]) {
  test('rejeita evidencia runtime incompleta: ' + label, () => {
    const evidence = validEvidence();
    mutateEvidence(evidence);
    assert.throws(
      () => assertRuntimeEvidence(evidence),
      (error) => error.rule === expectedRule,
    );
  });
}

test('orquestrador runtime limpa proxy e backend na ordem inversa', async () => {
  const { adapter, calls } = fakeAdapter();
  const evidence = await runRuntimeProof(projectRoot, adapter);
  assert.equal(evidence.jarHashAfter, VALID_HASH);
  assert.deepEqual(calls, [
    'prepare',
    'startRedisSentinel',
    'preflight',
    'startBackend',
    'waitForBackend',
    'startProxy',
    'exercise',
    'finish',
    'stopProxy',
    'stopBackend',
    'stopRedisSentinel',
  ]);
});

test('orquestrador limpa recursos quando o scanner HTTP assíncrono reprova', async () => {
  const { adapter, calls } = fakeAdapter({
    async exercise() {
      calls.push('exercise');
      await requestLocal('http://localhost:4200/login', {
        requestFactory: fakeHttpRequestFactory({
          headers: { 'set-cookie': ['access_token=opaque; Path=/'] },
        }),
      });
      return validEvidence();
    },
  });

  await assert.rejects(
    withTestDeadline(runRuntimeProof(projectRoot, adapter)),
    (error) => error.rule === 'RUNTIME_BROWSER_EXPOSURE',
  );
  assert.deepEqual(calls.slice(-4), [
    'finish',
    'stopProxy',
    'stopBackend',
    'stopRedisSentinel',
  ]);
});

test('orquestrador ocupa e limpa sentinel Redis sem conexoes', async () => {
  const { adapter, calls } = fakeAdapter({
    async exercise() {
      calls.push('exercise');
      const evidence = validEvidence();
      delete evidence.redis;
      delete evidence.jarHashAfter;
      return evidence;
    },
  });
  attachFakeRedisSentinel(adapter, calls);

  const evidence = await withTestDeadline(runRuntimeProof(projectRoot, adapter));
  assert.deepEqual(evidence.redis, {
    connectionObserved: false,
    sentinelOccupied: true,
    connectionAttempts: 0,
    cleanupComplete: true,
  });
  assert.equal(
    calls.indexOf('startRedisSentinel') < calls.indexOf('startBackend'),
    true,
  );
  assert.equal(calls.includes('finish'), true);
  assert.deepEqual(calls.slice(-3), [
    'stopProxy',
    'stopBackend',
    'stopRedisSentinel',
  ]);
});

test('falha ao adquirir sentinel Redis interrompe antes de preflight ou Java', async () => {
  const { adapter, calls } = fakeAdapter({
    async startRedisSentinel() {
      calls.push('startRedisSentinel');
      throw new RuntimePrerequisiteContractError(
        'RUNTIME_REDIS_SENTINEL',
        'porta indisponivel.',
      );
    },
  });

  await assert.rejects(
    runRuntimeProof(projectRoot, adapter),
    (error) => error.rule === 'RUNTIME_REDIS_SENTINEL',
  );
  assert.deepEqual(calls, ['prepare', 'startRedisSentinel', 'finish']);
});

test('conexao ao sentinel Redis falha a prova e ainda limpa todos os recursos', async () => {
  const { adapter, calls } = fakeAdapter({
    async exercise() {
      calls.push('exercise');
      const evidence = validEvidence();
      delete evidence.redis;
      delete evidence.jarHashAfter;
      return evidence;
    },
  });
  attachFakeRedisSentinel(adapter, calls, { connectionObserved: true });

  await assert.rejects(
    withTestDeadline(runRuntimeProof(projectRoot, adapter)),
    (error) => error.rule === 'RUNTIME_REDIS',
  );
  assert.deepEqual(calls.slice(-3), [
    'stopProxy',
    'stopBackend',
    'stopRedisSentinel',
  ]);
});

test('falha de cleanup do sentinel Redis prevalece sem expor detalhe interno', async () => {
  const { adapter, calls } = fakeAdapter();
  attachFakeRedisSentinel(adapter, calls, {
    stopError: new Error('SENSITIVE_REDIS_CLEANUP_CANARY'),
  });

  await assert.rejects(
    withTestDeadline(runRuntimeProof(projectRoot, adapter)),
    (error) => error.rule === 'RUNTIME_CLEANUP'
      && !error.message.includes('SENSITIVE_REDIS_CLEANUP_CANARY'),
  );
  assert.deepEqual(calls.slice(-3), [
    'stopProxy',
    'stopBackend',
    'stopRedisSentinel',
  ]);
});

test('timeout de etapa aborta a prova e limpa proxy e backend', async () => {
  const { adapter, calls } = fakeAdapter({
    async exercise() {
      calls.push('exercise');
      return new Promise(() => {});
    },
  });

  await assert.rejects(
    withTestDeadline(runRuntimeProof(projectRoot, adapter, { timeoutMilliseconds: 20 })),
    (error) => error.rule === 'RUNTIME_TIMEOUT',
  );
  assert.deepEqual(calls.slice(-3), [
    'stopProxy',
    'stopBackend',
    'stopRedisSentinel',
  ]);
});

test('timeout da verificacao final do JAR nao impede cleanup', async () => {
  let finishAborted = false;
  const { adapter, calls } = fakeAdapter({
    async finish(_prepared, _backend, _proxy, _sentinel, signal) {
      calls.push('finish');
      return new Promise((resolvePromise, rejectPromise) => {
        void resolvePromise;
        signal.addEventListener('abort', () => {
          finishAborted = true;
          rejectPromise(signal.reason);
        }, { once: true });
      });
    },
  });

  await assert.rejects(
    withTestDeadline(runRuntimeProof(projectRoot, adapter, {
      timeoutMilliseconds: 1_000,
      stepTimeoutMilliseconds: 20,
    })),
    (error) => error.rule === 'RUNTIME_CLEANUP'
      && error.cause?.rule === 'RUNTIME_TIMEOUT',
  );
  assert.deepEqual(calls.slice(-3), [
    'stopProxy',
    'stopBackend',
    'stopRedisSentinel',
  ]);
  assert.equal(finishAborted, true);
});

test('SIGTERM durante timeout final preserva o sinal e ainda limpa recursos', async () => {
  const signalEmitter = new EventEmitter();
  let finishAborted = false;
  const { adapter, calls } = fakeAdapter({
    async finish(_prepared, _backend, _proxy, _sentinel, signal) {
      calls.push('finish');
      return new Promise((resolvePromise, rejectPromise) => {
        void resolvePromise;
        const onAbort = () => {
          finishAborted = true;
          rejectPromise(signal.reason);
        };
        if (signal.aborted) {
          onAbort();
        } else {
          signal.addEventListener('abort', onAbort, { once: true });
        }
      });
    },
  });
  const signalTimer = setTimeout(() => signalEmitter.emit('SIGTERM'), 10);
  try {
    await assert.rejects(
      withTestDeadline(runRuntimeProof(projectRoot, adapter, {
        signalEmitter,
        timeoutMilliseconds: 1_000,
        stepTimeoutMilliseconds: 25,
      })),
      (error) => error.rule === 'RUNTIME_CLEANUP'
        && error.cause?.rule === 'RUNTIME_SIGNAL'
        && error.cause.signalName === 'SIGTERM',
    );
  } finally {
    clearTimeout(signalTimer);
  }
  assert.deepEqual(calls.slice(-3), [
    'stopProxy',
    'stopBackend',
    'stopRedisSentinel',
  ]);
  assert.equal(finishAborted, true);
});

test('SIGTERM interrompe a prova e limpa recursos proprios', async () => {
  const signalEmitter = new EventEmitter();
  const { adapter, calls } = fakeAdapter({
    async exercise() {
      calls.push('exercise');
      return new Promise(() => {});
    },
  });
  const signalTimer = setTimeout(() => signalEmitter.emit('SIGTERM'), 10);
  try {
    await assert.rejects(
      withTestDeadline(runRuntimeProof(projectRoot, adapter, {
        signalEmitter,
        timeoutMilliseconds: 1_000,
        stepTimeoutMilliseconds: 1_000,
      })),
      (error) => error.rule === 'RUNTIME_SIGNAL',
    );
  } finally {
    clearTimeout(signalTimer);
  }
  assert.equal(signalEmitter.listenerCount('SIGINT'), 0);
  assert.equal(signalEmitter.listenerCount('SIGTERM'), 0);
  assert.deepEqual(calls.slice(-3), [
    'stopProxy',
    'stopBackend',
    'stopRedisSentinel',
  ]);
});

test('AbortSignal externo ja abortado impede qualquer etapa material', async () => {
  const controller = new AbortController();
  controller.abort();
  const { adapter, calls } = fakeAdapter();

  await assert.rejects(
    runRuntimeProof(projectRoot, adapter, { signal: controller.signal }),
    (error) => error.rule === 'RUNTIME_ABORT',
  );
  assert.deepEqual(calls, []);
});

test('AbortSignal externo durante exercise preserva cleanup', async () => {
  const controller = new AbortController();
  const { adapter, calls } = fakeAdapter({
    async exercise() {
      calls.push('exercise');
      queueMicrotask(() => controller.abort());
      return new Promise(() => {});
    },
  });

  await assert.rejects(
    withTestDeadline(runRuntimeProof(projectRoot, adapter, {
      signal: controller.signal,
      timeoutMilliseconds: 1_000,
      stepTimeoutMilliseconds: 1_000,
    })),
    (error) => error.rule === 'RUNTIME_ABORT',
  );
  assert.deepEqual(calls.slice(-3), [
    'stopProxy',
    'stopBackend',
    'stopRedisSentinel',
  ]);
});

test('AbortSignal externo durante finish aborta a leitura e preserva cleanup', async () => {
  const controller = new AbortController();
  let finishAborted = false;
  const { adapter, calls } = fakeAdapter({
    async finish(_prepared, _backend, _proxy, _sentinel, signal) {
      calls.push('finish');
      queueMicrotask(() => controller.abort());
      return new Promise((resolvePromise, rejectPromise) => {
        void resolvePromise;
        const onAbort = () => {
          finishAborted = true;
          rejectPromise(signal.reason);
        };
        if (signal.aborted) {
          onAbort();
        } else {
          signal.addEventListener('abort', onAbort, { once: true });
        }
      });
    },
  });

  await assert.rejects(
    withTestDeadline(runRuntimeProof(projectRoot, adapter, {
      signal: controller.signal,
      timeoutMilliseconds: 1_000,
      stepTimeoutMilliseconds: 1_000,
    })),
    (error) => error.rule === 'RUNTIME_CLEANUP'
      && error.cause?.rule === 'RUNTIME_ABORT',
  );
  assert.equal(finishAborted, true);
  assert.deepEqual(calls.slice(-3), [
    'stopProxy',
    'stopBackend',
    'stopRedisSentinel',
  ]);
});

test('orquestrador limpa recursos quando a prova falha e sanitiza o erro', async () => {
  const { adapter, calls } = fakeAdapter({
    async exercise() {
      calls.push('exercise');
      throw new Error('SENSITIVE_CANARY_VALUE');
    },
  });
  let failure;
  try {
    await runRuntimeProof(projectRoot, adapter);
  } catch (error) {
    failure = error;
  }
  assert.deepEqual(calls.slice(-3), [
    'stopProxy',
    'stopBackend',
    'stopRedisSentinel',
  ]);
  assert.equal(
    sanitizeError(failure),
    'INTERNAL_ERROR: falha interna durante a validacao.',
  );
});

test('orquestrador limpa somente backend quando readiness falha antes do proxy', async () => {
  const { adapter, calls } = fakeAdapter({
    async waitForBackend() {
      calls.push('waitForBackend');
      throw new RuntimePrerequisiteContractError(
        'RUNTIME_BACKEND',
        'readiness falhou.',
      );
    },
  });

  await assert.rejects(
    runRuntimeProof(projectRoot, adapter),
    (error) => error.rule === 'RUNTIME_BACKEND',
  );
  assert.deepEqual(calls, [
    'prepare',
    'startRedisSentinel',
    'preflight',
    'startBackend',
    'waitForBackend',
    'finish',
    'stopBackend',
    'stopRedisSentinel',
  ]);
});

test('orquestrador limpa backend quando criacao do proxy falha', async () => {
  const { adapter, calls } = fakeAdapter({
    async startProxy() {
      calls.push('startProxy');
      throw new RuntimePrerequisiteContractError(
        'RUNTIME_PROXY',
        'proxy falhou.',
      );
    },
  });

  await assert.rejects(
    runRuntimeProof(projectRoot, adapter),
    (error) => error.rule === 'RUNTIME_PROXY',
  );
  assert.deepEqual(calls, [
    'prepare',
    'startRedisSentinel',
    'preflight',
    'startBackend',
    'waitForBackend',
    'startProxy',
    'finish',
    'stopBackend',
    'stopRedisSentinel',
  ]);
});

test('falha de cleanup prevalece e nao identifica segredo', async () => {
  const { adapter, calls } = fakeAdapter({
    async stopProxy() {
      calls.push('stopProxy');
      throw new Error('SENSITIVE_CANARY_VALUE');
    },
  });
  await assert.rejects(
    runRuntimeProof(projectRoot, adapter),
    (error) => error.rule === 'RUNTIME_CLEANUP'
      && !error.message.includes('SENSITIVE_CANARY_VALUE'),
  );
  assert.deepEqual(calls.slice(-3), [
    'stopProxy',
    'stopBackend',
    'stopRedisSentinel',
  ]);
});
