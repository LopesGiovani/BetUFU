#!/usr/bin/env node

import { spawn } from 'node:child_process';
import { createHash, randomUUID } from 'node:crypto';
import { createReadStream } from 'node:fs';
import { lstat, readFile, readdir, realpath } from 'node:fs/promises';
import { createServer, request as httpRequest } from 'node:http';
import { connect, createServer as createNetServer } from 'node:net';
import { dirname, isAbsolute, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
const defaultProjectRoot = resolve(scriptsDirectory, '..');

const PATHS = Object.freeze({
  application: 'backend/src/main/resources/application.yaml',
  development: 'backend/src/main/resources/application-dev.yaml',
  homolog: 'backend/src/main/resources/application-homolog.yaml',
  production: 'backend/src/main/resources/application-prod.yaml',
  resources: 'backend/src/main/resources',
  backend: 'backend',
  backendConfig: 'backend/config',
  environment: 'infra/.env.example',
  keycloakRealm: 'infra/keycloak/realm-betufu.json',
  pom: 'backend/pom.xml',
  security: 'backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java',
  csrfTest: 'backend/src/test/java/br/com/betufu/platform/security/CsrfSpaIT.java',
  angularConfig: 'frontend/src/app/app.config.ts',
  backendSources: 'backend/src/main/java',
  frontendSources: 'frontend/src',
  backendTarget: 'backend/target',
});

const REDIS_SESSION_AUTO_CONFIGURATION =
  'org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration';
const LOCAL_JDBC_URL = 'jdbc:postgresql://127.0.0.1:5432/betufu_local';
const LOCAL_REDIS_HOST = '127.0.0.1';
const LOCAL_REDIS_PORT = '6379';
const LOCAL_REDIS_PASSWORD = 'betufu-local-redis';
const XSRF_COOKIE = 'XSRF-TOKEN';
const XSRF_HEADER = 'X-XSRF-TOKEN';
const CLIENT_ORIGIN = 'http://localhost:4200';
const BACKEND_ORIGIN = 'http://127.0.0.1:8080';
const KEYCLOAK_ORIGIN = 'http://localhost:8081';
export const APPROVED_JAR_SHA256 =
  '15D7514BF98EE415D0FC83E6008AE0838ACBD01EE17CB53B09B3ABF33A5A0878';
export const APPROVED_JAR_SIZE = 78_665_537;
const MAX_TEXT_BYTES = 2 * 1024 * 1024;
const MAX_HTTP_BYTES = 2 * 1024 * 1024;
const MAX_HTTP_URL_BYTES = 4 * 1024;
const MAX_HTTP_REQUEST_BYTES = 128 * 1024;
const MAX_COOKIE_HEADER_BYTES = 16 * 1024;
const MAX_COOKIE_COUNT = 64;
const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/u;
const DEFAULT_RUNTIME_DEADLINE_MILLISECONDS = 180_000;
const DEFAULT_STEP_TIMEOUT_MILLISECONDS = 30_000;
const PROCESS_TERMINATION_GRACE_MILLISECONDS = 8_000;
const PROCESS_KILL_GRACE_MILLISECONDS = 4_000;
const REDIS_SENTINEL_HOST = '127.0.0.1';
const REDIS_SENTINEL_PORT = 6379;
const XSRF_TOMBSTONE_EXPIRES = 'Thu, 01 Jan 1970 00:00:10 GMT';

const REQUIRED_RUNTIME_ENV = Object.freeze([
  'BETUFU_DB_JDBC_URL',
  'BETUFU_DB_USER',
  'BETUFU_DB_PASSWORD',
  'BETUFU_REDIS_HOST',
  'BETUFU_REDIS_PORT',
  'BETUFU_REDIS_PASSWORD',
  'BETUFU_OIDC_CLIENT_ID',
  'BETUFU_OIDC_CLIENT_SECRET',
  'BETUFU_OIDC_ISSUER_URI',
  'BETUFU_TECHNICAL_USERNAME',
  'BETUFU_TECHNICAL_PASSWORD',
]);

export class RuntimePrerequisiteContractError extends Error {
  constructor(rule, detail, options = {}) {
    super(rule + ': ' + detail);
    this.name = 'RuntimePrerequisiteContractError';
    this.rule = rule;
    if (options.cause !== undefined) {
      this.cause = options.cause;
    }
  }
}

function fail(rule, detail) {
  throw new RuntimePrerequisiteContractError(rule, detail);
}

function expect(condition, rule, detail) {
  if (!condition) {
    fail(rule, detail);
  }
}

function contractError(rule, detail, cause) {
  return new RuntimePrerequisiteContractError(rule, detail, { cause });
}

function abortReason(signal, rule = 'RUNTIME_ABORT') {
  if (signal?.reason instanceof RuntimePrerequisiteContractError) {
    return signal.reason;
  }
  return contractError(rule, 'operacao runtime interrompida.');
}

function throwIfAborted(signal) {
  if (signal?.aborted) {
    throw abortReason(signal);
  }
}

export function childHasExited(child) {
  return child.exitCode != null || child.signalCode != null;
}

function delay(milliseconds, signal) {
  throwIfAborted(signal);
  let timeout;
  let onAbort;
  return new Promise((resolvePromise, rejectPromise) => {
    timeout = setTimeout(resolvePromise, milliseconds);
    onAbort = () => {
      clearTimeout(timeout);
      rejectPromise(abortReason(signal));
    };
    signal?.addEventListener('abort', onAbort, { once: true });
  }).finally(() => {
    clearTimeout(timeout);
    signal?.removeEventListener('abort', onAbort);
  });
}

async function withDeadline(operation, context, label) {
  throwIfAborted(context.signal);
  const remaining = context.deadline - Date.now();
  expect(remaining > 0, 'RUNTIME_TIMEOUT', 'deadline global expirou antes de ' + label + '.');
  const timeoutMilliseconds = Math.min(
    context.stepTimeoutMilliseconds,
    remaining,
  );
  let timeout;
  let onAbort;
  try {
    return await Promise.race([
      Promise.resolve().then(operation),
      new Promise((_, rejectPromise) => {
        timeout = setTimeout(() => {
          const error = contractError(
            'RUNTIME_TIMEOUT',
            label + ' excedeu o prazo.',
          );
          context.controller.abort(error);
          rejectPromise(error);
        }, timeoutMilliseconds);
      }),
      new Promise((_, rejectPromise) => {
        onAbort = () => rejectPromise(abortReason(context.signal));
        context.signal.addEventListener('abort', onAbort, { once: true });
      }),
    ]);
  } finally {
    clearTimeout(timeout);
    context.signal.removeEventListener('abort', onAbort);
  }
}

function countMatches(source, pattern) {
  const expression = new RegExp(
    pattern.source,
    pattern.flags.includes('g') ? pattern.flags : pattern.flags + 'g',
  );
  return Array.from(source.matchAll(expression)).length;
}

function normalizeText(source, label) {
  expect(!source.includes('\u0000'), 'TEXT_NUL', label + ' contem byte NUL.');
  return source.replace(/\r\n?/gu, '\n');
}

function resolveContained(root, relativePath) {
  expect(
    typeof relativePath === 'string' && relativePath !== '' && !isAbsolute(relativePath),
    'PATH_INVALID',
    'path relativo invalido.',
  );
  const resolvedRoot = resolve(root);
  const candidate = resolve(resolvedRoot, relativePath);
  expect(
    candidate === resolvedRoot || candidate.startsWith(resolvedRoot + sep),
    'PATH_ESCAPE',
    'path escapou da raiz aprovada.',
  );
  return candidate;
}

async function readRequiredText(root, relativePath) {
  const absolutePath = resolveContained(root, relativePath);
  let metadata;
  try {
    metadata = await lstat(absolutePath);
  } catch {
    fail('REQUIRED_FILE', relativePath + ' nao existe.');
  }
  expect(
    metadata.isFile() && !metadata.isSymbolicLink(),
    'REQUIRED_FILE',
    relativePath + ' deve ser arquivo regular sem link.',
  );
  expect(metadata.size <= MAX_TEXT_BYTES, 'FILE_SIZE', relativePath + ' excede o limite.');
  try {
    return normalizeText(await readFile(absolutePath, 'utf8'), relativePath);
  } catch {
    fail('FILE_READ', relativePath + ' nao pode ser lido como UTF-8.');
  }
}

async function readSourceTree(root, relativeRoot, extension, excluded = () => false) {
  const treeRoot = resolveContained(root, relativeRoot);
  const sources = new Map();

  async function visit(directory) {
    const directoryMetadata = await lstat(directory);
    expect(
      directoryMetadata.isDirectory() && !directoryMetadata.isSymbolicLink(),
      'SOURCE_TREE',
      relativeRoot + ' contem diretorio invalido.',
    );
    const entries = await readdir(directory, { withFileTypes: true });
    entries.sort((left, right) => left.name.localeCompare(right.name, 'en'));
    for (const entry of entries) {
      const absolutePath = join(directory, entry.name);
      const relativePath = relative(resolve(root), absolutePath).replaceAll('\\', '/');
      if (excluded(relativePath)) {
        continue;
      }
      expect(!entry.isSymbolicLink(), 'SOURCE_LINK', relativePath + ' nao pode ser link.');
      if (entry.isDirectory()) {
        await visit(absolutePath);
      } else if (
        entry.isFile()
          && relativePath.toLowerCase().endsWith(extension.toLowerCase())
      ) {
        sources.set(relativePath, await readRequiredText(root, relativePath));
      }
    }
  }

  await visit(treeRoot);
  return sources;
}

function applicationConfigurationSources(sources, extension) {
  const pattern = new RegExp(
    '/application(?:-[^/]+)?\\.' + extension + '$',
    'iu',
  );
  return new Map(
    Array.from(sources).filter(([relativePath]) => pattern.test(relativePath)),
  );
}

const SPRING_CONFIGURATION_FILE_PATTERN =
  /^application(?:-[^/]+)?\.(?:yaml|yml|properties)$/iu;

async function listExternalSpringConfigurationPaths(root) {
  const paths = [];

  async function inspectDirectory(relativeDirectory, childDepth, required) {
    const absoluteDirectory = resolveContained(root, relativeDirectory);
    let metadata;
    try {
      metadata = await lstat(absoluteDirectory);
    } catch (error) {
      if (!required && error?.code === 'ENOENT') {
        return;
      }
      fail(
        'APPLICATION_CONFIG_SOURCE',
        relativeDirectory + ' nao pode ser inspecionado para ConfigData.',
      );
    }
    expect(
      metadata.isDirectory() && !metadata.isSymbolicLink(),
      'APPLICATION_CONFIG_SOURCE',
      relativeDirectory + ' deve ser diretorio regular sem link.',
    );

    const entries = await readdir(absoluteDirectory, { withFileTypes: true });
    entries.sort((left, right) => left.name.localeCompare(right.name, 'en'));
    for (const entry of entries) {
      const relativePath = relativeDirectory + '/' + entry.name;
      if (SPRING_CONFIGURATION_FILE_PATTERN.test(entry.name)) {
        expect(
          entry.isFile() && !entry.isSymbolicLink(),
          'APPLICATION_CONFIG_SOURCE',
          relativePath + ' deve ser arquivo regular sem link.',
        );
        paths.push(relativePath);
        continue;
      }
      if (childDepth > 0 && (entry.isDirectory() || entry.isSymbolicLink())) {
        expect(
          entry.isDirectory() && !entry.isSymbolicLink(),
          'APPLICATION_CONFIG_SOURCE',
          relativePath + ' nao pode ser link em config/*/.',
        );
        await inspectDirectory(relativePath, childDepth - 1, true);
      }
    }
  }

  await inspectDirectory(PATHS.backend, 0, true);
  await inspectDirectory(PATHS.backendConfig, 1, false);
  return paths;
}

function stripComments(source) {
  let output = '';
  let index = 0;
  let quote = null;

  while (index < source.length) {
    const current = source[index];
    const next = source[index + 1];
    if (quote !== null) {
      output += current;
      if (current === '\\' && index + 1 < source.length) {
        output += source[index + 1];
        index += 2;
        continue;
      }
      if (current === quote) {
        quote = null;
      }
      index += 1;
      continue;
    }
    if (current === '"' || current === "'" || current.charCodeAt(0) === 96) {
      quote = current;
      output += current;
      index += 1;
      continue;
    }
    if (current === '/' && next === '/') {
      output += '  ';
      index += 2;
      while (index < source.length && source[index] !== '\n') {
        output += ' ';
        index += 1;
      }
      continue;
    }
    if (current === '/' && next === '*') {
      output += '  ';
      index += 2;
      while (index < source.length) {
        if (source[index] === '*' && source[index + 1] === '/') {
          output += '  ';
          index += 2;
          break;
        }
        output += source[index] === '\n' ? '\n' : ' ';
        index += 1;
      }
      continue;
    }
    output += current;
    index += 1;
  }
  return output;
}

function yamlScalar(value) {
  const trimmed = value.trim();
  if (
    trimmed.length >= 2
      && ((trimmed.startsWith('"') && trimmed.endsWith('"'))
        || (trimmed.startsWith("'") && trimmed.endsWith("'")))
  ) {
    return trimmed.slice(1, -1);
  }
  return trimmed;
}

function yamlContentWithoutComment(source) {
  let quote = null;
  for (let index = 0; index < source.length; index += 1) {
    const current = source[index];
    if (quote !== null) {
      if (current === quote && source[index - 1] !== '\\') {
        quote = null;
      }
      continue;
    }
    if (current === '"' || current === "'") {
      quote = current;
      continue;
    }
    if (current === '#' && (index === 0 || /\s/u.test(source[index - 1]))) {
      return source.slice(0, index).trimEnd();
    }
  }
  return source.trimEnd();
}

function yamlMappingSeparator(source) {
  let quote = null;
  for (let index = 0; index < source.length; index += 1) {
    const current = source[index];
    if (quote !== null) {
      if (current === quote && source[index - 1] !== '\\') {
        quote = null;
      }
      continue;
    }
    if (current === '"' || current === "'") {
      quote = current;
    } else if (current === ':') {
      return index;
    }
  }
  return -1;
}

function hasYamlFlowCollection(source) {
  let quote = null;
  for (let index = 0; index < source.length; index += 1) {
    const current = source[index];
    if (quote !== null) {
      if (current === quote && source[index - 1] !== '\\') {
        quote = null;
      }
      continue;
    }
    if (current === '"' || current === "'") {
      quote = current;
    } else if (current === '[' || (current === '{' && source[index - 1] !== '$')) {
      return true;
    }
  }
  return false;
}

function yamlSyntaxOutsideQuotes(source) {
  let output = '';
  let quote = null;
  for (let index = 0; index < source.length; index += 1) {
    const current = source[index];
    if (quote === "'") {
      output += ' ';
      if (current === "'" && source[index + 1] === "'") {
        output += ' ';
        index += 1;
      } else if (current === "'") {
        quote = null;
      }
      continue;
    }
    if (quote === '"') {
      output += ' ';
      if (current === '\\' && index + 1 < source.length) {
        output += ' ';
        index += 1;
      } else if (current === '"') {
        quote = null;
      }
      continue;
    }
    if (current === "'" || current === '"') {
      quote = current;
      output += ' ';
    } else {
      output += current;
    }
  }
  return output;
}

function hasYamlIndirectSyntax(source) {
  const active = yamlSyntaxOutsideQuotes(source);
  return /^\s*<<\s*:/u.test(active)
    || /^\s*%/u.test(active)
    || /^\s*\?(?:\s|$)/u.test(active)
    || /(?:^|[\s:[{,])(?:&|\*)(?=\S)/u.test(active)
    || /(?:^|[\s:[{,])!/u.test(active);
}

const PROTECTED_APPLICATION_YAML_PATHS = Object.freeze([
  'spring.autoconfigure.exclude',
  'spring.datasource.url',
  'spring.datasource.username',
  'spring.datasource.password',
  'spring.security.oauth2.client.provider.keycloak.issuer-uri',
  'spring.security.oauth2.client.registration.keycloak.provider',
  'spring.security.oauth2.client.registration.keycloak.client-id',
  'spring.security.oauth2.client.registration.keycloak.client-secret',
  'spring.security.oauth2.client.registration.keycloak.client-authentication-method',
  'spring.security.oauth2.client.registration.keycloak.authorization-grant-type',
  'spring.security.oauth2.client.registration.keycloak.redirect-uri',
  'spring.security.oauth2.client.registration.keycloak.scope',
  'spring.session.timeout',
  'spring.session.data.redis.namespace',
  'spring.session.data.redis.repository-type',
  'spring.session.data.redis.flush-mode',
  'spring.session.data.redis.save-mode',
  'spring.session.store-type',
  'spring.data.redis.host',
  'spring.data.redis.port',
  'spring.data.redis.password',
  'server.servlet.session.cookie.http-only',
  'server.servlet.session.cookie.secure',
  'server.servlet.session.cookie.same-site',
]);

const APPROVED_BLOCK_SEQUENCE_PATHS = Object.freeze([
  'spring.security.oauth2.client.registration.keycloak.scope',
]);

function normalizeYamlPath(path) {
  return path
    .split('.')
    .map((segment) => segment.toLowerCase().replace(/[-_]/gu, ''))
    .join('.');
}

function isProtectedYamlBranch(path) {
  const normalizedPath = normalizeYamlPath(path);
  return PROTECTED_APPLICATION_YAML_PATHS.some(
    (protectedPath) => {
      const normalizedProtectedPath = normalizeYamlPath(protectedPath);
      return normalizedProtectedPath === normalizedPath
        || normalizedProtectedPath.startsWith(normalizedPath + '.');
    },
  );
}

function isApprovedBlockSequencePath(path) {
  const normalizedPath = normalizeYamlPath(path);
  return APPROVED_BLOCK_SEQUENCE_PATHS.some(
    (approvedPath) => normalizeYamlPath(approvedPath) === normalizedPath,
  );
}

function parseApplicationYamlDocuments(source) {
  const documents = [{ entries: new Map() }];
  let document = documents[0];
  let stack = [];

  for (const rawLine of source.split('\n')) {
    if (/^\s*(?:#.*)?$/u.test(rawLine)) {
      continue;
    }
    expect(
      !/^\s*\.\.\.(?=\s|$)/u.test(rawLine),
      'APPLICATION_YAML_DOCUMENT_END',
      'terminador de documento YAML nao e permitido em application*.yaml.',
    );
    expect(
      !/^\s*---(?=\s|$)/u.test(rawLine)
        || /^\s*---\s*(?:#.*)?$/u.test(rawLine),
      'APPLICATION_YAML_DOCUMENT',
      'marcador de documento YAML deve aparecer sozinho, salvo comentario.',
    );
    if (/^\s*---\s*(?:#.*)?$/u.test(rawLine)) {
      document = { entries: new Map() };
      documents.push(document);
      stack = [];
      continue;
    }
    const indentation = rawLine.match(/^ */u)[0].length;
    const content = yamlContentWithoutComment(rawLine.slice(indentation));
    if (content === '') {
      continue;
    }
    expect(
      !content.includes('\\'),
      'APPLICATION_YAML_ESCAPE',
      'escapes YAML nao sao permitidos em application*.yaml.',
    );
    expect(
      !hasYamlFlowCollection(content),
      'APPLICATION_YAML_FLOW',
      'colecoes YAML em flow style nao sao permitidas em application*.yaml.',
    );
    expect(
      !hasYamlIndirectSyntax(content),
      'APPLICATION_YAML_INDIRECT',
      'anchors, aliases, merge keys, tags e chaves indiretas nao sao permitidos.',
    );
    if (content.startsWith('-')) {
      const parentPath = stack.length === 0 ? '' : stack.at(-1).segments.join('.');
      expect(
        normalizeYamlPath(parentPath)
          !== normalizeYamlPath('spring.autoconfigure.exclude'),
        'DEV_SESSION',
        'spring.autoconfigure.exclude nao e permitido em application*.yaml.',
      );
      expect(
        parentPath !== ''
          && (!isProtectedYamlBranch(parentPath)
            || isApprovedBlockSequencePath(parentPath)),
        'APPLICATION_YAML_STRUCTURE',
        'block sequence nao pode redefinir ramo de configuracao protegido.',
      );
      const item = content.slice(1).trim();
      if (item !== '' && yamlMappingSeparator(item) < 0) {
        const normalizedParentPath = normalizeYamlPath(parentPath);
        const values = document.entries.get(normalizedParentPath) ?? [];
        values.push(yamlScalar(item));
        document.entries.set(normalizedParentPath, values);
      }
      continue;
    }
    const separator = yamlMappingSeparator(content);
    if (separator <= 0) {
      continue;
    }

    const key = yamlScalar(content.slice(0, separator));
    expect(
      /^[A-Za-z0-9][A-Za-z0-9_-]*(?:\.[A-Za-z0-9][A-Za-z0-9_-]*)*$/u
        .test(key),
      'APPLICATION_YAML_KEY',
      'chave YAML fora do subset canonico seguro em application*.yaml.',
    );
    const rawValue = content.slice(separator + 1).trim();
    while (stack.length > 0 && stack.at(-1).indentation >= indentation) {
      stack.pop();
    }
    const parentSegments = stack.length === 0 ? [] : stack.at(-1).segments;
    const keySegments = key.split('.').map((segment) => segment.trim());
    const segments = [...parentSegments, ...keySegments];
    const path = normalizeYamlPath(segments.join('.'));
    expect(
      !(path === 'spring' || path.startsWith('spring.'))
        || !hasYamlFlowCollection(rawValue),
      'APPLICATION_YAML_FLOW',
      'colecoes YAML em flow style nao sao permitidas sob spring.',
    );
    if (rawValue === '') {
      const values = document.entries.get(path) ?? [];
      values.push(null);
      document.entries.set(path, values);
      stack.push({ indentation, segments });
      continue;
    }

    const values = document.entries.get(path) ?? [];
    values.push(yamlScalar(rawValue));
    document.entries.set(path, values);
  }

  return documents;
}

function yamlValues(document, path) {
  return document.entries.get(normalizeYamlPath(path)) ?? [];
}

function assertSingleYamlValue(
  document,
  path,
  expected,
  rule = 'REDIS_SESSION_SETTING',
) {
  const values = yamlValues(document, path);
  expect(
    values.length === 1 && values[0] === expected,
    rule,
    path + ' deve aparecer exatamente uma vez com o valor aprovado.',
  );
}

function assertUniqueApprovedYamlValue(
  documents,
  approvedDocument,
  path,
  expected,
  rule = 'REDIS_SESSION_SETTING',
) {
  assertSingleYamlValue(approvedDocument, path, expected, rule);
  const occurrences = documents.reduce(
    (total, document) => total + yamlValues(document, path).length,
    0,
  );
  expect(
    occurrences === 1,
    rule,
    path + ' deve existir somente no documento runtime | integrated aprovado.',
  );
}

function assertUniqueApprovedYamlSequence(
  documents,
  approvedDocument,
  path,
  expectedItems,
  rule,
) {
  const approvedValues = yamlValues(approvedDocument, path);
  expect(
    approvedValues.length === expectedItems.length + 1
      && approvedValues[0] === null
      && expectedItems.every(
        (expected, index) => approvedValues[index + 1] === expected,
      ),
    rule,
    path + ' deve conter somente a sequencia aprovada.',
  );
  const occurrences = documents.reduce(
    (total, document) => total + yamlValues(document, path).length,
    0,
  );
  expect(
    occurrences === approvedValues.length,
    rule,
    path + ' nao pode ser redefinido em outro documento ou profile.',
  );
}

function assertYamlSubtreeAllowlist(documents, root, allowedPaths) {
  const normalizedRoot = normalizeYamlPath(root);
  const allowed = new Set(allowedPaths.map(normalizeYamlPath));
  for (const document of documents) {
    for (const path of document.entries.keys()) {
      if (path === normalizedRoot || path.startsWith(normalizedRoot + '.')) {
        expect(
          allowed.has(path),
          'APPLICATION_SENSITIVE_SUBTREE',
          root + ' contem propriedade fora da allowlist canonica.',
        );
      }
    }
  }
}

function assertDatasourceContract(application, environment) {
  const placeholderPrefix = '$' + '{';
  const placeholders = [
    'BETUFU_DB_JDBC_URL',
    'BETUFU_DB_USER',
    'BETUFU_DB_PASSWORD',
  ];
  for (const variable of placeholders) {
    const exact = placeholderPrefix + variable + '}';
    expect(
      application.split(exact).length - 1 === 1,
      'DATASOURCE_PLACEHOLDER',
      variable + ' deve aparecer exatamente uma vez.',
    );
    expect(
      !application.includes(placeholderPrefix + variable + ':'),
      'DATASOURCE_DEFAULT',
      variable + ' nao pode possuir fallback.',
    );
  }
  expect(
    application.split(placeholderPrefix + 'BETUFU_DB_').length - 1 === 3,
    'DATASOURCE_PLACEHOLDER',
    'application.yaml deve conter somente os tres placeholders datasource.',
  );
  expect(
    !application.includes(LOCAL_JDBC_URL),
    'DATASOURCE_LOCAL_LEAK',
    'URL local deve ficar fora de application.yaml.',
  );
  expect(
    environment.split('BETUFU_DB_JDBC_URL=' + LOCAL_JDBC_URL).length - 1 === 1,
    'LOCAL_ENV',
    'URL JDBC local exata deve aparecer uma vez no .env.example.',
  );
  expect(
    /^BETUFU_DB_USER=betufu_app$/mu.test(environment),
    'LOCAL_ENV',
    'role local da aplicacao deve ser betufu_app.',
  );
  expect(
    !/^SPRING_DATASOURCE_/mu.test(environment),
    'LOCAL_ENV',
    'nomes SPRING_DATASOURCE duplicados nao sao permitidos.',
  );
}

function assertRedisSessionContract(applicationConfigurations, environment, pom) {
  expect(
    [
      PATHS.application,
      PATHS.development,
      PATHS.homolog,
      PATHS.production,
    ].every((path) => applicationConfigurations.has(path)),
    'REQUIRED_FILE',
    'os quatro application*.yaml canonicos devem permanecer cobertos.',
  );

  const forbiddenSessionClass =
    'org.springframework.boot.session.autoconfigure.SessionAutoConfiguration';
  const documentsByPath = new Map();
  for (const [relativePath, source] of applicationConfigurations) {
    expect(
      !source.includes(REDIS_SESSION_AUTO_CONFIGURATION),
      'DEV_SESSION',
      'SessionDataRedisAutoConfiguration nao pode ser excluida em application*.yaml.',
    );
    expect(
      !source.includes(forbiddenSessionClass),
      'DEV_SESSION',
      'SessionAutoConfiguration nao pode ser excluida em application*.yaml.',
    );
    const documents = parseApplicationYamlDocuments(source);
    documentsByPath.set(relativePath, documents);
    for (const document of documents) {
      expect(
        yamlValues(document, 'spring.autoconfigure.exclude').length === 0,
        'DEV_SESSION',
        'spring.autoconfigure.exclude nao e permitido em application*.yaml.',
      );
      expect(
        yamlValues(document, 'spring.session.store-type').length === 0,
        'DEV_SESSION',
        'spring.session.store-type nao e permitido em ' + relativePath + '.',
      );
      expect(
        yamlValues(document, 'spring.config.import').length === 0,
        'APPLICATION_CONFIG_IMPORT',
        'spring.config.import nao e permitido em application*.yaml.',
      );
    }
  }

  const applicationDocuments = documentsByPath.get(PATHS.application);
  const runtimeDocuments = applicationDocuments.filter(
    (document) => yamlValues(document, 'spring.config.activate.on-profile')
      .some((value) => value.replace(/\s/gu, '') === 'runtime|integrated'),
  );
  expect(
    runtimeDocuments.length === 1
      && yamlValues(
        runtimeDocuments[0],
        'spring.config.activate.on-profile',
      ).length === 1,
    'REDIS_SESSION_PROFILE',
    'deve existir um unico documento runtime | integrated.',
  );
  const runtimeDocument = runtimeDocuments[0];
  const allApplicationDocuments = Array.from(documentsByPath.values()).flat();
  assertYamlSubtreeAllowlist(
    allApplicationDocuments,
    'spring.datasource',
    [
      'spring.datasource',
      'spring.datasource.url',
      'spring.datasource.username',
      'spring.datasource.password',
    ],
  );
  assertYamlSubtreeAllowlist(
    allApplicationDocuments,
    'spring.session',
    [
      'spring.session',
      'spring.session.timeout',
      'spring.session.data',
      'spring.session.data.redis',
      'spring.session.data.redis.namespace',
      'spring.session.data.redis.repository-type',
      'spring.session.data.redis.flush-mode',
      'spring.session.data.redis.save-mode',
    ],
  );
  assertYamlSubtreeAllowlist(
    allApplicationDocuments,
    'spring.data.redis',
    [
      'spring.data.redis',
      'spring.data.redis.host',
      'spring.data.redis.port',
      'spring.data.redis.password',
    ],
  );
  assertYamlSubtreeAllowlist(
    allApplicationDocuments,
    'spring.security.oauth2.client',
    [
      'spring.security.oauth2.client',
      'spring.security.oauth2.client.provider',
      'spring.security.oauth2.client.provider.keycloak',
      'spring.security.oauth2.client.provider.keycloak.issuer-uri',
      'spring.security.oauth2.client.registration',
      'spring.security.oauth2.client.registration.keycloak',
      'spring.security.oauth2.client.registration.keycloak.provider',
      'spring.security.oauth2.client.registration.keycloak.client-id',
      'spring.security.oauth2.client.registration.keycloak.client-secret',
      'spring.security.oauth2.client.registration.keycloak.client-authentication-method',
      'spring.security.oauth2.client.registration.keycloak.authorization-grant-type',
      'spring.security.oauth2.client.registration.keycloak.redirect-uri',
      'spring.security.oauth2.client.registration.keycloak.scope',
    ],
  );
  assertYamlSubtreeAllowlist(
    allApplicationDocuments,
    'server.servlet.session',
    [
      'server.servlet.session',
      'server.servlet.session.cookie',
      'server.servlet.session.cookie.http-only',
      'server.servlet.session.cookie.secure',
      'server.servlet.session.cookie.same-site',
    ],
  );
  assertYamlSubtreeAllowlist(
    allApplicationDocuments,
    'betufu.verification',
    [
      'betufu.verification',
      'betufu.verification.matricula-hmac',
      'betufu.verification.matricula-hmac.current-version',
      'betufu.verification.matricula-hmac.keys',
      'betufu.verification.matricula-hmac.keys.v1',
    ],
  );
  const activationPrefix = normalizeYamlPath('spring.config.activate') + '.';
  const approvedActivationPath = normalizeYamlPath(
    'spring.config.activate.on-profile',
  );
  const runtimeActivationPaths = Array.from(runtimeDocument.entries.keys())
    .filter((path) => path.startsWith(activationPrefix));
  expect(
    runtimeActivationPaths.length === 1
      && runtimeActivationPaths[0] === approvedActivationPath
      && yamlValues(runtimeDocument, 'spring.config.activate').length === 1
      && yamlValues(runtimeDocument, 'spring.config.activate')[0] === null,
    'APPLICATION_PROFILE_ACTIVATION',
    'runtime | integrated nao pode possuir condicao adicional de ativacao.',
  );

  const baseDocument = applicationDocuments[0];
  expect(
    yamlValues(baseDocument, 'spring.config.activate').length === 0
      && Array.from(baseDocument.entries.keys())
        .every((path) => !path.startsWith(activationPrefix)),
    'APPLICATION_PROFILE_ACTIVATION',
    'documento base dos grupos de profile deve ser incondicional.',
  );
  for (const profile of ['dev', 'homolog', 'prod']) {
    assertUniqueApprovedYamlSequence(
      allApplicationDocuments,
      baseDocument,
      'spring.profiles.group.' + profile,
      ['runtime'],
      'APPLICATION_PROFILE_GROUP',
    );
  }

  const allApplicationYaml = Array.from(applicationConfigurations.values()).join('\n');
  for (const [path, variable] of [
    ['spring.datasource.url', 'BETUFU_DB_JDBC_URL'],
    ['spring.datasource.username', 'BETUFU_DB_USER'],
    ['spring.datasource.password', 'BETUFU_DB_PASSWORD'],
  ]) {
    const placeholder = '${' + variable + '}';
    assertUniqueApprovedYamlValue(
      allApplicationDocuments,
      runtimeDocument,
      path,
      placeholder,
      'DATASOURCE_SETTING',
    );
    expect(
      allApplicationYaml.split(placeholder).length - 1 === 1
        && !allApplicationYaml.includes('${' + variable + ':'),
      'DATASOURCE_PLACEHOLDER',
      variable + ' deve ser unico em ConfigData e nao possuir fallback.',
    );
  }

  for (const [path, expected] of [
    ['spring.session.timeout', '8h'],
    ['spring.session.data.redis.namespace', 'betufu:session'],
    ['spring.session.data.redis.repository-type', 'default'],
    ['spring.session.data.redis.flush-mode', 'on-save'],
    ['spring.session.data.redis.save-mode', 'on-set-attribute'],
  ]) {
    assertUniqueApprovedYamlValue(
      allApplicationDocuments,
      runtimeDocument,
      path,
      expected,
    );
  }

  for (const [path, variable] of [
    ['spring.data.redis.host', 'BETUFU_REDIS_HOST'],
    ['spring.data.redis.port', 'BETUFU_REDIS_PORT'],
    ['spring.data.redis.password', 'BETUFU_REDIS_PASSWORD'],
  ]) {
    const placeholder = '${' + variable + '}';
    assertUniqueApprovedYamlValue(
      allApplicationDocuments,
      runtimeDocument,
      path,
      placeholder,
    );
    expect(
      allApplicationYaml.split(placeholder).length - 1 === 1
        && !allApplicationYaml.includes('${' + variable + ':'),
      'REDIS_PLACEHOLDER',
      variable + ' deve aparecer exatamente uma vez e sem fallback.',
    );
  }

  for (const [path, expected] of [
    [
      'spring.security.oauth2.client.provider.keycloak.issuer-uri',
      '${BETUFU_OIDC_ISSUER_URI}',
    ],
    [
      'spring.security.oauth2.client.registration.keycloak.provider',
      'keycloak',
    ],
    [
      'spring.security.oauth2.client.registration.keycloak.client-id',
      '${BETUFU_OIDC_CLIENT_ID}',
    ],
    [
      'spring.security.oauth2.client.registration.keycloak.client-secret',
      '${BETUFU_OIDC_CLIENT_SECRET}',
    ],
    [
      'spring.security.oauth2.client.registration.keycloak.client-authentication-method',
      'client_secret_basic',
    ],
    [
      'spring.security.oauth2.client.registration.keycloak.authorization-grant-type',
      'authorization_code',
    ],
    [
      'spring.security.oauth2.client.registration.keycloak.redirect-uri',
      '{baseUrl}/login/oauth2/code/{registrationId}',
    ],
  ]) {
    assertUniqueApprovedYamlValue(
      allApplicationDocuments,
      runtimeDocument,
      path,
      expected,
      'OIDC_SETTING',
    );
  }
  for (const variable of [
    'BETUFU_OIDC_ISSUER_URI',
    'BETUFU_OIDC_CLIENT_ID',
    'BETUFU_OIDC_CLIENT_SECRET',
  ]) {
    const placeholder = '${' + variable + '}';
    expect(
      allApplicationYaml.split(placeholder).length - 1 === 1
        && !allApplicationYaml.includes('${' + variable + ':'),
      'OIDC_PLACEHOLDER',
      variable + ' deve ser unico em ConfigData e nao possuir fallback.',
    );
  }
  assertUniqueApprovedYamlSequence(
    allApplicationDocuments,
    runtimeDocument,
    'spring.security.oauth2.client.registration.keycloak.scope',
    ['openid'],
    'OIDC_SETTING',
  );

  for (const variable of [
    'BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION',
    'BETUFU_MATRICULA_HMAC_KEY_V1',
  ]) {
    expect(
      !allApplicationYaml.includes('${' + variable + ':'),
      'MATRICULA_HMAC_PLACEHOLDER',
      variable + ' nao pode possuir fallback.',
    );
  }

  for (const [path, expected] of [
    [
      'betufu.verification.matricula-hmac.current-version',
      '${BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION}',
    ],
    [
      'betufu.verification.matricula-hmac.keys.v1',
      '${BETUFU_MATRICULA_HMAC_KEY_V1}',
    ],
  ]) {
    assertUniqueApprovedYamlValue(
      allApplicationDocuments,
      baseDocument,
      path,
      expected,
      'MATRICULA_HMAC_SETTING',
    );
  }

  for (const [path, expected] of [
    ['server.servlet.session.cookie.http-only', 'true'],
    ['server.servlet.session.cookie.secure', 'true'],
    [
      'server.servlet.session.cookie.same-site',
      '${BETUFU_SESSION_COOKIE_SAME_SITE:lax}',
    ],
  ]) {
    assertUniqueApprovedYamlValue(
      allApplicationDocuments,
      baseDocument,
      path,
      expected,
      'SESSION_COOKIE',
    );
  }

  for (const [name, expected] of [
    ['BETUFU_REDIS_HOST', LOCAL_REDIS_HOST],
    ['BETUFU_REDIS_PORT', LOCAL_REDIS_PORT],
    ['BETUFU_REDIS_PASSWORD', LOCAL_REDIS_PASSWORD],
  ]) {
    const assignment = new RegExp('^' + name + '=', 'gmu');
    const approved = new RegExp(
      '^' + name + '=' + expected.replace(/[.*+?^${}()|[\]\\]/gu, '\\$&') + '$',
      'gmu',
    );
    expect(
      countMatches(environment, assignment) === 1
        && countMatches(environment, approved) === 1,
      'REDIS_LOCAL_ENV',
      name + ' deve possuir exatamente o valor local aprovado.',
    );
  }
  expect(
    !environment.includes('SPRING_DATA_REDIS_'),
    'REDIS_LOCAL_ENV',
    'aliases SPRING_DATA_REDIS_* nao sao permitidos no .env.example.',
  );

  expect(
    countMatches(pom, /<artifactId>spring-boot-starter-session-data-redis<\/artifactId>/gu) === 1,
    'REDIS_STARTER',
    'starter Spring Session Data Redis deve permanecer no POM.',
  );
}

function assertSecurityContract(securitySource) {
  const active = stripComments(securitySource);
  expect(
    countMatches(active, /\bCookieCsrfTokenRepository\.withHttpOnlyFalse\s*\(\s*\)/gu) === 1,
    'CSRF_REPOSITORY',
    'repository oficial com HttpOnly=false deve aparecer exatamente uma vez.',
  );
  const approvedCookieCustomizer =
    /setCookieCustomizer\s*\(\s*cookie\s*->\s*cookie\s*\.path\s*\(\s*"\/"\s*\)\s*\.sameSite\s*\(\s*"Lax"\s*\)\s*\)/gsu;
  expect(
    countMatches(active, /\bsetCookieCustomizer\s*\(/gu) === 1
      && countMatches(active, approvedCookieCustomizer) === 1,
    'CSRF_COOKIE',
    'deve existir um unico customizer com somente Path=/ e SameSite=Lax.',
  );
  expect(
    countMatches(active, /\brepository\s*\.\s*set[A-Z][A-Za-z0-9_]*\s*\(/gu) === 1,
    'CSRF_COOKIE',
    'nenhum setter adicional do repository e permitido.',
  );
  for (const [pattern, label] of [
    [/\.domain\s*\(/u, 'Domain'],
    [/\.secure\s*\(/u, 'Secure'],
    [/\.httpOnly\s*\(/u, 'HttpOnly'],
    [/\.maxAge\s*\(/u, 'Max-Age'],
    [/\.partitioned\s*\(/u, 'Partitioned'],
  ]) {
    expect(!pattern.test(active), 'CSRF_COOKIE', label + ' nao pode ser customizado.');
  }
  expect(
    /\.csrf\s*\(\s*csrf\s*->\s*csrf\s*\.spa\s*\(\s*\)\s*\.csrfTokenRepository\s*\(\s*csrfTokenRepository\s*\)\s*\)/su
      .test(active),
    'CSRF_SPA',
    'chain deve usar spa() e depois o repository aprovado.',
  );
  expect(
    /\.requestMatchers\s*\(\s*"\/api\/v1\/\*\*"\s*\)\s*\.authenticated\s*\(\s*\)/su
      .test(active),
    'SECURITY_BOUNDARY',
    '/api/v1/** deve continuar autenticado.',
  );
  expect(
    /\.anyRequest\s*\(\s*\)\s*\.denyAll\s*\(\s*\)/su.test(active),
    'SECURITY_BOUNDARY',
    'denyAll final deve ser preservado.',
  );
  expect(
    /\.oauth2Login\s*\(\s*(?:Customizer\s*\.\s*withDefaults\s*\(\s*\)|[A-Za-z_$][A-Za-z0-9_$]*\s*->)/su.test(active),
    'OIDC_BOUNDARY',
    'oauth2Login Authorization Code deve ser preservado.',
  );
}

function assertNoCsrfBypass(backendSources) {
  const forbidden = [
    [/\bcsrf\s*\.\s*disable\s*\(/u, 'csrf.disable'],
    [/\bAbstractHttpConfigurer\s*::\s*disable\b/u, 'AbstractHttpConfigurer::disable'],
    [
      /\.csrf\s*\(\s*\(?\s*([A-Za-z_$][A-Za-z0-9_$]*)\s*\)?\s*->[\s\S]{0,512}?\1\s*\.\s*disable\s*\(/u,
      'lambda CSRF disable',
    ],
    [/ignoringRequestMatchers\s*\(/u, 'ignoringRequestMatchers'],
    [/requireCsrfProtectionMatcher\s*\(/u, 'matcher CSRF customizado'],
    [/\bCsrfFilter\b/u, 'CsrfFilter customizado'],
    [/\bCsrfTokenRequestHandler\b/u, 'handler CSRF customizado'],
  ];
  for (const [relativePath, source] of backendSources) {
    const active = stripComments(source);
    for (const [pattern, label] of forbidden) {
      expect(
        !pattern.test(active),
        'CSRF_BYPASS',
        label + ' nao e permitido em ' + relativePath + '.',
      );
    }
  }
}

function assertNoCsrfEndpoint(backendSources) {
  for (const [relativePath, source] of backendSources) {
    expect(
      !/@(?:Get|Post|Put|Delete|Patch|Request)Mapping\s*\([^)]*["'][^"']*csrf[^"']*["']/isu
        .test(source),
      'CSRF_ENDPOINT',
      'endpoint CSRF nao e permitido em ' + relativePath + '.',
    );
  }
}

function assertAngularContract(angularConfig, frontendSources) {
  const activeConfig = stripComments(angularConfig);
  expect(
    countMatches(activeConfig, /\bwithXsrfConfiguration\s*\(/gu) === 1,
    'ANGULAR_XSRF',
    'withXsrfConfiguration deve aparecer exatamente uma vez.',
  );
  expect(
    countMatches(activeConfig, /cookieName\s*:\s*['"]XSRF-TOKEN['"]/gu) === 1,
    'ANGULAR_XSRF',
    'cookie Angular deve ser XSRF-TOKEN.',
  );
  expect(
    countMatches(activeConfig, /headerName\s*:\s*['"]X-XSRF-TOKEN['"]/gu) === 1,
    'ANGULAR_XSRF',
    'header Angular deve ser X-XSRF-TOKEN.',
  );
  for (const [relativePath, source] of frontendSources) {
    expect(
      !/\bdocument\s*\.\s*cookie\b/u.test(source),
      'ANGULAR_MANUAL_XSRF',
      'document.cookie nao e permitido em ' + relativePath + '.',
    );
    expect(
      !/\bwithNoXsrfProtection\s*\(/u.test(source),
      'ANGULAR_XSRF_DISABLED',
      'withNoXsrfProtection nao e permitido.',
    );
    if (relativePath !== PATHS.angularConfig) {
      expect(
        !source.includes(XSRF_HEADER),
        'ANGULAR_MANUAL_XSRF',
        'header XSRF manual nao e permitido em ' + relativePath + '.',
      );
    }
  }
}

function assertCsrfTestContract(testSource) {
  const active = stripComments(testSource);
  const requiredFragments = [
    'materializesTheApprovedCookieOnASafeRequest',
    'derivesSecureFromTheMockRequestSecurityState',
    'rejectsMissingOrDivergentDoubleSubmitValues',
    'acceptsMatchingCookieAndHeaderForAnAuthenticatedMutation',
    'modelsIndependentDoubleSubmitPairsWithoutClaimingAuthenticationRotation',
  ];
  for (const fragment of requiredFragments) {
    expect(active.includes(fragment), 'CSRF_TEST_MATRIX', fragment + ' esta ausente.');
  }
  expect(
    countMatches(active, /status\s*\(\s*\)\.isForbidden\s*\(\s*\)/gu) >= 5,
    'CSRF_TEST_MATRIX',
    'IT deve conter as rejeicoes 403 planejadas.',
  );
  expect(
    countMatches(active, /status\s*\(\s*\)\.isNoContent\s*\(\s*\)/gu) >= 2,
    'CSRF_TEST_MATRIX',
    'IT deve conter duas mutacoes validas.',
  );
}

function assertKeycloakTechnicalUserContract(keycloakRealmSource) {
  let realm;
  try {
    realm = JSON.parse(keycloakRealmSource);
  } catch {
    fail(
      'KEYCLOAK_TECHNICAL_USER',
      'realm Keycloak deve ser JSON valido e preservar o contrato tecnico.',
    );
  }

  expect(
    realm !== null && typeof realm === 'object' && Array.isArray(realm.users),
    'KEYCLOAK_TECHNICAL_USER',
    'realm Keycloak deve preservar o contrato tecnico.',
  );
  const technicalUsers = realm.users.filter(
    (candidate) => candidate !== null
      && typeof candidate === 'object'
      && candidate.username === 'technical',
  );
  expect(
    technicalUsers.length === 1,
    'KEYCLOAK_TECHNICAL_USER',
    'realm Keycloak deve conter um unico usuario tecnico canonico.',
  );

  const technicalUser = technicalUsers[0];
  expect(
    typeof technicalUser.firstName === 'string'
      && technicalUser.firstName.trim() !== ''
      && typeof technicalUser.lastName === 'string'
      && technicalUser.lastName.trim() !== '',
    'KEYCLOAK_TECHNICAL_USER',
    'usuario tecnico Keycloak deve possuir perfil completo.',
  );
  expect(
    realm.verifyEmail === false || technicalUser.emailVerified === true,
    'KEYCLOAK_TECHNICAL_USER',
    'usuario tecnico deve autenticar sem acao obrigatoria pendente de verificacao de email.',
  );
  expect(
    Array.isArray(technicalUser.requiredActions)
      && technicalUser.requiredActions.length === 0,
    'KEYCLOAK_TECHNICAL_USER',
    'usuario tecnico nao pode possuir acao obrigatoria explicita.',
  );
  const passwordCredentials = Array.isArray(technicalUser.credentials)
    ? technicalUser.credentials.filter((credential) => credential?.type === 'password')
    : [];
  expect(
    passwordCredentials.length === 1 && passwordCredentials[0].temporary === false,
    'KEYCLOAK_TECHNICAL_USER',
    'usuario tecnico deve possuir uma unica senha nao temporaria.',
  );
}

export async function validateStaticContract(projectRoot = defaultProjectRoot) {
  const resolvedRoot = await realpath(resolve(projectRoot));
  const [
    application,
    yamlResources,
    ymlResources,
    propertiesResources,
    externalSpringConfigurationPaths,
    environment,
    keycloakRealm,
    pom,
    security,
    csrfTest,
    angularConfig,
    backendSources,
    frontendSources,
  ] = await Promise.all([
    readRequiredText(resolvedRoot, PATHS.application),
    readSourceTree(resolvedRoot, PATHS.resources, '.yaml'),
    readSourceTree(resolvedRoot, PATHS.resources, '.yml'),
    readSourceTree(resolvedRoot, PATHS.resources, '.properties'),
    listExternalSpringConfigurationPaths(resolvedRoot),
    readRequiredText(resolvedRoot, PATHS.environment),
    readRequiredText(resolvedRoot, PATHS.keycloakRealm),
    readRequiredText(resolvedRoot, PATHS.pom),
    readRequiredText(resolvedRoot, PATHS.security),
    readRequiredText(resolvedRoot, PATHS.csrfTest),
    readRequiredText(resolvedRoot, PATHS.angularConfig),
    readSourceTree(resolvedRoot, PATHS.backendSources, '.java'),
    readSourceTree(
      resolvedRoot,
      PATHS.frontendSources,
      '.ts',
      (path) => path.endsWith('.spec.ts') || path.includes('/api/generated/'),
    ),
  ]);

  const applicationConfigurations = applicationConfigurationSources(
    yamlResources,
    'yaml',
  );
  const alternativeResourceConfigurations = [
    ...applicationConfigurationSources(ymlResources, 'yml').keys(),
    ...applicationConfigurationSources(propertiesResources, 'properties').keys(),
  ];
  const discoveredConfigurationPaths = [
    ...applicationConfigurations.keys(),
    ...alternativeResourceConfigurations,
    ...externalSpringConfigurationPaths,
  ];
  const approvedConfigurationPaths = new Set([
    PATHS.application,
    PATHS.development,
    PATHS.homolog,
    PATHS.production,
  ]);
  expect(
    discoveredConfigurationPaths.length === approvedConfigurationPaths.size
      && discoveredConfigurationPaths.every(
        (path) => approvedConfigurationPaths.has(path),
      ),
    'APPLICATION_CONFIG_SOURCE',
    'somente os quatro application*.yaml canonicos podem participar do ConfigData.',
  );

  assertDatasourceContract(application, environment);
  assertRedisSessionContract(applicationConfigurations, environment, pom);
  assertKeycloakTechnicalUserContract(keycloakRealm);
  assertNoCsrfBypass(backendSources);
  assertSecurityContract(security);
  assertNoCsrfEndpoint(backendSources);
  assertAngularContract(angularConfig, frontendSources);
  assertCsrfTestContract(csrfTest);
  return true;
}

export function assertRuntimeEvidence(evidence) {
  expect(evidence?.status === 'ok', 'RUNTIME_STATUS', 'status runtime deve ser ok.');
  expect(
    evidence?.loginFlow === 'authorization-code-hosted',
    'RUNTIME_LOGIN',
    'fluxo hospedado ausente.',
  );
  expect(
    evidence?.statuses?.missingCookie === 403
      && evidence?.statuses?.missingHeader === 403
      && evidence?.statuses?.divergent === 403,
    'RUNTIME_XSRF_NEGATIVE',
    'matriz negativa XSRF deve retornar tres respostas 403.',
  );
  expect(
    evidence?.statuses?.firstPut === 200 && evidence?.statuses?.secondPut === 200,
    'RUNTIME_XSRF_POSITIVE',
    'duas mutacoes validas devem retornar 200.',
  );
  expect(
    Array.isArray(evidence?.identityIds)
      && evidence.identityIds.length === 2
      && UUID_PATTERN.test(evidence.identityIds[0])
      && evidence.identityIds[0] === evidence.identityIds[1],
    'RUNTIME_IDENTITY',
    'duas mutacoes devem convergir no mesmo UUID.',
  );
  expect(
    evidence?.csrf?.different === true
      && /^[0-9A-F]{64}$/u.test(evidence.csrf.tokenAHash)
      && /^[0-9A-F]{64}$/u.test(evidence.csrf.tokenBHash)
      && evidence.csrf.tokenAHash !== evidence.csrf.tokenBHash
      && evidence.csrf.cookieAApproved === true
      && evidence.csrf.cookieBApproved === true,
    'RUNTIME_ROTATION',
    'transicao A para B nao foi comprovada.',
  );
  expect(
    evidence?.redis?.connectionObserved === false
      && evidence?.redis?.sentinelOccupied === true
      && evidence?.redis?.connectionAttempts === 0
      && evidence?.redis?.cleanupComplete === true,
    'RUNTIME_REDIS',
    'a sentinela detectou participacao de Redis.',
  );
  expect(
    evidence?.browserExposure?.authorizationHeader === false
      && evidence?.browserExposure?.oidcToken === false
      && evidence?.browserExposure?.surface === 'http'
      && evidence?.browserExposure?.urlToken === false
      && evidence?.browserExposure?.bodyToken === false
      && evidence?.browserExposure?.setCookieToken === false
      && evidence?.browserExposure?.cookieJarOriginPartitioned === true,
    'RUNTIME_BROWSER_EXPOSURE',
    'token OIDC ou Authorization chegou a superficie browser.',
  );
  expect(
    evidence?.java?.specificationVersion === '25'
      && typeof evidence?.java?.vendor === 'string'
      && evidence.java.vendor.trim() !== '',
    'RUNTIME_JAVA',
    'identidade Java 25 nao foi comprovada.',
  );
  const backendMetadata = evidence?.ownedProcesses?.backend;
  const proxyMetadata = evidence?.ownedProcesses?.proxy;
  const sentinelMetadata = evidence?.ownedProcesses?.redisSentinel;
  expect(
    Number.isSafeInteger(backendMetadata?.pid)
      && backendMetadata.pid > 0
      && UUID_PATTERN.test(backendMetadata.nonce)
      && Number.isFinite(Date.parse(backendMetadata.startedAt))
      && backendMetadata.port === 8080
      && Number.isSafeInteger(proxyMetadata?.pid)
      && proxyMetadata.pid > 0
      && UUID_PATTERN.test(proxyMetadata.nonce)
      && proxyMetadata.nonce !== backendMetadata.nonce
      && Number.isFinite(Date.parse(proxyMetadata.startedAt))
      && proxyMetadata.port === 4200
      && Number.isSafeInteger(sentinelMetadata?.pid)
      && sentinelMetadata.pid === process.pid
      && UUID_PATTERN.test(sentinelMetadata.nonce)
      && sentinelMetadata.nonce !== backendMetadata.nonce
      && sentinelMetadata.nonce !== proxyMetadata.nonce
      && sentinelMetadata.host === REDIS_SENTINEL_HOST
      && sentinelMetadata.port === REDIS_SENTINEL_PORT
      && Number.isFinite(Date.parse(sentinelMetadata.startedAt)),
    'RUNTIME_METADATA',
    'PID, nonce, porta ou instante dos recursos proprios divergiu.',
  );
  expect(
    evidence?.proxyEvidence?.untrustedForwardedHeadersOverridden === true
      && evidence?.proxyEvidence?.trustedHttpsProducedSecureCookie === true,
    'RUNTIME_PROXY',
    'provas de proxy confiavel/nao confiavel nao foram observadas.',
  );
  expect(
    evidence?.csrf?.httpCookie?.name === XSRF_COOKIE
      && evidence.csrf.httpCookie.hostOnly === true
      && evidence.csrf.httpCookie.path === '/'
      && evidence.csrf.httpCookie.httpOnly === false
      && evidence.csrf.httpCookie.sameSite === 'Lax'
      && evidence.csrf.httpCookie.secure === false
      && evidence?.csrf?.httpsCookie?.name === XSRF_COOKIE
      && evidence.csrf.httpsCookie.hostOnly === true
      && evidence.csrf.httpsCookie.path === '/'
      && evidence.csrf.httpsCookie.httpOnly === false
      && evidence.csrf.httpsCookie.sameSite === 'Lax'
      && evidence.csrf.httpsCookie.secure === true,
    'RUNTIME_COOKIE',
    'Secure do cookie nao acompanhou o scheme reconstruido.',
  );
  expect(
    evidence?.jarSha256 === APPROVED_JAR_SHA256
      && evidence?.jarSize === APPROVED_JAR_SIZE
      && evidence?.jarHashBefore === APPROVED_JAR_SHA256
      && evidence?.jarHashAfter === APPROVED_JAR_SHA256
      && evidence?.jarSizeBefore === APPROVED_JAR_SIZE
      && evidence?.jarSizeAfter === APPROVED_JAR_SIZE,
    'RUNTIME_JAR_DRIFT',
    'JAR divergiu da identidade aprovada antes ou depois da prova.',
  );
  expect(
    evidence?.identityUuid === evidence?.identityIds?.[0],
    'RUNTIME_IDENTITY',
    'identityUuid sanitizado diverge dos corpos HTTP.',
  );
  expect(
    evidence?.cleanup?.proxy === true
      && evidence?.cleanup?.backend === true
      && evidence?.cleanup?.redisSentinel === true,
    'RUNTIME_CLEANUP',
    'cleanup dos recursos proprios nao foi comprovado.',
  );
  return true;
}

export async function runRuntimeProof(
  projectRoot = defaultProjectRoot,
  adapter = createLocalRuntimeAdapter(projectRoot),
  options = {},
) {
  let prepared;
  let redisSentinel;
  let backend;
  let proxy;
  let evidence;
  let primaryFailure;
  const cleanupFailures = [];
  const controller = new AbortController();
  const externalSignal = options.signal;
  const signalEmitter = options.signalEmitter ?? process;
  const timeoutMilliseconds = options.timeoutMilliseconds
    ?? DEFAULT_RUNTIME_DEADLINE_MILLISECONDS;
  const stepTimeoutMilliseconds = options.stepTimeoutMilliseconds
    ?? DEFAULT_STEP_TIMEOUT_MILLISECONDS;
  expect(
    Number.isSafeInteger(timeoutMilliseconds) && timeoutMilliseconds > 0,
    'RUNTIME_TIMEOUT',
    'deadline global invalido.',
  );
  expect(
    Number.isSafeInteger(stepTimeoutMilliseconds) && stepTimeoutMilliseconds > 0,
    'RUNTIME_TIMEOUT',
    'timeout de etapa invalido.',
  );
  const context = {
    controller,
    signal: controller.signal,
    deadline: Date.now() + timeoutMilliseconds,
    stepTimeoutMilliseconds,
  };
  const signalHandlers = new Map();
  let signalFailure;
  let externalAbortFailure;
  let externalAbortHandler;
  let externalAbortListenerInstalled = false;
  if (externalSignal !== undefined) {
    expect(
      typeof externalSignal?.aborted === 'boolean'
        && typeof externalSignal?.addEventListener === 'function'
        && typeof externalSignal?.removeEventListener === 'function',
      'RUNTIME_ABORT',
      'AbortSignal externo invalido.',
    );
    externalAbortHandler = () => {
      externalAbortFailure ??= abortReason(externalSignal);
      if (!controller.signal.aborted) {
        controller.abort(externalAbortFailure);
      }
    };
    if (externalSignal.aborted) {
      externalAbortHandler();
    } else {
      externalSignal.addEventListener('abort', externalAbortHandler, { once: true });
      externalAbortListenerInstalled = true;
    }
  }
  for (const signalName of ['SIGINT', 'SIGTERM']) {
    const handler = () => {
      if (signalFailure !== undefined) {
        return;
      }
      signalFailure = contractError(
        'RUNTIME_SIGNAL',
        'sinal ' + signalName + ' interrompeu a prova.',
      );
      signalFailure.signalName = signalName;
      controller.abort(signalFailure);
    };
    signalHandlers.set(signalName, handler);
    signalEmitter.once(signalName, handler);
  }

  try {
    prepared = await withDeadline(
      () => adapter.prepare(options.expectedArtifact, context.signal),
      context,
      'preparo do artefato',
    );
    redisSentinel = await withDeadline(
      () => adapter.startRedisSentinel(prepared, context.signal),
      context,
      'aquisicao da sentinela Redis',
    );
    await withDeadline(
      () => adapter.preflight(prepared, redisSentinel, context.signal),
      context,
      'preflight local',
    );
    backend = await withDeadline(
      () => adapter.startBackend(prepared, redisSentinel, context.signal),
      context,
      'inicio do backend',
    );
    await withDeadline(
      () => adapter.waitForBackend(prepared, backend, context.signal),
      context,
      'readiness do backend',
    );
    proxy = await withDeadline(
      () => adapter.startProxy(prepared, backend, context.signal),
      context,
      'inicio do proxy',
    );
    evidence = await withDeadline(
      () => adapter.exercise(prepared, backend, proxy, redisSentinel, context.signal),
      context,
      'fluxo hospedado',
    );
  } catch (error) {
    primaryFailure = error;
  } finally {
    if (!controller.signal.aborted) {
      controller.abort(primaryFailure ?? contractError('RUNTIME_CLEANUP', 'inicio do cleanup.'));
    }
  }

  let finalArtifact;
  if (prepared !== undefined && typeof adapter.finish === 'function') {
    const finishTimeoutMilliseconds = Math.min(
      stepTimeoutMilliseconds,
      Math.max(1, context.deadline - Date.now()),
    );
    let finishTimeout;
    const finishController = new AbortController();
    const finishSignalHandler = () => finishController.abort(signalFailure);
    const finishExternalAbortHandler = () => {
      if (!finishController.signal.aborted) {
        finishController.abort(abortReason(externalSignal));
      }
    };
    for (const signalName of ['SIGINT', 'SIGTERM']) {
      signalEmitter.once(signalName, finishSignalHandler);
    }
    if (signalFailure !== undefined) {
      finishController.abort(signalFailure);
    }
    if (externalSignal?.aborted) {
      finishExternalAbortHandler();
    } else {
      externalSignal?.addEventListener('abort', finishExternalAbortHandler, { once: true });
    }
    try {
      finalArtifact = await Promise.race([
        adapter.finish(
          prepared,
          backend,
          proxy,
          redisSentinel,
          finishController.signal,
        ),
        new Promise((_, rejectPromise) => {
          finishTimeout = setTimeout(() => {
            const error = contractError(
              'RUNTIME_TIMEOUT',
              'verificacao final do artefato excedeu o prazo.',
            );
            finishController.abort(error);
            rejectPromise(error);
          }, finishTimeoutMilliseconds);
        }),
      ]);
    } catch (error) {
      cleanupFailures.push('integridade-final');
      primaryFailure ??= error;
    } finally {
      clearTimeout(finishTimeout);
      for (const signalName of ['SIGINT', 'SIGTERM']) {
        signalEmitter.removeListener(signalName, finishSignalHandler);
      }
      externalSignal?.removeEventListener('abort', finishExternalAbortHandler);
      if (!finishController.signal.aborted) {
        finishController.abort(contractError(
          'RUNTIME_CLEANUP',
          'verificacao final concluida.',
        ));
      }
    }
  }

  const cleanupState = {
    proxy: proxy === undefined,
    backend: backend === undefined,
    redisSentinel: redisSentinel === undefined,
  };
  for (const [label, resource, cleanupOperation] of [
    ['proxy', proxy, adapter.stopProxy],
    ['backend', backend, adapter.stopBackend],
    ['redisSentinel', redisSentinel, adapter.stopRedisSentinel],
  ]) {
    if (resource === undefined || typeof cleanupOperation !== 'function') {
      continue;
    }
    let cleanupTimeout;
    try {
      await Promise.race([
        cleanupOperation.call(adapter, resource, prepared),
        new Promise((_, rejectPromise) => {
          cleanupTimeout = setTimeout(
            () => rejectPromise(contractError(
              'RUNTIME_CLEANUP',
              'cleanup excedeu o prazo para ' + label + '.',
            )),
            stepTimeoutMilliseconds,
          );
        }),
      ]);
      cleanupState[label] = true;
    } catch {
      cleanupFailures.push(label);
    } finally {
      clearTimeout(cleanupTimeout);
    }
  }

  for (const [signalName, handler] of signalHandlers) {
    signalEmitter.removeListener(signalName, handler);
  }
  if (externalAbortListenerInstalled) {
    externalSignal.removeEventListener('abort', externalAbortHandler);
  }

  if (externalAbortFailure !== undefined) {
    if (primaryFailure !== undefined && primaryFailure !== externalAbortFailure) {
      externalAbortFailure.cause = primaryFailure;
    }
    primaryFailure = externalAbortFailure;
  }

  if (signalFailure !== undefined) {
    if (primaryFailure !== undefined && primaryFailure !== signalFailure) {
      signalFailure.cause = primaryFailure;
    }
    primaryFailure = signalFailure;
  }

  if (cleanupFailures.length > 0) {
    throw contractError(
      'RUNTIME_CLEANUP',
      'cleanup falhou para recurso proprio: ' + cleanupFailures.join(', ') + '.',
      primaryFailure,
    );
  }
  if (primaryFailure !== undefined) {
    throw primaryFailure;
  }
  evidence.status = 'ok';
  evidence.identityUuid = evidence.identityIds[0];
  evidence.jarSha256 = finalArtifact.sha256;
  evidence.jarSize = finalArtifact.size;
  evidence.jarHashBefore = prepared.jarArtifact.sha256;
  evidence.jarSizeBefore = prepared.jarArtifact.size;
  evidence.jarHashAfter = finalArtifact.sha256;
  evidence.jarSizeAfter = finalArtifact.size;
  evidence.java = prepared.java ?? evidence.java;
  evidence.redis = {
    ...(evidence.redis ?? {}),
    connectionObserved: redisSentinel.connectionObserved ?? false,
    sentinelOccupied: true,
    connectionAttempts: redisSentinel.connectionObserved ? 1 : 0,
    cleanupComplete: cleanupState.redisSentinel,
  };
  evidence.ownedProcesses = {
    backend: {
      pid: backend.child?.pid ?? evidence.ownedProcesses?.backend?.pid,
      nonce: backend.nonce ?? evidence.ownedProcesses?.backend?.nonce,
      port: 8080,
      startedAt: backend.startedAt ?? evidence.ownedProcesses?.backend?.startedAt,
    },
    proxy: {
      pid: proxy.pid ?? evidence.ownedProcesses?.proxy?.pid ?? process.pid,
      nonce: proxy.nonce ?? evidence.ownedProcesses?.proxy?.nonce,
      port: 4200,
      startedAt: proxy.startedAt ?? evidence.ownedProcesses?.proxy?.startedAt,
    },
    redisSentinel: {
      pid: process.pid,
      nonce: redisSentinel.nonce,
      host: REDIS_SENTINEL_HOST,
      port: REDIS_SENTINEL_PORT,
      startedAt: redisSentinel.startedAt,
    },
  };
  evidence.cleanup = cleanupState;
  assertRuntimeEvidence(evidence);
  return evidence;
}

async function inspectJarArtifact(path, signal) {
  const metadata = await lstat(path);
  expect(
    metadata.isFile() && !metadata.isSymbolicLink(),
    'RUNTIME_ARTIFACT',
    'artefato deve ser arquivo regular sem link.',
  );
  expect(
    metadata.size === APPROVED_JAR_SIZE,
    'RUNTIME_ARTIFACT_DRIFT',
    'tamanho do JAR divergiu antes da leitura de hash.',
  );
  const digest = createHash('sha256');
  const stream = createReadStream(path, { signal });
  let bytesRead = 0;
  try {
    for await (const chunk of stream) {
      throwIfAborted(signal);
      bytesRead += chunk.length;
      expect(
        bytesRead <= APPROVED_JAR_SIZE,
        'RUNTIME_ARTIFACT_DRIFT',
        'leitura do JAR excedeu o tamanho aprovado.',
      );
      digest.update(chunk);
    }
  } catch (error) {
    if (signal?.aborted) {
      throw abortReason(signal);
    }
    throw error;
  }
  expect(
    bytesRead === metadata.size,
    'RUNTIME_ARTIFACT_DRIFT',
    'quantidade de bytes lida do JAR divergiu.',
  );
  return {
    sha256: digest.digest('hex').toUpperCase(),
    size: bytesRead,
  };
}

export function assertExpectedRuntimeArtifact(artifact, expected) {
  expect(
    expected?.sha256 === APPROVED_JAR_SHA256 && expected?.size === APPROVED_JAR_SIZE,
    'RUNTIME_ARTIFACT_ANCHOR',
    'SHA-256 e tamanho esperados devem ser informados explicitamente com os valores aprovados.',
  );
  expect(
    artifact?.sha256 === expected.sha256 && artifact?.size === expected.size,
    'RUNTIME_ARTIFACT_DRIFT',
    'JAR nao corresponde ao SHA-256 e tamanho aprovados.',
  );
}

function sha256Text(value) {
  return createHash('sha256').update(value, 'utf8').digest('hex').toUpperCase();
}

function parseEnvironmentFile(source) {
  const result = Object.create(null);
  for (const rawLine of source.split('\n')) {
    const line = rawLine.trim();
    if (line === '' || line.startsWith('#')) {
      continue;
    }
    const separator = line.indexOf('=');
    expect(separator > 0, 'RUNTIME_ENV', 'linha dotenv invalida.');
    const name = line.slice(0, separator);
    const value = line.slice(separator + 1);
    expect(/^[A-Z][A-Z0-9_]*$/u.test(name), 'RUNTIME_ENV', 'nome dotenv invalido.');
    expect(!(name in result), 'RUNTIME_ENV', 'variavel dotenv duplicada: ' + name + '.');
    expect(value !== '', 'RUNTIME_ENV', 'variavel dotenv vazia: ' + name + '.');
    result[name] = value;
  }
  for (const name of REQUIRED_RUNTIME_ENV) {
    expect(name in result, 'RUNTIME_ENV', 'variavel obrigatoria ausente: ' + name + '.');
  }
  expect(
    result.BETUFU_DB_JDBC_URL === LOCAL_JDBC_URL,
    'RUNTIME_ENV',
    'URL JDBC local diverge do contrato.',
  );
  expect(
    result.BETUFU_OIDC_ISSUER_URI === KEYCLOAK_ORIGIN + '/realms/betufu-local',
    'RUNTIME_ENV',
    'issuer local diverge do contrato.',
  );
  return result;
}

async function findBackendJar(root) {
  const target = resolveContained(root, PATHS.backendTarget);
  let entries;
  try {
    entries = await readdir(target, { withFileTypes: true });
  } catch {
    fail('RUNTIME_ARTIFACT', 'backend/target nao existe; execute o checkpoint Maven primeiro.');
  }
  const candidates = entries
    .filter((entry) => entry.isFile())
    .map((entry) => entry.name)
    .filter((name) => /^betufu-backend-.+\.jar$/u.test(name))
    .filter((name) => !name.endsWith('.original.jar'))
    .sort();
  expect(
    candidates.length === 1,
    'RUNTIME_ARTIFACT',
    'deve existir exatamente um JAR executavel betufu-backend em target.',
  );
  const jarPath = join(target, candidates[0]);
  const canonicalTarget = await realpath(target);
  const canonicalJar = await realpath(jarPath);
  expect(
    canonicalJar.startsWith(canonicalTarget + sep),
    'RUNTIME_ARTIFACT',
    'JAR escapou de backend/target.',
  );
  return canonicalJar;
}

function inheritedEnvironmentAllowlist(sourceEnvironment = process.env) {
  const allowedNames = [
    'SystemRoot',
    'WINDIR',
    'ComSpec',
    'PATHEXT',
    'PATH',
    'TEMP',
    'TMP',
  ];
  const result = Object.create(null);
  for (const allowedName of allowedNames) {
    const actualName = Object.keys(sourceEnvironment)
      .find((name) => name.toLowerCase() === allowedName.toLowerCase());
    if (actualName !== undefined && sourceEnvironment[actualName] !== undefined) {
      result[actualName] = sourceEnvironment[actualName];
    }
  }
  return result;
}

export function buildBackendEnvironment(values, nonce, parentEnvironment = {}) {
  const environment = inheritedEnvironmentAllowlist(parentEnvironment);
  for (const name of [
    'BETUFU_DB_JDBC_URL',
    'BETUFU_DB_USER',
    'BETUFU_DB_PASSWORD',
    'BETUFU_REDIS_HOST',
    'BETUFU_REDIS_PORT',
    'BETUFU_REDIS_PASSWORD',
    'BETUFU_OIDC_CLIENT_ID',
    'BETUFU_OIDC_CLIENT_SECRET',
    'BETUFU_OIDC_ISSUER_URI',
  ]) {
    environment[name] = values[name];
  }
  environment.SPRING_PROFILES_ACTIVE = 'dev';
  environment.SPRING_DOCKER_COMPOSE_ENABLED = 'false';
  environment.SERVER_PORT = '8080';
  environment.BETUFU_SESSION_COOKIE_SAME_SITE = 'lax';
  environment.SERVER_SERVLET_SESSION_COOKIE_SECURE = 'false';
  environment.BETUFU_RUNTIME_PROOF_NONCE = nonce;
  environment.SPRING_AUTOCONFIGURE_EXCLUDE = REDIS_SESSION_AUTO_CONFIGURATION;
  return environment;
}

function runCaptured(command, args, options = {}) {
  return new Promise((resolvePromise, rejectPromise) => {
    throwIfAborted(options.signal);
    const child = spawn(command, args, {
      cwd: options.cwd,
      env: options.env,
      shell: false,
      windowsHide: true,
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    const chunks = [];
    let size = 0;
    let settled = false;
    let timeout;
    let killTimeout;
    let forcedFailureTimeout;
    let terminalError;
    const settle = (callback, value) => {
      if (settled) {
        return;
      }
      settled = true;
      clearTimeout(timeout);
      clearTimeout(killTimeout);
      clearTimeout(forcedFailureTimeout);
      options.signal?.removeEventListener('abort', onAbort);
      callback(value);
    };
    const collect = (chunk) => {
      if (size >= 128 * 1024) {
        return;
      }
      const slice = chunk.subarray(0, 128 * 1024 - size);
      chunks.push(slice);
      size += slice.length;
    };
    const terminate = (error) => {
      terminalError ??= error;
      if (!childHasExited(child)) {
        child.kill('SIGTERM');
        killTimeout = setTimeout(() => {
          if (!childHasExited(child)) {
            child.kill('SIGKILL');
          }
        }, 2_000);
        forcedFailureTimeout = setTimeout(() => {
          settle(
            rejectPromise,
            contractError('RUNTIME_CLEANUP', 'subprocesso local nao encerrou no prazo.', terminalError),
          );
        }, 5_000);
      }
    };
    const onAbort = () => {
      terminate(abortReason(options.signal));
    };
    timeout = setTimeout(() => {
      terminate(contractError(
        'RUNTIME_TIMEOUT',
        'subprocesso local excedeu o prazo.',
      ));
    }, options.timeoutMilliseconds ?? DEFAULT_STEP_TIMEOUT_MILLISECONDS);
    options.signal?.addEventListener('abort', onAbort, { once: true });
    child.stdout.on('data', collect);
    child.stderr.on('data', collect);
    child.once('error', () => settle(
      rejectPromise,
      terminalError ?? contractError('RUNTIME_PROCESS', 'processo local nao iniciou.'),
    ));
    child.once('exit', (code, signal) => {
      if (terminalError !== undefined) {
        settle(rejectPromise, terminalError);
        return;
      }
      settle(resolvePromise, {
        code,
        signal,
        output: Buffer.concat(chunks).toString('utf8'),
      });
    });
  });
}

async function assertJava25(environment, signal) {
  const result = await runCaptured(
    'java',
    ['-XshowSettings:properties', '-version'],
    { env: environment, signal, timeoutMilliseconds: 10_000 },
  );
  expect(result.code === 0, 'RUNTIME_JAVA', 'preflight Java falhou.');
  const specificationMatch = result.output.match(
    /java\.specification\.version\s*=\s*(25)(?:\s|$)/u,
  );
  const vendorMatch = result.output.match(/java\.vendor\s*=\s*([^\r\n]+)/u);
  expect(
    specificationMatch !== null,
    'RUNTIME_JAVA',
    'java.specification.version deve ser 25.',
  );
  expect(
    vendorMatch !== null && vendorMatch[1].trim() !== '',
    'RUNTIME_JAVA',
    'vendor Java nao foi identificado.',
  );
  return {
    specificationVersion: specificationMatch[1],
    vendor: vendorMatch[1].trim(),
  };
}

function assertPortFree(host, port, signal) {
  return new Promise((resolvePromise, rejectPromise) => {
    throwIfAborted(signal);
    const server = createServer();
    server.unref();
    const onAbort = () => {
      server.close();
      rejectPromise(abortReason(signal));
    };
    signal?.addEventListener('abort', onAbort, { once: true });
    server.once('error', () => {
      signal?.removeEventListener('abort', onAbort);
      rejectPromise(
        contractError(
        'RUNTIME_PORT',
        host + ':' + port + ' ja esta ocupado; processo preexistente nao sera alterado.',
        ),
      );
    });
    server.listen({ host, port, exclusive: true }, () => {
      server.close((error) => {
        signal?.removeEventListener('abort', onAbort);
        if (error) {
          rejectPromise(contractError(
            'RUNTIME_PORT',
            'nao foi possivel liberar porta de preflight.',
          ));
        } else {
          resolvePromise();
        }
      });
    });
  });
}

function probeTcp(host, port, timeoutMilliseconds = 2_000, signal) {
  return new Promise((resolvePromise, rejectPromise) => {
    throwIfAborted(signal);
    const socket = connect({ host, port });
    let settled = false;
    const settle = (callback, value) => {
      if (settled) {
        return;
      }
      settled = true;
      clearTimeout(timeout);
      signal?.removeEventListener('abort', onAbort);
      callback(value);
    };
    const timeout = setTimeout(() => {
      socket.destroy();
      settle(rejectPromise, contractError(
        'RUNTIME_SERVICE',
        host + ':' + port + ' nao respondeu no prazo.',
      ));
    }, timeoutMilliseconds);
    const onAbort = () => {
      socket.destroy();
      settle(rejectPromise, abortReason(signal));
    };
    signal?.addEventListener('abort', onAbort, { once: true });
    socket.once('connect', () => {
      socket.end();
      settle(resolvePromise);
    });
    socket.once('error', () => {
      settle(rejectPromise, contractError(
        'RUNTIME_SERVICE',
        host + ':' + port + ' nao esta disponivel.',
      ));
    });
  });
}

function startRedisSentinel(signal) {
  return new Promise((resolvePromise, rejectPromise) => {
    throwIfAborted(signal);
    const sockets = new Set();
    const handle = {
      connectionObserved: false,
      nonce: randomUUID(),
      server: undefined,
      sockets,
      startedAt: new Date().toISOString(),
    };
    const server = createNetServer((socket) => {
      handle.connectionObserved = true;
      sockets.add(socket);
      socket.once('close', () => sockets.delete(socket));
      socket.on('error', () => socket.destroy());
      socket.destroy();
    });
    handle.server = server;
    server.unref();
    const onAbort = () => {
      server.close();
      rejectPromise(abortReason(signal));
    };
    signal?.addEventListener('abort', onAbort, { once: true });
    server.once('error', () => {
      signal?.removeEventListener('abort', onAbort);
      rejectPromise(contractError(
        'RUNTIME_REDIS_SENTINEL',
        REDIS_SENTINEL_HOST + ':' + REDIS_SENTINEL_PORT
          + ' nao esta livre; o runtime nao sera iniciado.',
      ));
    });
    server.listen({
      host: REDIS_SENTINEL_HOST,
      port: REDIS_SENTINEL_PORT,
      exclusive: true,
    }, () => {
      signal?.removeEventListener('abort', onAbort);
      resolvePromise(handle);
    });
  });
}

function defaultCookiePath(pathname) {
  if (!pathname.startsWith('/') || pathname === '/') {
    return '/';
  }
  const lastSeparator = pathname.lastIndexOf('/');
  return lastSeparator <= 0 ? '/' : pathname.slice(0, lastSeparator);
}

function cookiePathMatches(requestPath, cookiePath) {
  return requestPath === cookiePath
    || requestPath.startsWith(cookiePath.endsWith('/') ? cookiePath : cookiePath + '/');
}

function isApprovedSecureCookieTransport(url) {
  return url.protocol === 'https:'
    || (
      url.protocol === 'http:'
      && (url.origin === CLIENT_ORIGIN || url.origin === KEYCLOAK_ORIGIN)
    );
}

function parseSetCookieHeader(header, requestUrl) {
  expect(
    typeof header === 'string'
      && Buffer.byteLength(header, 'utf8') <= MAX_COOKIE_HEADER_BYTES
      && !/[\r\n\0]/u.test(header),
    'RUNTIME_COOKIE',
    'Set-Cookie invalido ou excedeu o limite.',
  );
  const url = new URL(requestUrl);
  const parts = header.split(';').map((part) => part.trim());
  const separator = parts[0].indexOf('=');
  expect(separator > 0, 'RUNTIME_COOKIE', 'cookie sem nome/valor valido.');
  const name = parts[0].slice(0, separator);
  const rawValue = parts[0].slice(separator + 1);
  const startsWithQuote = rawValue.startsWith('"');
  const endsWithQuote = rawValue.endsWith('"');
  const quoted = rawValue.length >= 2 && startsWithQuote && endsWithQuote;
  const value = quoted ? rawValue.slice(1, -1) : rawValue;
  expect(
    /^[!#$%&'*+.^_`|~0-9A-Za-z-]+$/u.test(name),
    'RUNTIME_COOKIE',
    'nome de cookie invalido.',
  );
  expect(
    startsWithQuote === endsWithQuote
      && /^[\x21\x23-\x2B\x2D-\x3A\x3C-\x5B\x5D-\x7E]*$/u.test(value),
    'RUNTIME_COOKIE',
    'valor de cookie invalido.',
  );
  const attributes = new Map();
  const booleanAttributes = new Set(['secure', 'httponly', 'partitioned']);
  for (const rawAttribute of parts.slice(1)) {
    expect(rawAttribute !== '', 'RUNTIME_COOKIE', 'atributo de cookie vazio.');
    const attributeSeparator = rawAttribute.indexOf('=');
    const attributeName = (
      attributeSeparator === -1
        ? rawAttribute
        : rawAttribute.slice(0, attributeSeparator)
    ).toLowerCase();
    const attributeValue = attributeSeparator === -1
      ? null
      : rawAttribute.slice(attributeSeparator + 1);
    expect(
      /^[!#$%&'*+.^_`|~0-9A-Za-z-]+$/u.test(attributeName)
        && !attributes.has(attributeName)
        && (!booleanAttributes.has(attributeName) || attributeValue === null),
      'RUNTIME_COOKIE',
      'atributo de cookie duplicado, invalido ou flag com valor.',
    );
    attributes.set(attributeName, attributeValue);
  }
  const domainAttribute = attributes.get('domain');
  const domain = domainAttribute === undefined
    ? url.hostname.toLowerCase()
    : String(domainAttribute).replace(/^\./u, '').toLowerCase();
  if (domainAttribute !== undefined) {
    expect(
      url.hostname === domain || url.hostname.endsWith('.' + domain),
      'RUNTIME_COOKIE',
      'cookie tentou escapar do host atual.',
    );
  }
  const pathAttribute = attributes.get('path');
  const path = typeof pathAttribute === 'string' && pathAttribute.startsWith('/')
    ? pathAttribute
    : defaultCookiePath(url.pathname);
  const maxAge = attributes.get('max-age');
  const expires = attributes.get('expires');
  const expired = (maxAge !== undefined && Number(maxAge) <= 0)
    || (expires !== undefined && Number.isFinite(Date.parse(String(expires)))
      && Date.parse(String(expires)) <= Date.now());
  return {
    name,
    value,
    quoted,
    domain,
    hostOnly: domainAttribute === undefined,
    path,
    secure: attributes.has('secure'),
    httpOnly: attributes.has('httponly'),
    sameSite: attributes.get('samesite'),
    partitioned: attributes.has('partitioned'),
    sourceOrigin: url.origin,
    attributes,
    expired,
  };
}

export function parseAndAssertXsrfSetCookie(
  setCookieHeaders,
  requestUrl,
  expectedSecure = false,
) {
  expect(
    Array.isArray(setCookieHeaders) && setCookieHeaders.length <= MAX_COOKIE_COUNT,
    'RUNTIME_XSRF_COOKIE',
    'quantidade de Set-Cookie invalida.',
  );
  const xsrfCookies = setCookieHeaders
    .map((header) => parseSetCookieHeader(header, requestUrl))
    .filter((cookie) => cookie.name === XSRF_COOKIE);
  expect(
    xsrfCookies.length === 1,
    'RUNTIME_XSRF_COOKIE',
    'resposta deve materializar exatamente um XSRF-TOKEN.',
  );
  const cookie = xsrfCookies[0];
  const allowedAttributes = new Set(['path', 'samesite']);
  if (expectedSecure) {
    allowedAttributes.add('secure');
  }
  expect(
    cookie.value !== ''
      && cookie.quoted === false
      && cookie.hostOnly
      && cookie.path === '/'
      && cookie.attributes.get('path') === '/'
      && String(cookie.sameSite).toLowerCase() === 'lax'
      && cookie.httpOnly === false
      && cookie.partitioned === false
      && cookie.secure === expectedSecure
      && cookie.expired === false
      && [...cookie.attributes.keys()].every((name) => allowedAttributes.has(name))
      && [...allowedAttributes].every((name) => cookie.attributes.has(name)),
    'RUNTIME_XSRF_COOKIE',
    'atributos do XSRF-TOKEN divergem do contrato aprovado.',
  );
  return cookie;
}

export class CookieJar {
  constructor(cookies = []) {
    this.cookies = cookies.map((cookie) => ({ ...cookie }));
  }

  clone() {
    return new CookieJar(this.cookies);
  }

  remove(name) {
    this.cookies = this.cookies.filter((cookie) => cookie.name !== name);
  }

  apply(setCookieHeaders, requestUrl) {
    for (const header of setCookieHeaders) {
      const cookie = parseSetCookieHeader(header, requestUrl);
      this.cookies = this.cookies.filter((current) => !(
        current.name === cookie.name
        && current.sourceOrigin === cookie.sourceOrigin
        && current.domain === cookie.domain
        && current.path === cookie.path
      ));
      if (!cookie.expired && cookie.value !== '') {
        this.cookies.push(cookie);
      }
    }
  }

  matching(urlValue) {
    const url = new URL(urlValue);
    return this.cookies.filter((cookie) => {
      const domainMatches = cookie.hostOnly
        ? url.hostname === cookie.domain
        : url.hostname === cookie.domain || url.hostname.endsWith('.' + cookie.domain);
      return domainMatches
        && cookie.sourceOrigin === url.origin
        && cookiePathMatches(url.pathname, cookie.path)
        && (!cookie.secure || isApprovedSecureCookieTransport(url));
    });
  }

  header(urlValue) {
    return this.matching(urlValue)
      .map((cookie) => cookie.name + '=' + cookie.value)
      .join('; ');
  }

  getScriptReadable(name, urlValue) {
    return this.matching(urlValue).find(
      (cookie) => cookie.name === name && cookie.httpOnly === false,
    );
  }

  names() {
    return this.cookies.map((cookie) => cookie.name);
  }
}

function assertApprovedRuntimeUrl(urlValue, allowedOrigins) {
  expect(
    typeof urlValue === 'string'
      && Buffer.byteLength(urlValue, 'utf8') <= MAX_HTTP_URL_BYTES,
    'RUNTIME_ORIGIN',
    'URL runtime excedeu o limite.',
  );
  let url;
  try {
    url = new URL(urlValue);
  } catch {
    fail('RUNTIME_ORIGIN', 'URL runtime invalida.');
  }
  expect(
    url.protocol === 'http:'
      && allowedOrigins.has(url.origin)
      && url.username === ''
      && url.password === ''
      && url.hash === '',
    'RUNTIME_ORIGIN',
    'origem, userinfo ou fragmento fora do contrato.',
  );
  expect(
    url.hostname === 'localhost' || url.hostname === '127.0.0.1',
    'RUNTIME_ORIGIN',
    'host deve ser loopback literal aprovado.',
  );
  expect(
    !urlContainsOidcToken(url.href),
    'RUNTIME_BROWSER_EXPOSURE',
    'token OIDC apareceu em URL browser-facing.',
  );
  return url;
}

const OAUTH_REDIRECT_ENDPOINT = Object.freeze({
  CLIENT_AUTHORIZATION: 'client-authorization',
  KEYCLOAK_AUTHORIZATION: 'keycloak-authorization',
  KEYCLOAK_AUTHENTICATE: 'keycloak-authenticate',
  CLIENT_CALLBACK: 'client-callback',
  CLIENT_ROOT: 'client-root',
});

function classifyOAuthRedirectEndpoint(url) {
  if (url.origin === CLIENT_ORIGIN
      && url.pathname === '/oauth2/authorization/keycloak'
      && url.search === '') {
    return OAUTH_REDIRECT_ENDPOINT.CLIENT_AUTHORIZATION;
  }
  if (url.origin === KEYCLOAK_ORIGIN
      && url.pathname === '/realms/betufu-local/protocol/openid-connect/auth') {
    return OAUTH_REDIRECT_ENDPOINT.KEYCLOAK_AUTHORIZATION;
  }
  if (url.origin === KEYCLOAK_ORIGIN
      && url.pathname === '/realms/betufu-local/login-actions/authenticate') {
    return OAUTH_REDIRECT_ENDPOINT.KEYCLOAK_AUTHENTICATE;
  }
  if (url.origin === CLIENT_ORIGIN
      && url.pathname === '/login/oauth2/code/keycloak') {
    return OAUTH_REDIRECT_ENDPOINT.CLIENT_CALLBACK;
  }
  if (url.origin === CLIENT_ORIGIN
      && url.pathname === '/'
      && url.search === '') {
    return OAUTH_REDIRECT_ENDPOINT.CLIENT_ROOT;
  }
  return null;
}

function classifyOAuthRedirectDiagnosticEndpoint(url) {
  const approvedEndpoint = classifyOAuthRedirectEndpoint(url);
  if (approvedEndpoint !== null) {
    return approvedEndpoint;
  }
  if (url.origin === CLIENT_ORIGIN) {
    if (url.pathname === '/') {
      return 'client-root-other';
    }
    if (url.pathname === '/login' || url.pathname.startsWith('/login/')) {
      return 'client-login-other';
    }
    if (url.pathname.startsWith('/oauth2/')) {
      return 'client-oauth-other';
    }
    return 'client-other';
  }
  if (url.origin === KEYCLOAK_ORIGIN) {
    const realmPrefix = '/realms/betufu-local/';
    if (url.pathname.startsWith(realmPrefix + 'login-actions/')) {
      return 'keycloak-login-action-other';
    }
    if (url.pathname.startsWith(realmPrefix + 'protocol/')) {
      return 'keycloak-protocol-other';
    }
    return 'keycloak-other';
  }
  return 'other';
}

function classifyOAuthRedirectDiagnosticMethod(method) {
  const upperMethod = String(method).toUpperCase();
  return ['GET', 'POST'].includes(upperMethod) ? upperMethod : 'OTHER';
}

function formatOAuthRedirectDiagnostic({ current, next, method, status }) {
  return 'atual=' + classifyOAuthRedirectDiagnosticEndpoint(current)
    + '; destino=' + classifyOAuthRedirectDiagnosticEndpoint(next)
    + '; metodo=' + classifyOAuthRedirectDiagnosticMethod(method)
    + '; status=' + status + '.';
}

function assertExactOAuthCallback(url) {
  const codeValues = url.searchParams.getAll('code');
  const stateValues = url.searchParams.getAll('state');
  const hasOAuthErrorParameter = [
    'error',
    'error_description',
    'error_uri',
  ].some((parameterName) => url.searchParams.getAll(parameterName).length !== 0);
  expect(
    codeValues.length === 1
      && codeValues[0] !== ''
      && stateValues.length === 1
      && stateValues[0] !== ''
      && !hasOAuthErrorParameter,
    'RUNTIME_REDIRECT',
    'callback OAuth2 de sucesso exige code e state unicos sem parametros de erro.',
  );
}

function isExactOAuthLoginError(url) {
  return url.origin === CLIENT_ORIGIN
    && url.pathname === '/login'
    && url.search === '?error';
}

function assertOAuthRedirectTransition({
  current,
  next,
  status,
  method,
  hasBody,
}) {
  const upperMethod = String(method).toUpperCase();
  const diagnostic = formatOAuthRedirectDiagnostic({
    current,
    next,
    method: upperMethod,
    status,
  });
  const currentKind = classifyOAuthRedirectEndpoint(current);
  if (currentKind === null) {
    fail(
      'RUNTIME_REDIRECT',
      'salto OAuth2 nao classificado; ' + diagnostic,
    );
  }
  if (currentKind === OAUTH_REDIRECT_ENDPOINT.CLIENT_CALLBACK
      && isExactOAuthLoginError(next)) {
    expect(
      status === 302 && upperMethod === 'GET' && hasBody === false,
      'RUNTIME_REDIRECT',
      'redirect de falha OAuth2 divergiu do contrato.',
    );
    fail('RUNTIME_LOGIN', 'callback OAuth2 terminou no handler de falha.');
  }
  if (currentKind === OAUTH_REDIRECT_ENDPOINT.CLIENT_CALLBACK) {
    assertExactOAuthCallback(current);
  }

  const nextKind = classifyOAuthRedirectEndpoint(next);
  if (nextKind === null) {
    fail(
      'RUNTIME_REDIRECT',
      'salto OAuth2 nao classificado; ' + diagnostic,
    );
  }
  if (nextKind === OAUTH_REDIRECT_ENDPOINT.CLIENT_CALLBACK) {
    assertExactOAuthCallback(next);
  }

  const transitionKey = currentKind + ':' + nextKind;
  const transition = {
    [OAUTH_REDIRECT_ENDPOINT.CLIENT_AUTHORIZATION + ':'
      + OAUTH_REDIRECT_ENDPOINT.KEYCLOAK_AUTHORIZATION]: {
      methods: ['GET'],
      hasBody: false,
      statuses: [302],
    },
    [OAUTH_REDIRECT_ENDPOINT.KEYCLOAK_AUTHORIZATION + ':'
      + OAUTH_REDIRECT_ENDPOINT.KEYCLOAK_AUTHENTICATE]: {
      methods: ['GET'],
      hasBody: false,
      statuses: [302, 303],
    },
    [OAUTH_REDIRECT_ENDPOINT.KEYCLOAK_AUTHENTICATE + ':'
      + OAUTH_REDIRECT_ENDPOINT.CLIENT_CALLBACK]: {
      methods: ['POST'],
      hasBody: true,
      statuses: [301, 302, 303],
    },
    [OAUTH_REDIRECT_ENDPOINT.CLIENT_CALLBACK + ':'
      + OAUTH_REDIRECT_ENDPOINT.CLIENT_ROOT]: {
      methods: ['GET'],
      hasBody: false,
      statuses: [302],
    },
  }[transitionKey];
  if (!(transition !== undefined
      && transition.methods.includes(upperMethod)
      && transition.hasBody === hasBody
      && transition.statuses.includes(status))) {
    fail(
      'RUNTIME_REDIRECT',
      'transicao OAuth2 fora da politica; ' + diagnostic,
    );
  }
}

export function requestLocal(urlValue, options = {}) {
  const allowedOrigins = options.allowedOrigins ?? new Set([
    CLIENT_ORIGIN,
    BACKEND_ORIGIN,
    KEYCLOAK_ORIGIN,
  ]);
  const url = assertApprovedRuntimeUrl(urlValue, allowedOrigins);
  throwIfAborted(options.signal);
  const headers = { ...(options.headers ?? {}) };
  expect(
    !Object.keys(headers).some((name) => [
      'authorization',
      'cookie',
    ].includes(name.toLowerCase())),
    'RUNTIME_BROWSER_EXPOSURE',
    'runner nao pode enviar Authorization ou Cookie manual.',
  );
  const cookieHeader = options.jar?.header(urlValue);
  if (cookieHeader) {
    headers.Cookie = cookieHeader;
  }
  headers.Host = url.host;
  if (options.body !== undefined) {
    expect(
      Buffer.byteLength(options.body) <= MAX_HTTP_REQUEST_BYTES,
      'RUNTIME_HTTP',
      'body do request excedeu o limite.',
    );
    headers['Content-Length'] = Buffer.byteLength(options.body);
  }

  return new Promise((resolvePromise, rejectPromise) => {
    let settled = false;
    let timeout;
    const settle = (callback, value) => {
      if (settled) {
        return;
      }
      settled = true;
      clearTimeout(timeout);
      options.signal?.removeEventListener('abort', onAbort);
      callback(value);
    };
    const requestFactory = options.requestFactory ?? httpRequest;
    const request = requestFactory({
      host: '127.0.0.1',
      port: Number(url.port),
      method: options.method ?? 'GET',
      path: url.pathname + url.search,
      headers,
    });
    const onAbort = () => {
      const error = abortReason(options.signal);
      request.destroy(error);
      settle(rejectPromise, error);
    };
    timeout = setTimeout(() => {
      const error = contractError(
        'RUNTIME_HTTP',
        'request loopback excedeu o timeout.',
      );
      request.destroy(error);
      settle(rejectPromise, error);
    }, options.timeoutMilliseconds ?? 10_000);
    options.signal?.addEventListener('abort', onAbort, { once: true });
    request.once('error', () => {
      settle(rejectPromise, contractError(
        'RUNTIME_HTTP',
        'request loopback falhou.',
      ));
    });
    request.once('response', (response) => {
      const chunks = [];
      let size = 0;
      let responseFailure;
      response.on('data', (chunk) => {
        size += chunk.length;
        if (size > MAX_HTTP_BYTES) {
          responseFailure = contractError('RUNTIME_HTTP', 'resposta excedeu o limite.');
          response.destroy(responseFailure);
          return;
        }
        chunks.push(chunk);
      });
      response.once('error', () => {
        settle(
          rejectPromise,
          responseFailure ?? contractError('RUNTIME_HTTP', 'leitura da resposta falhou.'),
        );
      });
      response.once('aborted', () => {
        settle(rejectPromise, contractError('RUNTIME_HTTP', 'resposta foi interrompida.'));
      });
      response.once('end', () => {
        try {
          if (responseFailure !== undefined) {
            settle(rejectPromise, responseFailure);
            return;
          }
          const completedResponse = {
            url: urlValue,
            status: response.statusCode ?? 0,
            headers: response.headers,
            body: Buffer.concat(chunks).toString('utf8'),
            requestEvidence: {
              authorizationHeader: Object.keys(headers).some(
                (name) => name.toLowerCase() === 'authorization',
              ),
            },
          };
          assertNoBrowserResponseExposure(
            completedResponse,
            options.sensitiveBrowserValues,
          );
          const setCookie = response.headers['set-cookie'];
          const setCookieHeaders = Array.isArray(setCookie)
            ? setCookie
            : typeof setCookie === 'string' ? [setCookie] : [];
          options.jar?.apply(setCookieHeaders, urlValue);
          settle(resolvePromise, completedResponse);
        } catch (error) {
          settle(rejectPromise, error);
        }
      });
    });
    if (options.body !== undefined) {
      request.write(options.body);
    }
    request.end();
  });
}

export function assertRedirectPolicy({
  currentUrl,
  nextUrl,
  status,
  method,
  hasBody,
  allowedOrigins = new Set([CLIENT_ORIGIN, KEYCLOAK_ORIGIN]),
  sensitivePostUrl,
  rawLocation,
}) {
  if (rawLocation !== undefined) {
    expect(
      typeof rawLocation === 'string'
        && rawLocation !== ''
        && !rawLocation.includes('\\'),
      'RUNTIME_REDIRECT',
      'Location de redirect nao canonico.',
    );
    const withoutQuery = rawLocation.split(/[?#]/u, 1)[0];
    const absoluteMatch = withoutQuery.match(/^[A-Za-z][A-Za-z0-9+.-]*:\/\/[^/]*(\/.*)?$/u);
    let rawPath = absoluteMatch ? (absoluteMatch[1] ?? '/') : withoutQuery;
    for (let count = 0; count < 4; count += 1) {
      let decoded;
      try {
        decoded = decodeURIComponent(rawPath);
      } catch {
        fail('RUNTIME_REDIRECT', 'Location contem escape percent inválido.');
      }
      if (decoded === rawPath) {
        break;
      }
      rawPath = decoded;
    }
    expect(
      !rawPath.split('/').some((segment) => segment === '.' || segment === '..'),
      'RUNTIME_REDIRECT',
      'Location contem segmento de path nao canonico.',
    );
  }
  expect(
    [301, 302, 303, 307, 308].includes(status),
    'RUNTIME_REDIRECT',
    'status de redirect fora da allowlist explicita.',
  );
  const current = assertApprovedRuntimeUrl(currentUrl, allowedOrigins);
  const next = assertApprovedRuntimeUrl(nextUrl, allowedOrigins);
  const upperMethod = String(method).toUpperCase();
  const preservesBody = status === 307 || status === 308;
  if (sensitivePostUrl !== undefined && upperMethod === 'POST') {
    const sensitive = assertApprovedRuntimeUrl(sensitivePostUrl, allowedOrigins);
    expect(
      current.href === sensitive.href
        && current.origin === KEYCLOAK_ORIGIN
        && /^\/realms\/[A-Za-z0-9._~-]+\/login-actions\/authenticate$/u.test(
          current.pathname,
        ),
      'RUNTIME_LOGIN',
      'POST portador de credenciais saiu do action Keycloak aprovado.',
    );
    expect(
      !preservesBody,
      'RUNTIME_REDIRECT_CREDENTIALS',
      'redirect 307/308 nao pode preservar credenciais de login.',
    );
  }
  expect(
    !(hasBody && preservesBody && current.origin !== next.origin),
    'RUNTIME_REDIRECT_CREDENTIALS',
    'redirect cross-origin nao pode preservar body.',
  );
  assertOAuthRedirectTransition({
    current,
    next,
    status,
    method: upperMethod,
    hasBody,
  });
  return true;
}

async function followRedirects(startUrl, options = {}) {
  let currentUrl = startUrl;
  let method = options.method ?? 'GET';
  let body = options.body;
  const history = [];
  for (let count = 0; count <= (options.maxRedirects ?? 12); count += 1) {
    const response = await requestLocal(currentUrl, {
      ...options,
      method,
      body,
      headers: method === 'GET' ? options.getHeaders ?? {} : options.headers,
      signal: options.signal,
    });
    history.push(response);
    if (response.status < 300 || response.status >= 400 || !response.headers.location) {
      return { response, history };
    }
    const rawLocation = response.headers.location;
    const nextUrl = new URL(rawLocation, currentUrl).href;
    assertRedirectPolicy({
      currentUrl,
      nextUrl,
      status: response.status,
      method,
      hasBody: body !== undefined,
      allowedOrigins: options.allowedOrigins,
      sensitivePostUrl: options.sensitivePostUrl,
      rawLocation,
    });
    if (response.status === 303 || ((response.status === 301 || response.status === 302) && method !== 'GET')) {
      method = 'GET';
      body = undefined;
    }
    currentUrl = nextUrl;
  }
  fail('RUNTIME_REDIRECT', 'limite de redirects excedido.');
}

function decodeHtmlCodePoint(match, encoded, radix) {
  const codePoint = Number.parseInt(encoded, radix);
  return Number.isSafeInteger(codePoint)
      && codePoint >= 0
      && codePoint <= 0x10FFFF
      && !(codePoint >= 0xD800 && codePoint <= 0xDFFF)
    ? String.fromCodePoint(codePoint)
    : match;
}

function decodeHtml(value) {
  return value
    .replaceAll('&amp;', '&')
    .replaceAll('&quot;', '"')
    .replaceAll('&#39;', "'")
    .replaceAll('&lt;', '<')
    .replaceAll('&gt;', '>')
    .replace(/&#x([0-9A-Fa-f]{1,6});/gu, (match, encoded) => (
      decodeHtmlCodePoint(match, encoded, 16)
    ))
    .replace(/&#([0-9]{1,7});/gu, (match, encoded) => (
      decodeHtmlCodePoint(match, encoded, 10)
    ));
}

function parseAttributes(tag) {
  const attributes = Object.create(null);
  const pattern = /([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)')/gu;
  for (const match of tag.matchAll(pattern)) {
    attributes[match[1].toLowerCase()] = decodeHtml(match[2] ?? match[3] ?? '');
  }
  return attributes;
}

function parseLoginForm(html, documentUrl) {
  const formMatch = html.match(/<form\b[^>]*>/iu);
  expect(formMatch !== null, 'RUNTIME_LOGIN', 'formulario hospedado nao foi encontrado.');
  const formAttributes = parseAttributes(formMatch[0]);
  expect(formAttributes.action, 'RUNTIME_LOGIN', 'action do formulario esta ausente.');
  const values = new URLSearchParams();
  for (const inputMatch of html.matchAll(/<input\b[^>]*>/giu)) {
    const attributes = parseAttributes(inputMatch[0]);
    if (attributes.name && attributes.value !== undefined) {
      values.set(attributes.name, attributes.value);
    }
  }
  return {
    action: new URL(formAttributes.action, documentUrl).href,
    values,
  };
}

function decodeUrlParameterName(value) {
  let decoded = String(value);
  for (let count = 0; count < 4; count += 1) {
    let next;
    try {
      next = decodeURIComponent(decoded);
    } catch {
      return decoded.toLowerCase();
    }
    if (next === decoded) {
      break;
    }
    decoded = next;
  }
  return decoded.toLowerCase();
}

function urlContainsOidcToken(urlValue) {
  let url;
  try {
    url = new URL(urlValue);
  } catch {
    fail('RUNTIME_BROWSER_EXPOSURE', 'URL browser-facing invalida.');
  }
  const forbiddenNames = new Set(['access_token', 'id_token', 'refresh_token']);
  return Array.from(url.searchParams.keys()).some(
    (name) => forbiddenNames.has(decodeUrlParameterName(name)),
  );
}

function decodedExposureText(value) {
  let decoded = String(value);
  for (let count = 0; count < 4; count += 1) {
    const next = decoded.replaceAll('+', ' ').replace(
      /(?:%[0-9A-Fa-f]{2})+/gu,
      (encoded) => {
        try {
          return decodeURIComponent(encoded);
        } catch {
          return encoded.replace(
            /%([0-9A-Fa-f]{2})/gu,
            (_match, hex) => String.fromCharCode(Number.parseInt(hex, 16)),
          );
        }
      },
    );
    if (next === decoded) {
      break;
    }
    decoded = next;
  }
  return decoded;
}

function browserExposureVariants(value) {
  const variants = new Set([String(value ?? '')]);
  for (let count = 0; count < 4; count += 1) {
    const current = [...variants];
    for (const candidate of current) {
      variants.add(decodedExposureText(candidate));
      variants.add(decodeHtml(candidate));
    }
  }
  return variants;
}

function containsSensitiveBrowserValue(values, sensitiveBrowserValues = []) {
  const sensitiveValues = sensitiveBrowserValues.filter(
    (value) => typeof value === 'string' && value !== '',
  );
  if (sensitiveValues.length === 0) {
    return false;
  }
  return values.some((value) => [...browserExposureVariants(value)].some(
    (candidate) => sensitiveValues.some((sensitive) => candidate.includes(sensitive)),
  ));
}

function assertNoOidcTokenSetCookie(setCookieHeaders) {
  for (const header of setCookieHeaders) {
    const separator = String(header).indexOf('=');
    const rawName = separator === -1 ? String(header) : String(header).slice(0, separator);
    expect(
      !['access_token', 'id_token', 'refresh_token', 'authorization']
        .includes(decodeUrlParameterName(rawName.trim())),
      'RUNTIME_BROWSER_EXPOSURE',
      'token OIDC apareceu em Set-Cookie browser-facing.',
    );
    expect(
      ![...browserExposureVariants(header)].some((candidate) => (
        /\b(?:access_token|id_token|refresh_token)\b\s*[:=]/iu.test(candidate)
        || /\bAuthorization\s*[:=]\s*Bearer\s+\S+/iu.test(candidate)
      )),
      'RUNTIME_BROWSER_EXPOSURE',
      'token OIDC apareceu no valor de Set-Cookie browser-facing.',
    );
  }
}

function assertNoBrowserResponseExposure(response, sensitiveBrowserValues = []) {
  const decodedBody = [...browserExposureVariants(response.body)].join('\n');
  const bodyContainsToken = (
    /\b(?:access_token|id_token|refresh_token)\b["']?\s*[:=]/iu
      .test(decodedBody)
    || /<input\b[^>]*\bname\s*=\s*(?:["'](?:access_token|id_token|refresh_token|authorization)["']|(?:access_token|id_token|refresh_token|authorization)(?=[\s/>]))/iu
      .test(decodedBody)
  );
  const bodyContainsAuthorization = (
    /(?:^|[,;{\s])["']?authorization["']?\s*[:=]\s*["']?\s*Bearer\s+[^\s"']+/iu
      .test(decodedBody)
  );
  const location = response.headers?.location;
  let locationContainsToken = false;
  if (typeof location === 'string' && location !== '') {
    let locationUrl;
    try {
      locationUrl = new URL(location, response.url).href;
    } catch {
      fail('RUNTIME_BROWSER_EXPOSURE', 'Location browser-facing invalido.');
    }
    locationContainsToken = urlContainsOidcToken(locationUrl);
  }
  const setCookieHeaders = responseSetCookieHeaders(response);
  const sensitiveValueObserved = containsSensitiveBrowserValue(
    [response.url, location ?? '', response.body, ...setCookieHeaders],
    sensitiveBrowserValues,
  );
  expect(
    !urlContainsOidcToken(response.url) && !locationContainsToken,
    'RUNTIME_BROWSER_EXPOSURE',
    'token OIDC apareceu em URL browser-facing.',
  );
  expect(
    !bodyContainsToken && !bodyContainsAuthorization,
    'RUNTIME_BROWSER_EXPOSURE',
    'token OIDC apareceu em body browser-facing.',
  );
  expect(
    !sensitiveValueObserved,
    'RUNTIME_BROWSER_EXPOSURE',
    'credencial de autenticacao apareceu na superficie HTTP browser-facing.',
  );
  assertNoOidcTokenSetCookie(setCookieHeaders);
  return { bodyContainsAuthorization, bodyContainsToken, sensitiveValueObserved };
}

export function assertNoBrowserTokenExposure(history, jar, sensitiveBrowserValues = []) {
  let authorizationHeaderObserved = false;
  let oidcTokenObserved = false;
  for (const response of history) {
    const { bodyContainsAuthorization, bodyContainsToken } =
      assertNoBrowserResponseExposure(response, sensitiveBrowserValues);
    oidcTokenObserved ||= bodyContainsToken;
    authorizationHeaderObserved ||=
      response.requestEvidence?.authorizationHeader === true;
    oidcTokenObserved ||= bodyContainsAuthorization;
  }
  const tokenCookieObserved = jar.names().some(
    (name) => /^(?:access_token|id_token|refresh_token|authorization)$/iu.test(name),
  );
  const urlTokenObserved = history.some((response) => urlContainsOidcToken(response.url));
  const setCookieTokenObserved = history.some((response) => responseSetCookieHeaders(response)
    .some((header) => {
      const separator = String(header).indexOf('=');
      const name = separator === -1 ? String(header) : String(header).slice(0, separator);
      return /^(?:access_token|id_token|refresh_token|authorization)$/iu
        .test(decodeUrlParameterName(name.trim()));
    }));
  const originPartitioned = Array.isArray(jar.cookies) && jar.cookies.every(
    (cookie) => typeof cookie.sourceOrigin === 'string'
      && cookie.sourceOrigin === new URL(cookie.sourceOrigin).origin,
  );
  oidcTokenObserved ||= tokenCookieObserved;
  expect(
    !tokenCookieObserved,
    'RUNTIME_BROWSER_EXPOSURE',
    'token OIDC apareceu em cookie browser-facing.',
  );
  return {
    authorizationHeader: authorizationHeaderObserved,
    oidcToken: oidcTokenObserved,
    surface: 'http',
    urlToken: urlTokenObserved,
    bodyToken: oidcTokenObserved || authorizationHeaderObserved,
    setCookieToken: setCookieTokenObserved,
    cookieJarOriginPartitioned: originPartitioned,
  };
}

function readIdentityResponse(response) {
  expect(response.status === 200, 'RUNTIME_IDENTITY', 'PUT valido nao retornou 200.');
  let document;
  try {
    document = JSON.parse(response.body);
  } catch {
    fail('RUNTIME_IDENTITY', 'resposta do PUT nao e JSON valido.');
  }
  expect(
    document
      && Object.keys(document).sort().join(',') === 'createdAt,identityId,status,updatedAt'
      && document.status === 'PERSISTED'
      && UUID_PATTERN.test(document.identityId),
    'RUNTIME_IDENTITY',
    'resposta do PUT diverge do contrato publico.',
  );
  return document.identityId;
}

export function buildProxyRequestHeaders(incomingHeaders) {
  const forwardedHeaders = Object.create(null);
  for (const name of ['accept', 'content-type', 'cookie', 'user-agent', XSRF_HEADER.toLowerCase()]) {
    if (incomingHeaders[name] !== undefined) {
      forwardedHeaders[name] = incomingHeaders[name];
    }
  }
  forwardedHeaders.host = 'localhost:4200';
  forwardedHeaders['x-forwarded-host'] = 'localhost:4200';
  forwardedHeaders['x-forwarded-port'] = '4200';
  forwardedHeaders['x-forwarded-proto'] = 'http';
  forwardedHeaders['x-forwarded-for'] = '127.0.0.1';
  return forwardedHeaders;
}

export function handleProxyRequest(
  incoming,
  outgoing,
  { requestFactory = httpRequest, upstreamRequests = new Set() } = {},
) {
    if (incoming.headers.host !== 'localhost:4200') {
      outgoing.writeHead(421);
      outgoing.end();
      return undefined;
    }
    if (!incoming.url?.startsWith('/') || incoming.url.includes('://')) {
      outgoing.writeHead(400);
      outgoing.end();
      return undefined;
    }
    const forwardedHeaders = buildProxyRequestHeaders(incoming.headers);
    let upstream;
    let upstreamResponse;
    let failed = false;
    const failSafely = ({ respond = true } = {}) => {
      if (failed) {
        return;
      }
      failed = true;
      incoming.unpipe?.(upstream);
      upstreamResponse?.unpipe?.(outgoing);
      if (upstreamResponse && !upstreamResponse.destroyed) {
        upstreamResponse.destroy?.();
      }
      if (upstream && !upstream.destroyed) {
        upstream.destroy();
      }
      if (!respond || outgoing.destroyed || outgoing.writableEnded) {
        return;
      }
      if (!outgoing.headersSent) {
        outgoing.writeHead(502);
        outgoing.end();
      } else {
        outgoing.destroy?.();
      }
    };
    try {
      upstream = requestFactory({
        host: '127.0.0.1',
        port: 8080,
        method: incoming.method,
        path: incoming.url,
        headers: forwardedHeaders,
      });
    } catch {
      failSafely();
      return undefined;
    }
    upstreamRequests.add(upstream);
    const releaseUpstream = () => upstreamRequests.delete(upstream);
    upstream.once('close', releaseUpstream);
    upstream.setTimeout(10_000, () => {
      upstream.destroy(contractError('RUNTIME_PROXY', 'upstream excedeu o timeout.'));
    });
    incoming.once('aborted', () => {
      upstream.destroy(contractError('RUNTIME_PROXY', 'cliente interrompeu o request.'));
    });
    incoming.on('error', () => failSafely());
    outgoing.on('error', () => failSafely({ respond: false }));
    outgoing.once('close', () => {
      if (!outgoing.writableEnded) {
        upstream.destroy(contractError('RUNTIME_PROXY', 'cliente fechou a resposta.'));
      }
    });
    upstream.on('error', () => failSafely());
    upstream.once('response', (response) => {
      upstreamResponse = response;
      response.on('error', () => failSafely());
      const responseHeaders = Object.create(null);
      for (const name of ['content-type', 'cache-control', 'location', 'set-cookie']) {
        if (response.headers[name] !== undefined) {
          responseHeaders[name] = response.headers[name];
        }
      }
      outgoing.writeHead(response.statusCode ?? 502, responseHeaders);
      response.pipe(outgoing);
    });
    incoming.pipe(upstream);
    return upstream;
}

function createProxyServer() {
  const nonce = randomUUID();
  const upstreamRequests = new Set();
  const server = createServer((incoming, outgoing) => {
    handleProxyRequest(incoming, outgoing, { upstreamRequests });
  });
  return { nonce, server, upstreamRequests };
}

function listen(server, host, port, signal) {
  return new Promise((resolvePromise, rejectPromise) => {
    throwIfAborted(signal);
    const onAbort = () => {
      server.close();
      rejectPromise(abortReason(signal));
    };
    signal?.addEventListener('abort', onAbort, { once: true });
    server.once('error', () => {
      signal?.removeEventListener('abort', onAbort);
      rejectPromise(contractError('RUNTIME_PROXY', 'proxy loopback nao iniciou.'));
    });
    server.listen({ host, port, exclusive: true }, () => {
      signal?.removeEventListener('abort', onAbort);
      resolvePromise();
    });
  });
}

function closeServer(server, timeoutMilliseconds = 5_000) {
  return new Promise((resolvePromise, rejectPromise) => {
    let settled = false;
    const timeout = setTimeout(() => {
      server.closeAllConnections?.();
      if (!settled) {
        settled = true;
        rejectPromise(contractError('RUNTIME_CLEANUP', 'servidor nao fechou no prazo.'));
      }
    }, timeoutMilliseconds);
    server.closeIdleConnections?.();
    server.close((error) => {
      if (settled) {
        return;
      }
      settled = true;
      clearTimeout(timeout);
      if (error) {
        rejectPromise(contractError('RUNTIME_CLEANUP', 'falha ao fechar servidor.'));
      } else {
        resolvePromise();
      }
    });
  });
}

export async function waitForBackendReady(
  backend,
  signal,
  timeoutMilliseconds = 45_000,
  request = requestLocal,
) {
  throwIfAborted(signal);
  await backend.spawnOutcome;
  throwIfAborted(signal);
  const deadline = Date.now() + timeoutMilliseconds;
  while (Date.now() < deadline) {
    if (childHasExited(backend.child)) {
      fail('RUNTIME_BACKEND', 'backend encerrou antes do readiness.');
    }
    try {
      const response = await request(
        BACKEND_ORIGIN + '/actuator/health/liveness',
        {
          allowedOrigins: new Set([BACKEND_ORIGIN]),
          timeoutMilliseconds: 1_500,
          signal,
        },
      );
      if (response.status === 200) {
        return;
      }
    } catch (error) {
      if (!(error instanceof RuntimePrerequisiteContractError)
        || error.rule !== 'RUNTIME_HTTP') {
        throw error;
      }
      // Apenas indisponibilidade de transporte e repetida durante o startup.
    }
    await delay(250, signal);
  }
  fail('RUNTIME_BACKEND', 'backend nao ficou pronto no prazo.');
}

function waitForExit(child, timeoutMilliseconds) {
  if (childHasExited(child)) {
    return Promise.resolve(true);
  }
  return new Promise((resolvePromise) => {
    const timeout = setTimeout(() => resolvePromise(false), timeoutMilliseconds);
    child.once('exit', () => {
      clearTimeout(timeout);
      resolvePromise(true);
    });
  });
}

async function stopOwnedChild(handle) {
  expect(handle.spawnOutcome instanceof Promise, 'RUNTIME_CLEANUP', 'ownership de spawn ausente.');
  try {
    await handle.spawnOutcome;
  } catch {
    expect(
      handle.child.pid === undefined,
      'RUNTIME_CLEANUP',
      'falha de spawn deixou estado de processo ambiguo.',
    );
    return;
  }
  if (childHasExited(handle.child)) {
    return;
  }
  expect(handle.nonce !== '', 'RUNTIME_CLEANUP', 'nonce do processo proprio ausente.');
  handle.child.kill('SIGTERM');
  if (await waitForExit(handle.child, PROCESS_TERMINATION_GRACE_MILLISECONDS)) {
    return;
  }
  handle.child.kill('SIGKILL');
  expect(
    await waitForExit(handle.child, PROCESS_KILL_GRACE_MILLISECONDS),
    'RUNTIME_CLEANUP',
    'processo proprio nao encerrou.',
  );
}

function responseSetCookieHeaders(response) {
  const raw = response.headers?.['set-cookie'];
  return Array.isArray(raw) ? raw : typeof raw === 'string' ? [raw] : [];
}

function sameCookieContract(left, right) {
  if (!(left?.attributes instanceof Map) || !(right?.attributes instanceof Map)) {
    return false;
  }
  const leftAttributes = [...left.attributes.entries()];
  const rightAttributes = [...right.attributes.entries()];
  return left.name === right.name
    && left.value === right.value
    && left.quoted === right.quoted
    && left.domain === right.domain
    && left.hostOnly === right.hostOnly
    && left.path === right.path
    && left.secure === right.secure
    && left.httpOnly === right.httpOnly
    && String(left.sameSite).toLowerCase() === String(right.sameSite).toLowerCase()
    && left.partitioned === right.partitioned
    && left.sourceOrigin === right.sourceOrigin
    && left.expired === right.expired
    && leftAttributes.length === rightAttributes.length
    && leftAttributes.every(([name, value]) => right.attributes.get(name) === value);
}

function clientXsrfCookies(jar) {
  return Array.isArray(jar?.cookies)
    ? jar.cookies.filter((cookie) => (
      cookie.name === XSRF_COOKIE && cookie.sourceOrigin === CLIENT_ORIGIN
    ))
    : [];
}

function assertNoLaterXsrfMutation(history, callbackIndex) {
  const laterXsrfCookies = history.slice(callbackIndex + 1).flatMap((response) => (
    responseSetCookieHeaders(response)
      .map((header) => parseSetCookieHeader(header, response.url))
      .filter((cookie) => cookie.name === XSRF_COOKIE)
  ));
  expect(
    laterXsrfCookies.length === 0,
    'RUNTIME_XSRF_ROTATION',
    'resposta posterior ao callback tentou alterar o XSRF-TOKEN.',
  );
}

export function assertOAuthXsrfRotation(loginHistory, jar, tokenAValue) {
  expect(
    Array.isArray(loginHistory),
    'RUNTIME_XSRF_ROTATION',
    'historico OAuth2 invalido para comprovar a rotacao XSRF.',
  );
  const callbackEntries = loginHistory.flatMap((response, index) => {
    let url;
    try {
      url = new URL(response?.url);
    } catch {
      return [];
    }
    return url.origin === CLIENT_ORIGIN
        && url.pathname === '/login/oauth2/code/keycloak'
      ? [{ index, response, url }]
      : [];
  });
  expect(
    callbackEntries.length === 1,
    'RUNTIME_XSRF_ROTATION',
    'rotacao XSRF exige um unico callback OAuth2 do cliente.',
  );
  const [{ index: callbackIndex, response: callback, url: callbackUrl }] = callbackEntries;
  try {
    assertExactOAuthCallback(callbackUrl);
    const location = callback.headers?.location;
    expect(
      typeof location === 'string' && location !== '',
      'RUNTIME_XSRF_ROTATION',
      'callback OAuth2 nao informou o redirect final aprovado.',
    );
    assertRedirectPolicy({
      currentUrl: callbackUrl.href,
      nextUrl: new URL(location, callbackUrl).href,
      status: callback.status,
      method: 'GET',
      hasBody: false,
      rawLocation: location,
    });
  } catch {
    fail(
      'RUNTIME_XSRF_ROTATION',
      'callback OAuth2 da rotacao divergiu da transicao de sucesso aprovada.',
    );
  }

  const callbackSetCookieHeaders = responseSetCookieHeaders(callback);
  expect(
    callbackSetCookieHeaders.length <= MAX_COOKIE_COUNT,
    'RUNTIME_XSRF_ROTATION',
    'callback OAuth2 excedeu o limite de Set-Cookie.',
  );
  const xsrfSetCookies = callbackSetCookieHeaders
    .map((header) => ({ header, cookie: parseSetCookieHeader(header, callbackUrl.href) }))
    .filter(({ cookie }) => cookie.name === XSRF_COOKIE);
  expect(
    xsrfSetCookies.length === 2,
    'RUNTIME_XSRF_ROTATION',
    'callback OAuth2 deve emitir exatamente o tombstone e o cookie B.',
  );

  const tombstone = xsrfSetCookies[0].cookie;
  const allowedTombstoneAttributes = new Set(['expires', 'path', 'samesite']);
  expect(
    tombstone.value === ''
      && tombstone.quoted === false
      && tombstone.hostOnly
      && tombstone.domain === 'localhost'
      && tombstone.path === '/'
      && tombstone.sourceOrigin === CLIENT_ORIGIN
      && tombstone.secure === false
      && tombstone.httpOnly === false
      && tombstone.partitioned === false
      && tombstone.expired === true
      && tombstone.attributes.get('expires') === XSRF_TOMBSTONE_EXPIRES
      && tombstone.attributes.get('path') === '/'
      && String(tombstone.sameSite).toLowerCase() === 'lax'
      && [...tombstone.attributes.keys()].every(
        (name) => allowedTombstoneAttributes.has(name),
      )
      && [...allowedTombstoneAttributes].every(
        (name) => tombstone.attributes.has(name),
      ),
    'RUNTIME_XSRF_ROTATION',
    'tombstone do cookie A divergiu do contrato Spring e Tomcat aprovado.',
  );

  const cookieB = parseAndAssertXsrfSetCookie(
    [xsrfSetCookies[1].header],
    callbackUrl.href,
    false,
  );
  expect(
    typeof tokenAValue === 'string'
      && tokenAValue !== ''
      && cookieB.value !== tokenAValue,
    'RUNTIME_XSRF_ROTATION',
    'cookie B deve ser novo e diferente do cookie A.',
  );
  assertNoLaterXsrfMutation(loginHistory, callbackIndex);

  const storedCookies = clientXsrfCookies(jar);
  expect(
    storedCookies.length === 1
      && sameCookieContract(storedCookies[0], cookieB)
      && jar.getScriptReadable(XSRF_COOKIE, CLIENT_ORIGIN + '/') === storedCookies[0],
    'RUNTIME_XSRF_ROTATION',
    'cookie jar deve terminar o callback contendo somente o cookie B aprovado.',
  );
  return cookieB;
}

export function assertXsrfContinuity(response, jar, cookieB) {
  let responseUrl;
  try {
    responseUrl = new URL(response?.url);
  } catch {
    fail('RUNTIME_XSRF_CONTINUITY', 'resposta de liveness possui URL invalida.');
  }
  expect(
    responseUrl.href === CLIENT_ORIGIN + '/actuator/health/liveness'
      && response.status === 200,
    'RUNTIME_XSRF_CONTINUITY',
    'liveness deve responder 200 na URL publica aprovada.',
  );
  const livenessSetCookieHeaders = responseSetCookieHeaders(response);
  expect(
    livenessSetCookieHeaders.length <= MAX_COOKIE_COUNT,
    'RUNTIME_XSRF_CONTINUITY',
    'liveness excedeu o limite de Set-Cookie.',
  );
  const xsrfSetCookies = livenessSetCookieHeaders
    .map((header) => parseSetCookieHeader(header, responseUrl.href))
    .filter((cookie) => cookie.name === XSRF_COOKIE);
  expect(
    xsrfSetCookies.length === 0,
    'RUNTIME_XSRF_CONTINUITY',
    'liveness nao pode reemitir, remover ou substituir o cookie B.',
  );
  const storedCookies = clientXsrfCookies(jar);
  expect(
    storedCookies.length === 1
      && sameCookieContract(storedCookies[0], cookieB)
      && jar.getScriptReadable(XSRF_COOKIE, CLIENT_ORIGIN + '/') === storedCookies[0],
    'RUNTIME_XSRF_CONTINUITY',
    'cookie B deve permanecer unico e inalterado apos o liveness.',
  );
  return storedCookies[0];
}

async function exerciseHostedFlow(prepared, redisSentinel, signal) {
  const allowedOrigins = new Set([CLIENT_ORIGIN, KEYCLOAK_ORIGIN]);
  const jar = new CookieJar();
  const history = [];
  const sensitiveBrowserValues = [prepared.values.BETUFU_TECHNICAL_PASSWORD];

  const beforeLogin = await requestLocal(CLIENT_ORIGIN + '/login', {
    jar,
    allowedOrigins,
    signal,
    sensitiveBrowserValues,
    headers: {
      'X-Forwarded-For': '198.51.100.10',
      'X-Forwarded-Proto': 'https',
    },
  });
  history.push(beforeLogin);
  expect(beforeLogin.status === 200, 'RUNTIME_XSRF', 'GET seguro inicial falhou.');
  const cookieA = parseAndAssertXsrfSetCookie(
    responseSetCookieHeaders(beforeLogin),
    CLIENT_ORIGIN + '/login',
    false,
  );
  const tokenA = jar.getScriptReadable(XSRF_COOKIE, CLIENT_ORIGIN + '/');
  expect(
    tokenA?.value && tokenA.value === cookieA.value,
    'RUNTIME_XSRF',
    'cookie A nao esta legivel pelo browser no escopo aprovado.',
  );

  const trustedHttps = await requestLocal(BACKEND_ORIGIN + '/login', {
    allowedOrigins: new Set([BACKEND_ORIGIN]),
    signal,
    sensitiveBrowserValues,
    headers: {
      'X-Forwarded-For': '127.0.0.1',
      'X-Forwarded-Host': 'localhost:4200',
      'X-Forwarded-Proto': 'https',
    },
  });
  history.push(trustedHttps);
  const httpsCookie = parseAndAssertXsrfSetCookie(
    responseSetCookieHeaders(trustedHttps),
    'https://localhost:4200/login',
    true,
  );

  const authorization = await followRedirects(
    CLIENT_ORIGIN + '/oauth2/authorization/keycloak',
    { jar, allowedOrigins, signal, sensitiveBrowserValues },
  );
  history.push(...authorization.history);
  expect(
    new URL(authorization.response.url).origin === KEYCLOAK_ORIGIN
      && authorization.response.status === 200,
    'RUNTIME_LOGIN',
    'login hospedado Keycloak nao foi alcancado.',
  );
  const form = parseLoginForm(authorization.response.body, authorization.response.url);
  const issuerUrl = new URL(prepared.values.BETUFU_OIDC_ISSUER_URI);
  const formUrl = new URL(form.action);
  expect(
    formUrl.origin === issuerUrl.origin
      && formUrl.pathname === issuerUrl.pathname + '/login-actions/authenticate',
    'RUNTIME_LOGIN',
    'formulario de login saiu do issuer/action Keycloak aprovado.',
  );
  form.values.set('username', prepared.values.BETUFU_TECHNICAL_USERNAME);
  form.values.set('password', prepared.values.BETUFU_TECHNICAL_PASSWORD);
  const login = await followRedirects(form.action, {
    method: 'POST',
    body: form.values.toString(),
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    jar,
    allowedOrigins,
    sensitivePostUrl: form.action,
    signal,
    sensitiveBrowserValues,
  });
  history.push(...login.history);
  const cookieB = assertOAuthXsrfRotation(login.history, jar, tokenA.value);

  const liveness = await requestLocal(
    CLIENT_ORIGIN + '/actuator/health/liveness',
    { jar, allowedOrigins, signal, sensitiveBrowserValues },
  );
  history.push(liveness);
  const tokenB = assertXsrfContinuity(liveness, jar, cookieB);

  const putUrl = CLIENT_ORIGIN + '/api/v1/tracer/identity/current';
  const missingCookieJar = jar.clone();
  missingCookieJar.remove(XSRF_COOKIE);
  const missingCookie = await requestLocal(putUrl, {
    method: 'PUT',
    headers: { [XSRF_HEADER]: tokenB.value },
    jar: missingCookieJar,
    allowedOrigins,
    signal,
    sensitiveBrowserValues,
  });
  const missingHeader = await requestLocal(putUrl, {
    method: 'PUT',
    jar: jar.clone(),
    allowedOrigins,
    signal,
    sensitiveBrowserValues,
  });
  const divergent = await requestLocal(putUrl, {
    method: 'PUT',
    headers: { [XSRF_HEADER]: tokenA.value },
    jar: jar.clone(),
    allowedOrigins,
    signal,
    sensitiveBrowserValues,
  });
  const firstPut = await requestLocal(putUrl, {
    method: 'PUT',
    headers: { [XSRF_HEADER]: tokenB.value },
    jar,
    allowedOrigins,
    signal,
    sensitiveBrowserValues,
  });
  const secondPut = await requestLocal(putUrl, {
    method: 'PUT',
    headers: { [XSRF_HEADER]: tokenB.value },
    jar,
    allowedOrigins,
    signal,
    sensitiveBrowserValues,
  });
  history.push(missingCookie, missingHeader, divergent, firstPut, secondPut);
  const browserExposure = assertNoBrowserTokenExposure(
    history,
    jar,
    sensitiveBrowserValues,
  );

  return {
    loginFlow: 'authorization-code-hosted',
    statuses: {
      missingCookie: missingCookie.status,
      missingHeader: missingHeader.status,
      divergent: divergent.status,
      firstPut: firstPut.status,
      secondPut: secondPut.status,
    },
    identityIds: [
      readIdentityResponse(firstPut),
      readIdentityResponse(secondPut),
    ],
    csrf: {
      different: tokenA.value !== tokenB.value,
      tokenAHash: sha256Text(tokenA.value),
      tokenBHash: sha256Text(tokenB.value),
      cookieAApproved: true,
      cookieBApproved: true,
      httpCookie: {
        name: cookieA.name,
        hostOnly: cookieA.hostOnly,
        path: cookieA.path,
        httpOnly: cookieA.httpOnly,
        sameSite: cookieA.sameSite,
        secure: cookieA.secure,
      },
      httpsCookie: {
        name: httpsCookie.name,
        hostOnly: httpsCookie.hostOnly,
        path: httpsCookie.path,
        httpOnly: httpsCookie.httpOnly,
        sameSite: httpsCookie.sameSite,
        secure: httpsCookie.secure,
      },
    },
    redis: {
      connectionObserved: redisSentinel.connectionObserved,
    },
    browserExposure,
    proxyEvidence: {
      untrustedForwardedHeadersOverridden: cookieA.secure === false,
      trustedHttpsProducedSecureCookie: httpsCookie.secure === true,
    },
  };
}

export function createLocalRuntimeAdapter(projectRoot = defaultProjectRoot) {
  const requestedRoot = resolve(projectRoot);
  return {
    async prepare(expectedArtifact, signal) {
      throwIfAborted(signal);
      const root = await realpath(requestedRoot);
      const values = parseEnvironmentFile(await readRequiredText(root, PATHS.environment));
      const jarPath = await findBackendJar(root);
      const jarArtifact = await inspectJarArtifact(jarPath, signal);
      assertExpectedRuntimeArtifact(jarArtifact, expectedArtifact);
      await assertPortFree('127.0.0.1', 8080, signal);
      await assertPortFree('127.0.0.1', 4200, signal);
      const javaEnvironment = inheritedEnvironmentAllowlist();
      return {
        root,
        values,
        jarPath,
        jarArtifact,
        expectedArtifact,
        javaEnvironment,
      };
    },

    async startRedisSentinel(_prepared, signal) {
      return startRedisSentinel(signal);
    },

    async preflight(prepared, _redisSentinel, signal) {
      await probeTcp('127.0.0.1', 5432, 2_000, signal);
      const discovery = await requestLocal(
        prepared.values.BETUFU_OIDC_ISSUER_URI + '/.well-known/openid-configuration',
        { allowedOrigins: new Set([KEYCLOAK_ORIGIN]), signal },
      );
      expect(discovery.status === 200, 'RUNTIME_SERVICE', 'discovery Keycloak falhou.');
      prepared.java = await assertJava25(prepared.javaEnvironment, signal);
    },

    async startBackend(prepared, _redisSentinel, signal) {
      throwIfAborted(signal);
      const nonce = randomUUID();
      const environment = buildBackendEnvironment(
        prepared.values,
        nonce,
        prepared.javaEnvironment,
      );
      const startedAt = new Date().toISOString();
      const child = spawn('java', ['-jar', prepared.jarPath], {
        cwd: resolveContained(prepared.root, 'backend'),
        env: environment,
        shell: false,
        windowsHide: true,
        stdio: ['ignore', 'pipe', 'pipe'],
      });
      child.stdout.resume();
      child.stderr.resume();
      const spawnOutcome = new Promise((resolvePromise, rejectPromise) => {
        child.once('spawn', resolvePromise);
        child.once('error', () => {
          rejectPromise(contractError('RUNTIME_BACKEND', 'backend nao iniciou.'));
        });
      });
      return {
        child,
        nonce,
        spawnOutcome,
        startedAt,
      };
    },

    async waitForBackend(_prepared, backend, signal) {
      await waitForBackendReady(backend, signal);
    },

    async startProxy(_prepared, _backend, signal) {
      const proxy = createProxyServer();
      await listen(proxy.server, '127.0.0.1', 4200, signal);
      proxy.startedAt = new Date().toISOString();
      return proxy;
    },

    async exercise(prepared, _backend, _proxy, redisSentinel, signal) {
      return exerciseHostedFlow(prepared, redisSentinel, signal);
    },

    async finish(prepared, _backend, _proxy, _redisSentinel, signal) {
      const jarArtifact = await inspectJarArtifact(prepared.jarPath, signal);
      assertExpectedRuntimeArtifact(jarArtifact, prepared.expectedArtifact);
      return jarArtifact;
    },

    async stopProxy(proxy) {
      expect(proxy.nonce !== '', 'RUNTIME_CLEANUP', 'nonce do proxy proprio ausente.');
      for (const upstream of proxy.upstreamRequests) {
        upstream.destroy(contractError('RUNTIME_CLEANUP', 'proxy em cleanup.'));
      }
      await closeServer(proxy.server);
    },

    async stopBackend(backend) {
      await stopOwnedChild(backend);
    },

    async stopRedisSentinel(redisSentinel) {
      expect(
        redisSentinel.nonce !== '',
        'RUNTIME_CLEANUP',
        'nonce da sentinela propria ausente.',
      );
      for (const socket of redisSentinel.sockets) {
        socket.destroy();
      }
      await closeServer(redisSentinel.server);
    },
  };
}

export function parseArguments(argumentsList) {
  let runtime = false;
  let root = defaultProjectRoot;
  let expectedJarSha256;
  let expectedJarSize;
  for (let index = 0; index < argumentsList.length; index += 1) {
    const argument = argumentsList[index];
    if (argument === '--runtime' && !runtime) {
      runtime = true;
      continue;
    }
    if (argument === '--root' && index + 1 < argumentsList.length) {
      index += 1;
      root = resolve(argumentsList[index]);
      continue;
    }
    if (argument === '--expected-jar-sha256'
      && expectedJarSha256 === undefined
      && index + 1 < argumentsList.length) {
      index += 1;
      expectedJarSha256 = argumentsList[index];
      continue;
    }
    if (argument === '--expected-jar-size'
      && expectedJarSize === undefined
      && index + 1 < argumentsList.length) {
      index += 1;
      if (!/^[1-9][0-9]*$/u.test(argumentsList[index])) {
        return null;
      }
      expectedJarSize = Number(argumentsList[index]);
      continue;
    }
    return null;
  }
  if (runtime) {
    if (expectedJarSha256 !== APPROVED_JAR_SHA256
      || expectedJarSize !== APPROVED_JAR_SIZE) {
      return null;
    }
  } else if (expectedJarSha256 !== undefined || expectedJarSize !== undefined) {
    return null;
  }
  return {
    root,
    runtime,
    expectedArtifact: runtime
      ? { sha256: expectedJarSha256, size: expectedJarSize }
      : undefined,
  };
}

export function sanitizeError(error) {
  if (error instanceof RuntimePrerequisiteContractError) {
    return error.message;
  }
  return 'INTERNAL_ERROR: falha interna durante a validacao.';
}

function interruptedExitCode(error) {
  const visited = new Set();
  let current = error;
  while (current instanceof Error && !visited.has(current)) {
    visited.add(current);
    if (current.signalName === 'SIGINT') {
      return 130;
    }
    if (current.signalName === 'SIGTERM') {
      return 143;
    }
    current = current.cause;
  }
  return 1;
}

async function main() {
  const options = parseArguments(process.argv.slice(2));
  if (options === null) {
    console.error(
      'Uso: node scripts/validate-runtime-prerequisites.mjs [--root <project-root>] '
        + '[--runtime --expected-jar-sha256 <SHA256> --expected-jar-size <bytes>]',
    );
    process.exitCode = 2;
    return;
  }
  try {
    await validateStaticContract(options.root);
    if (options.runtime) {
      const evidence = await runRuntimeProof(
        options.root,
        createLocalRuntimeAdapter(options.root),
        { expectedArtifact: options.expectedArtifact },
      );
      console.log(JSON.stringify({
        status: evidence.status,
        identityUuid: evidence.identityUuid,
        jarSha256: evidence.jarSha256,
        jarSize: evidence.jarSize,
        jarHashBefore: evidence.jarHashBefore,
        jarHashAfter: evidence.jarHashAfter,
        jarSizeBefore: evidence.jarSizeBefore,
        jarSizeAfter: evidence.jarSizeAfter,
        loginFlow: evidence.loginFlow,
        statuses: evidence.statuses,
        csrf: evidence.csrf,
        redis: evidence.redis,
        browserExposure: evidence.browserExposure,
        java: evidence.java,
        ownedProcesses: evidence.ownedProcesses,
        proxyEvidence: evidence.proxyEvidence,
        cleanup: evidence.cleanup,
      }));
    } else {
      console.log(
        '[validate-runtime-prerequisites] OK: datasource, sessao dev e XSRF seguem o contrato.',
      );
    }
  } catch (error) {
    console.error('[validate-runtime-prerequisites] ERRO: ' + sanitizeError(error));
    process.exitCode = interruptedExitCode(error);
  }
}

const invokedAsScript = process.argv[1]
  && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (invokedAsScript) {
  await main();
}
