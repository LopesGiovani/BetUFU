#!/usr/bin/env node

import { isDeepStrictEqual } from 'node:util';
import { pathToFileURL } from 'node:url';

export const REQUIRED_EVENT_LISTENERS = Object.freeze([
  'jboss-logging',
  'betufu-password-event-listener',
]);

export const DEFAULT_TIMEOUT_MS = 10_000;

export class KeycloakEventListenerReconciliationError extends Error {
  constructor(code, message) {
    super(`${code}: ${message}`);
    this.name = 'KeycloakEventListenerReconciliationError';
    this.code = code;
  }
}

function fail(code, message) {
  throw new KeycloakEventListenerReconciliationError(code, message);
}

function expect(condition, code, message) {
  if (!condition) fail(code, message);
}

function cloneJson(value) {
  return JSON.parse(JSON.stringify(value));
}

function isLiteralLoopback(hostname) {
  return /^127(?:\.\d{1,3}){3}$/u.test(hostname) || hostname === '[::1]';
}

function safeErrorCode(error) {
  return error instanceof KeycloakEventListenerReconciliationError
    ? error.code
    : 'UNEXPECTED_ERROR';
}

function parseValue(argv, index, name) {
  expect(index + 1 < argv.length, 'ARGUMENT_VALUE_MISSING', `${name} exige um valor.`);
  return argv[index + 1];
}

export function parseArguments(argv) {
  const parsed = {
    apply: false,
    baseUrl: undefined,
    realm: undefined,
    timeoutMs: DEFAULT_TIMEOUT_MS,
  };
  const seen = new Set();

  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === '--apply') {
      expect(!seen.has(argument), 'ARGUMENT_DUPLICATE', '--apply foi informado mais de uma vez.');
      seen.add(argument);
      parsed.apply = true;
      continue;
    }

    if (argument === '--base-url' || argument === '--realm' || argument === '--timeout-ms') {
      expect(!seen.has(argument), 'ARGUMENT_DUPLICATE', `${argument} foi informado mais de uma vez.`);
      seen.add(argument);
      const value = parseValue(argv, index, argument);
      index += 1;
      if (argument === '--base-url') parsed.baseUrl = value;
      if (argument === '--realm') parsed.realm = value;
      if (argument === '--timeout-ms') parsed.timeoutMs = Number(value);
      continue;
    }

    // Nunca repetimos um argumento desconhecido: ele pode conter uma credencial colada por engano.
    fail('ARGUMENT_UNKNOWN', 'foi informado um argumento desconhecido.');
  }

  expect(typeof parsed.baseUrl === 'string' && parsed.baseUrl.length > 0, 'BASE_URL_REQUIRED', '--base-url e obrigatorio.');
  expect(typeof parsed.realm === 'string' && parsed.realm.length > 0, 'REALM_REQUIRED', '--realm e obrigatorio.');
  expect(
    Number.isInteger(parsed.timeoutMs) && parsed.timeoutMs >= 100 && parsed.timeoutMs <= 60_000,
    'TIMEOUT_INVALID',
    '--timeout-ms deve ser um inteiro entre 100 e 60000.',
  );

  let parsedUrl;
  try {
    parsedUrl = new URL(parsed.baseUrl);
  } catch {
    fail('BASE_URL_INVALID', '--base-url deve ser uma URL HTTP ou HTTPS valida.');
  }
  expect(['http:', 'https:'].includes(parsedUrl.protocol), 'BASE_URL_PROTOCOL', '--base-url deve usar HTTP ou HTTPS.');
  expect(
    parsedUrl.protocol === 'https:' || isLiteralLoopback(parsedUrl.hostname),
    'BASE_URL_TLS_REQUIRED',
    '--base-url exige HTTPS fora de um endereco loopback literal.',
  );
  expect(parsedUrl.username === '' && parsedUrl.password === '', 'BASE_URL_USERINFO', '--base-url nao pode conter credenciais.');
  expect(parsedUrl.search === '' && parsedUrl.hash === '', 'BASE_URL_COMPONENTS', '--base-url nao pode conter query ou fragmento.');
  expect(/^[A-Za-z0-9._-]+$/u.test(parsed.realm), 'REALM_INVALID', '--realm contem caracteres nao permitidos.');

  parsedUrl.pathname = parsedUrl.pathname.replace(/\/+$/u, '');
  parsed.baseUrl = parsedUrl.toString().replace(/\/$/u, '');
  return parsed;
}

async function request({ fetchImpl, url, init, timeoutMs, operation, expectJson }) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  let response;
  try {
    response = await fetchImpl(url, { ...init, redirect: 'error', signal: controller.signal });
  } catch (error) {
    if (controller.signal.aborted || error?.name === 'AbortError') {
      fail('HTTP_TIMEOUT', `${operation} excedeu o timeout configurado.`);
    }
    fail('HTTP_TRANSPORT', `${operation} falhou antes de receber resposta HTTP.`);
  } finally {
    clearTimeout(timeout);
  }

  expect(response && typeof response.ok === 'boolean', 'HTTP_RESPONSE_INVALID', `${operation} devolveu resposta HTTP invalida.`);
  if (!response.ok) {
    fail('HTTP_STATUS', `${operation} foi recusada com HTTP ${response.status}.`);
  }

  if (!expectJson) return undefined;
  try {
    return await response.json();
  } catch {
    fail('HTTP_JSON_INVALID', `${operation} devolveu JSON invalido.`);
  }
}

async function obtainAdminToken({ fetchImpl, baseUrl, timeoutMs, username, password }) {
  const body = new URLSearchParams({
    grant_type: 'password',
    client_id: 'admin-cli',
    username,
    password,
  });
  const tokenDocument = await request({
    fetchImpl,
    url: `${baseUrl}/realms/master/protocol/openid-connect/token`,
    init: {
      method: 'POST',
      headers: { 'content-type': 'application/x-www-form-urlencoded' },
      body,
    },
    timeoutMs,
    operation: 'autenticacao administrativa',
    expectJson: true,
  });
  expect(
    tokenDocument && typeof tokenDocument.access_token === 'string' && tokenDocument.access_token.length > 0,
    'TOKEN_MISSING',
    'a resposta de autenticacao nao contem access_token valido.',
  );
  return tokenDocument.access_token;
}

function eventsConfigUrl(baseUrl, realm) {
  return `${baseUrl}/admin/realms/${encodeURIComponent(realm)}/events/config`;
}

async function readEventsConfig(context) {
  const document = await request({
    fetchImpl: context.fetchImpl,
    url: eventsConfigUrl(context.baseUrl, context.realm),
    init: {
      method: 'GET',
      headers: { authorization: `Bearer ${context.token}` },
    },
    timeoutMs: context.timeoutMs,
    operation: 'leitura da configuracao de eventos',
    expectJson: true,
  });
  expect(document !== null && typeof document === 'object' && !Array.isArray(document), 'EVENTS_CONFIG_INVALID', 'a configuracao de eventos deve ser um objeto JSON.');
  expect(
    document.eventsListeners === undefined || (Array.isArray(document.eventsListeners) && document.eventsListeners.every((entry) => typeof entry === 'string')),
    'EVENT_LISTENERS_INVALID',
    'eventsListeners deve ser uma lista de strings.',
  );
  return cloneJson(document);
}

async function writeEventsConfig(context, document, operation = 'escrita da configuracao de eventos') {
  await request({
    fetchImpl: context.fetchImpl,
    url: eventsConfigUrl(context.baseUrl, context.realm),
    init: {
      method: 'PUT',
      headers: {
        authorization: `Bearer ${context.token}`,
        'content-type': 'application/json',
      },
      body: JSON.stringify(document),
    },
    timeoutMs: context.timeoutMs,
    operation,
    expectJson: false,
  });
}

export function withRequiredListeners(preimage) {
  const listeners = [];
  for (const listener of [...(preimage.eventsListeners ?? []), ...REQUIRED_EVENT_LISTENERS]) {
    if (!listeners.includes(listener)) listeners.push(listener);
  }
  return { ...cloneJson(preimage), eventsListeners: listeners };
}

export function equivalentEventsConfig(expected, observed) {
  if (expected === null || observed === null
      || typeof expected !== 'object' || typeof observed !== 'object'
      || Array.isArray(expected) || Array.isArray(observed)) {
    return false;
  }
  const expectedWithoutListeners = cloneJson(expected);
  const observedWithoutListeners = cloneJson(observed);
  const expectedListeners = [...(expectedWithoutListeners.eventsListeners ?? [])];
  const observedListeners = [...(observedWithoutListeners.eventsListeners ?? [])];
  delete expectedWithoutListeners.eventsListeners;
  delete observedWithoutListeners.eventsListeners;
  return isDeepStrictEqual(expectedWithoutListeners, observedWithoutListeners)
    && expectedListeners.length === observedListeners.length
    && expectedListeners.every((listener) => observedListeners.includes(listener));
}

async function rollbackPreimage(context, preimage) {
  try {
    await writeEventsConfig(context, preimage, 'rollback da configuracao de eventos');
    const restored = await readEventsConfig(context);
    return equivalentEventsConfig(preimage, restored) ? 'confirmed' : 'diverged';
  } catch {
    return 'failed';
  }
}

export async function reconcileKeycloakEventListeners({
  argv = [],
  env = process.env,
  fetchImpl = globalThis.fetch,
  log = console.log,
} = {}) {
  const options = parseArguments(argv);
  expect(typeof fetchImpl === 'function', 'FETCH_UNAVAILABLE', 'a implementacao HTTP nao esta disponivel.');

  const username = env.KC_BOOTSTRAP_ADMIN_USERNAME;
  const password = env.KC_BOOTSTRAP_ADMIN_PASSWORD;
  expect(typeof username === 'string' && username.length > 0, 'ADMIN_USERNAME_REQUIRED', 'KC_BOOTSTRAP_ADMIN_USERNAME deve existir no ambiente.');
  expect(typeof password === 'string' && password.length > 0, 'ADMIN_PASSWORD_REQUIRED', 'KC_BOOTSTRAP_ADMIN_PASSWORD deve existir no ambiente.');

  const token = await obtainAdminToken({
    fetchImpl,
    baseUrl: options.baseUrl,
    timeoutMs: options.timeoutMs,
    username,
    password,
  });
  const context = { ...options, fetchImpl, token };
  const preimage = await readEventsConfig(context);
  const expected = withRequiredListeners(preimage);

  if (isDeepStrictEqual(preimage, expected)) {
    log(`[reconcile-keycloak-event-listeners] UNCHANGED realm=${options.realm}`);
    return { status: 'UNCHANGED', changed: false };
  }

  if (!options.apply) {
    log(`[reconcile-keycloak-event-listeners] DRY_RUN realm=${options.realm} action=ADD_REQUIRED_LISTENERS`);
    return { status: 'DRY_RUN', changed: true };
  }

  await writeEventsConfig(context, expected);
  try {
    const observed = await readEventsConfig(context);
    expect(
      equivalentEventsConfig(expected, observed),
      'POST_WRITE_DIVERGENCE',
      'a confirmacao divergiu do documento esperado.',
    );
  } catch (verificationFailure) {
    const rollback = await rollbackPreimage(context, preimage);
    fail(
      'POST_WRITE_VERIFICATION_FAILED',
      `a confirmacao pos-escrita falhou; primary=${safeErrorCode(verificationFailure)}; rollback=${rollback}.`,
    );
  }

  log(`[reconcile-keycloak-event-listeners] APPLIED realm=${options.realm}`);
  return { status: 'APPLIED', changed: true };
}

export async function main({
  argv = process.argv.slice(2),
  env = process.env,
  fetchImpl = globalThis.fetch,
  log = console.log,
  errorLog = console.error,
} = {}) {
  try {
    await reconcileKeycloakEventListeners({ argv, env, fetchImpl, log });
    return 0;
  } catch (error) {
    const message = error instanceof KeycloakEventListenerReconciliationError
      ? error.message
      : 'UNEXPECTED_ERROR: a reconciliacao falhou de forma inesperada.';
    errorLog(`[reconcile-keycloak-event-listeners] ERROR ${message}`);
    return 1;
  }
}

const invokedAsScript = process.argv[1]
  && import.meta.url === pathToFileURL(process.argv[1]).href;

if (invokedAsScript) {
  process.exitCode = await main();
}
