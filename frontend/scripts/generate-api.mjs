#!/usr/bin/env node

import { spawn } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';
import {
  copyFile,
  lstat,
  mkdir,
  open,
  readFile,
  readdir,
  realpath,
  rename,
  rm,
  stat,
  unlink,
  writeFile,
} from 'node:fs/promises';
import { constants as fileSystemConstants } from 'node:fs';
import {
  dirname,
  delimiter,
  isAbsolute,
  join,
  parse,
  relative,
  resolve,
  sep,
} from 'node:path';
import { isDeepStrictEqual } from 'node:util';
import { fileURLToPath } from 'node:url';

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
const defaultProjectRoot = resolve(scriptsDirectory, '..', '..');
const CONFIGURATION_FILE = 'openapitools.json';
const WORKSPACE_RELATIVE_PATH = 'tmp/openapi-generator';
const LOCK_FILE = 'pipeline.lock';
const JOURNAL_FILE = 'pipeline.journal.json';
const NONCE_PATTERN = /^[a-f0-9]{32}$/u;
const APPROVED_JAVA_RUNTIME = Object.freeze({
  specificationVersion: '25',
  version: '25.0.4',
  vendor: 'Eclipse Adoptium',
});

export const APPROVED_ENGINE = Object.freeze({
  version: '7.24.0',
  path: 'tmp/openapi-generator/7.24.0/openapi-generator-cli-7.24.0.jar',
  size: 31_904_922,
  sha256: '4B83CCC6FD43056C8C631CD0195E5100BD0550912502527BAB09AC76152DAB0C',
});

const APPROVED_GENERATOR = Object.freeze({
  name: 'typescript-angular',
  inputSpec: '../backend/openapi/betufu.yaml',
  target: 'src/app/api/generated',
  additionalProperties: Object.freeze({
    ngVersion: '22.1.0',
    tsVersion: '>=6.0.0 <6.1.0',
    rxjsVersion: '7.8.2',
    ngPackagrVersion: '22.1.0',
    zonejsVersion: '0.15.0',
    supportsES6: true,
    ngVersion21: true,
    providedIn: 'root',
    useSingleRequestParameter: true,
    fileNaming: 'kebab-case',
    modelPropertyNaming: 'original',
    disallowAdditionalPropertiesIfNotPresent: false,
    hideGenerationTimestamp: true,
  }),
});
const GENERATED_BASE_SERVICE_PATH = 'api.base.service.ts';
const GENERATED_API_MODULE_PATH = 'api.module.ts';
const GENERATED_BASE_PATH_SOURCE = "    protected basePath = 'http://localhost';";
const GENERATED_BASE_PATH_HARDENED = "    protected basePath = '';";
const GENERATED_API_MODULE_DIAGNOSTIC = [
  '        if (!http) {',
  "            throw new Error('You need to import the HttpClientModule in your AppModule! \\n' +",
  "            'See also https://github.com/angular/angular/issues/20575');",
  '        }',
].join('\n');

export const APPROVED_GENERATED_FILES = Object.freeze([
  'api.base.service.ts',
  'api.module.ts',
  'api/api.ts',
  'api/diagnostic.service.ts',
  'api/identity.service.ts',
  'api/profile.service.ts',
  'api/session.service.ts',
  'configuration.ts',
  'encoder.ts',
  'index.ts',
  'model/current-device-logout-response.ts',
  'model/current-identity-response.ts',
  'model/current-profile-response.ts',
  'model/diagnostic-component.ts',
  'model/diagnostic-response.ts',
  'model/diagnostic-status.ts',
  'model/models.ts',
  'model/profile-update-request.ts',
  'model/session-state-response.ts',
  'param.ts',
  'provide-api.ts',
  'query.params.ts',
  'variables.ts',
]);
export const APPROVED_GENERATED_DIGEST = '0D73D00945E6E573441857114D43E08047C059BA91F380A06D08F4F7C080E220';
const APPROVED_GENERATED_MANIFEST = Object.freeze({
  files: APPROVED_GENERATED_FILES,
  digest: APPROVED_GENERATED_DIGEST,
});

export class GenerateApiContractError extends Error {
  constructor(rule, message) {
    super(`${rule}: ${message}`);
    this.name = 'GenerateApiContractError';
    this.rule = rule;
  }
}

function fail(rule, message) {
  throw new GenerateApiContractError(rule, message);
}

function isMissing(error) {
  return error?.code === 'ENOENT';
}

function toPosixPath(path) {
  return path.split(sep).join('/');
}

function isContained(parent, child) {
  const pathFromParent = relative(parent, child);
  return pathFromParent === ''
    || (!pathFromParent.startsWith(`..${sep}`) && pathFromParent !== '..' && !isAbsolute(pathFromParent));
}

function resolveSafeRelative(base, value, rule = 'UNSAFE_PATH') {
  if (typeof value !== 'string' || value.length === 0 || value.includes('\0')) {
    fail(rule, 'caminho relativo invalido.');
  }

  const normalized = value.replaceAll('\\', '/');
  const segments = normalized.split('/');
  if (
    isAbsolute(value)
    || /^[a-zA-Z]:/u.test(value)
    || normalized.startsWith('//')
    || segments.some((segment) => segment === '' || segment === '.' || segment === '..')
  ) {
    fail(rule, 'caminho fora do escopo permitido.');
  }

  const absolutePath = resolve(base, ...segments);
  if (!isContained(base, absolutePath)) {
    fail(rule, 'caminho fora do escopo permitido.');
  }
  return absolutePath;
}

async function assertNoLinks(trustedRoot, target) {
  if (!isContained(trustedRoot, target)) {
    fail('UNSAFE_PATH', 'caminho fora da raiz confiavel.');
  }

  const pathFromRoot = relative(trustedRoot, target);
  if (pathFromRoot === '') {
    return;
  }

  let current = trustedRoot;
  for (const segment of pathFromRoot.split(sep)) {
    current = join(current, segment);
    try {
      const metadata = await lstat(current);
      if (metadata.isSymbolicLink()) {
        fail('UNSAFE_LINK', 'link simbolico ou junction nao permitido.');
      }
    } catch (error) {
      if (isMissing(error)) {
        return;
      }
      throw error;
    }
  }
}

async function pathExists(path) {
  try {
    await lstat(path);
    return true;
  } catch (error) {
    if (isMissing(error)) {
      return false;
    }
    throw error;
  }
}

async function removeKnownTree(workspace, path) {
  if (!isContained(workspace, path) || path === workspace) {
    fail('UNSAFE_PATH', 'limpeza recusada fora da area temporaria.');
  }
  await rm(path, { recursive: true, force: true });
}

function parseJson(contents, rule) {
  try {
    return JSON.parse(contents);
  } catch {
    fail(rule, 'JSON invalido.');
  }
}

async function readJson(path, rule) {
  return parseJson(await readFile(path, 'utf8'), rule);
}

async function writeJson(path, value) {
  await writeFile(path, `${JSON.stringify(value, null, 2)}\n`, {
    encoding: 'utf8',
    flag: 'w',
  });
}

async function writeJsonAtomically(path, value, nonce) {
  const temporaryPath = `${path}.${nonce}.tmp`;
  const handle = await open(temporaryPath, 'wx', 0o600);
  try {
    await handle.writeFile(`${JSON.stringify(value, null, 2)}\n`, 'utf8');
  } finally {
    await handle.close();
  }
  try {
    await rename(temporaryPath, path);
  } catch (error) {
    await rm(temporaryPath, { force: true });
    throw error;
  }
}

function assertExactConfiguration(configuration, contract) {
  const expected = {
    engine: { ...contract },
    generator: {
      ...APPROVED_GENERATOR,
      additionalProperties: { ...APPROVED_GENERATOR.additionalProperties },
    },
  };
  if (!isDeepStrictEqual(configuration, expected)) {
    fail('CONFIGURATION_DRIFT', 'openapitools.json diverge do contrato aprovado.');
  }
}

async function loadConfiguration(projectRoot, contract) {
  const frontendRoot = resolve(projectRoot, 'frontend');
  const configurationPath = join(frontendRoot, CONFIGURATION_FILE);
  await assertNoLinks(projectRoot, configurationPath);

  let configuration;
  try {
    configuration = await readJson(configurationPath, 'CONFIGURATION_INVALID');
  } catch (error) {
    if (isMissing(error)) {
      fail('CONFIGURATION_MISSING', 'openapitools.json nao encontrado.');
    }
    throw error;
  }
  assertExactConfiguration(configuration, contract);

  const enginePath = resolveSafeRelative(frontendRoot, configuration.engine.path);
  const target = resolveSafeRelative(frontendRoot, configuration.generator.target);
  const workspace = resolveSafeRelative(frontendRoot, WORKSPACE_RELATIVE_PATH);
  const inputSpec = resolve(frontendRoot, configuration.generator.inputSpec);
  if (!isContained(projectRoot, inputSpec)) {
    fail('UNSAFE_PATH', 'especificacao OpenAPI fora do projeto.');
  }

  for (const path of [enginePath, target, workspace, inputSpec]) {
    await assertNoLinks(projectRoot, path);
  }

  return {
    configuration,
    configurationPath,
    enginePath,
    frontendRoot,
    inputSpec,
    target,
    workspace,
  };
}

async function verifyRegularFile(path, missingRule, invalidRule) {
  let metadata;
  try {
    metadata = await lstat(path);
  } catch (error) {
    if (isMissing(error)) {
      fail(missingRule, 'arquivo obrigatorio ausente.');
    }
    throw error;
  }
  if (metadata.isSymbolicLink() || !metadata.isFile()) {
    fail(invalidRule, 'artefato deve ser arquivo regular.');
  }
  return metadata;
}

async function verifyEngine(enginePath, contract) {
  const metadata = await verifyRegularFile(enginePath, 'ENGINE_MISSING', 'ENGINE_INVALID');
  if (metadata.size !== contract.size) {
    fail('ENGINE_SIZE', 'tamanho do motor diverge do aprovado.');
  }
  const bytes = await readFile(enginePath);
  const digest = createHash('sha256').update(bytes).digest('hex').toUpperCase();
  if (digest !== contract.sha256) {
    fail('ENGINE_HASH', 'SHA-256 do motor diverge do aprovado.');
  }
  return digest;
}

export function normalizeGeneratedText(contents) {
  if (typeof contents !== 'string') {
    fail('GENERATED_ENCODING', 'arquivo gerado nao e texto UTF-8 valido.');
  }
  if (contents.includes('\0')) {
    fail('GENERATED_NUL', 'arquivo gerado contem byte NUL.');
  }
  const normalized = contents
    .replaceAll('\r\n', '\n')
    .replaceAll('\r', '\n')
    .replaceAll('\u2028', '\n')
    .replaceAll('\u2029', '\n')
    .replace(/\n*$/u, '');
  return `${normalized}\n`;
}

function maskCommentsAndLiterals(source) {
  let masked = '';
  let state = 'code';
  let quote = null;

  for (let index = 0; index < source.length; index += 1) {
    const current = source[index];
    const next = source[index + 1];

    if (state === 'line-comment') {
      if (current === '\n') {
        masked += '\n';
        state = 'code';
      } else {
        masked += ' ';
      }
      continue;
    }
    if (state === 'block-comment') {
      if (current === '*' && next === '/') {
        masked += '  ';
        index += 1;
        state = 'code';
      } else {
        masked += current === '\n' ? '\n' : ' ';
      }
      continue;
    }
    if (state === 'literal') {
      if (current === '\\' && next !== undefined) {
        masked += '  ';
        index += 1;
      } else if (current === quote) {
        masked += current;
        state = 'code';
        quote = null;
      } else {
        masked += current === '\n' ? '\n' : ' ';
      }
      continue;
    }
    if (current === '/' && next === '/') {
      masked += '  ';
      index += 1;
      state = 'line-comment';
      continue;
    }
    if (current === '/' && next === '*') {
      masked += '  ';
      index += 1;
      state = 'block-comment';
      continue;
    }
    if (current === "'" || current === '"' || current.codePointAt(0) === 96) {
      masked += current;
      state = 'literal';
      quote = current;
      continue;
    }
    masked += current;
  }

  return masked;
}

function stripComments(source) {
  let stripped = '';
  let state = 'code';
  let quote = null;

  for (let index = 0; index < source.length; index += 1) {
    const current = source[index];
    const next = source[index + 1];

    if (state === 'line-comment') {
      if (current === '\n') {
        stripped += '\n';
        state = 'code';
      } else {
        stripped += ' ';
      }
      continue;
    }
    if (state === 'block-comment') {
      if (current === '*' && next === '/') {
        stripped += '  ';
        index += 1;
        state = 'code';
      } else {
        stripped += current === '\n' ? '\n' : ' ';
      }
      continue;
    }
    if (state === 'literal') {
      stripped += current;
      if (current === '\\' && next !== undefined) {
        stripped += next;
        index += 1;
      } else if (current === quote) {
        state = 'code';
        quote = null;
      }
      continue;
    }
    if (current === '/' && next === '/') {
      stripped += '  ';
      index += 1;
      state = 'line-comment';
      continue;
    }
    if (current === '/' && next === '*') {
      stripped += '  ';
      index += 1;
      state = 'block-comment';
      continue;
    }
    if (current === "'" || current === '"' || current.codePointAt(0) === 96) {
      stripped += current;
      state = 'literal';
      quote = current;
      continue;
    }
    stripped += current;
  }

  return stripped;
}

function decodeUrlEscapes(source) {
  return source
    .replace(
      /\\x([0-9a-f]{2})/giu,
      function decodeHexByte(_, hex) {
        return String.fromCodePoint(Number.parseInt(hex, 16));
      },
    )
    .replace(
      /\\u\{([0-9a-f]{1,6})\}/giu,
      function decodeUnicodeCodePoint(_, hex) {
        return String.fromCodePoint(Number.parseInt(hex, 16));
      },
    )
    .replace(
      /\\u([0-9a-f]{4})/giu,
      function decodeUnicodeUnit(_, hex) {
        return String.fromCodePoint(Number.parseInt(hex, 16));
      },
    )
    .replace(/\\\//gu, '/');
}

export function validateGeneratedTypeScript(relativePath, contents) {
  let activeSource = stripComments(normalizeGeneratedText(contents));
  if (relativePath === GENERATED_API_MODULE_PATH) {
    const occurrences = activeSource.split(GENERATED_API_MODULE_DIAGNOSTIC).length - 1;
    if (occurrences !== 1) {
      fail(
        'GENERATED_ABSOLUTE_URL',
        'ApiModule deve manter somente a URL diagnostica canonica do gerador.',
      );
    }
    activeSource = activeSource.replace(GENERATED_API_MODULE_DIAGNOSTIC, '');
  }

  const comparableSource = decodeUrlEscapes(activeSource);
  if (/(?::\/\/|['"\x60]\/\/)/iu.test(comparableSource)) {
    fail(
      'GENERATED_ABSOLUTE_URL',
      'saida gerada contem URL HTTP(S) ou network-path fora da excecao diagnostica.',
    );
  }
  return normalizeGeneratedText(contents);
}

export function hardenGeneratedTypeScript(relativePath, contents) {
  const normalized = normalizeGeneratedText(contents);
  if (relativePath !== GENERATED_BASE_SERVICE_PATH) {
    return normalized;
  }

  const executable = maskCommentsAndLiterals(normalized);
  const declarations = executable.match(/\bprotected[ \t]+basePath[ \t]*=/gu) ?? [];
  const baseServiceDeclarations = executable.match(/\bclass[ \t]+BaseService\b/gu) ?? [];
  const sourceOccurrences = [
    ...executable.matchAll(
      /^export class BaseService \{\n    protected basePath = '[ \t]*';$/gmu,
    ),
  ].filter((match) => (
    normalized.slice(
      match.index + 'export class BaseService {\n'.length,
      match.index + match[0].length,
    ) === GENERATED_BASE_PATH_SOURCE
  ));

  if (
    declarations.length !== 1
    || baseServiceDeclarations.length !== 1
    || sourceOccurrences.length !== 1
  ) {
    fail(
      'GENERATED_BASE_PATH',
      'BaseService gerado deve conter exatamente um fallback basePath canonico conhecido.',
    );
  }

  const sourceIndex = sourceOccurrences[0].index + 'export class BaseService {\n'.length;
  return normalized.slice(0, sourceIndex)
    + GENERATED_BASE_PATH_HARDENED
    + normalized.slice(sourceIndex + GENERATED_BASE_PATH_SOURCE.length);
}

async function collectFiles(root, current, entries) {
  const children = await readdir(current, { withFileTypes: true });
  children.sort((left, right) => left.name.localeCompare(right.name, 'en'));

  for (const child of children) {
    const absolutePath = join(current, child.name);
    const relativePath = toPosixPath(relative(root, absolutePath));
    const metadata = await lstat(absolutePath);
    if (metadata.isSymbolicLink()) {
      fail('UNSAFE_LINK', 'link simbolico ou junction na arvore gerada.');
    }
    if (metadata.isDirectory()) {
      await collectFiles(root, absolutePath, entries);
      continue;
    }
    if (!metadata.isFile()) {
      fail('GENERATED_ENTRY', 'entrada nao regular na arvore gerada.');
    }
    entries.push({ path: relativePath, bytes: await readFile(absolutePath) });
  }
}

function digestEntries(entries) {
  const hash = createHash('sha256');
  for (const entry of entries) {
    hash.update(Buffer.from(entry.path, 'utf8'));
    hash.update(Buffer.from([0]));
    hash.update(Buffer.from(String(entry.bytes.length), 'ascii'));
    hash.update(Buffer.from([0]));
    hash.update(entry.bytes);
    hash.update(Buffer.from([0]));
  }
  return hash.digest('hex').toUpperCase();
}

export async function readTreeManifest(root) {
  const metadata = await lstat(root);
  if (metadata.isSymbolicLink()) {
    fail('UNSAFE_LINK', 'link simbolico ou junction na raiz gerada.');
  }
  if (!metadata.isDirectory()) {
    fail('GENERATED_ENTRY', 'arvore gerada nao e diretorio.');
  }

  const entries = [];
  await collectFiles(root, root, entries);
  entries.sort((left, right) => left.path.localeCompare(right.path, 'en'));
  return {
    digest: digestEntries(entries),
    entries,
    files: entries.map((entry) => entry.path),
  };
}

export function assertApprovedGeneratedManifest(
  manifest,
  contract = APPROVED_GENERATED_MANIFEST,
) {
  if (
    !isDeepStrictEqual(manifest?.files, contract?.files)
    || manifest?.digest !== contract?.digest
  ) {
    fail(
      'GENERATED_MANIFEST',
      'arvore TypeScript gerada diverge da allowlist ou do digest aprovado.',
    );
  }
  return true;
}

function defaultProcessStatus(pid) {
  if (!Number.isSafeInteger(pid) || pid <= 0) {
    return 'ambiguous';
  }
  try {
    process.kill(pid, 0);
    return 'alive';
  } catch (error) {
    return error?.code === 'ESRCH' ? 'dead' : 'ambiguous';
  }
}

async function defaultVolumeOf(path) {
  let current = resolve(path);
  while (true) {
    try {
      return (await stat(current)).dev;
    } catch (error) {
      if (!isMissing(error)) {
        throw error;
      }
      const parent = dirname(current);
      if (parent === current) {
        throw error;
      }
      current = parent;
    }
  }
}

function createDefaultAdapters() {
  return {
    beforeRename: async () => {},
    generate: runOpenApiGenerator,
    nonce: () => randomBytes(16).toString('hex'),
    now: () => new Date().toISOString(),
    pid: process.pid,
    processStatus: defaultProcessStatus,
    volumeOf: defaultVolumeOf,
  };
}

function validateNonce(nonce) {
  if (typeof nonce !== 'string' || !NONCE_PATTERN.test(nonce)) {
    fail('NONCE_INVALID', 'identificador de operacao invalido.');
  }
}

function validateLock(value) {
  if (
    value?.version !== 1
    || !Number.isSafeInteger(value.pid)
    || value.pid <= 0
    || typeof value.createdAt !== 'string'
    || !NONCE_PATTERN.test(value.nonce ?? '')
  ) {
    fail('LOCK_AMBIGUOUS', 'lock existente nao pode ser validado.');
  }
  return value;
}

async function writeExclusiveJson(path, value) {
  const handle = await open(path, 'wx', 0o600);
  try {
    await handle.writeFile(`${JSON.stringify(value)}\n`, 'utf8');
  } finally {
    await handle.close();
  }
}

async function installLockDirectory(workspace, lockPath, lockValue) {
  const preparedPath = join(workspace, `.pipeline.lock.prepare-${lockValue.nonce}`);
  try {
    await mkdir(preparedPath, { recursive: false, mode: 0o700 });
    await writeExclusiveJson(join(preparedPath, 'owner.json'), lockValue);
    try {
      await rename(preparedPath, lockPath);
      return true;
    } catch (error) {
      if (!['EEXIST', 'ENOTEMPTY', 'EPERM'].includes(error?.code)) {
        throw error;
      }
      return false;
    }
  } finally {
    if (await pathExists(preparedPath)) {
      await removeKnownTree(workspace, preparedPath);
    }
  }
}

async function readLockOwner(lockPath) {
  try {
    const metadata = await lstat(lockPath);
    if (metadata.isSymbolicLink() || !metadata.isDirectory()) {
      fail('LOCK_AMBIGUOUS', 'lock deve ser um diretorio regular controlado.');
    }
    return validateLock(await readJson(join(lockPath, 'owner.json'), 'LOCK_AMBIGUOUS'));
  } catch (error) {
    if (error instanceof GenerateApiContractError) {
      throw error;
    }
    fail('LOCK_AMBIGUOUS', 'lock existente nao pode ser lido.');
  }
}

async function processStatus(adapters, lockValue) {
  try {
    const status = await adapters.processStatus(lockValue.pid);
    return ['alive', 'dead', 'ambiguous'].includes(status) ? status : 'ambiguous';
  } catch {
    return 'ambiguous';
  }
}

async function releaseRecoveryMarker(lockPath, markerValue) {
  const recoveryPath = join(lockPath, 'recovery.lock');
  let current;
  try {
    current = validateLock(await readJson(recoveryPath, 'LOCK_AMBIGUOUS'));
  } catch (error) {
    if (isMissing(error)) {
      return;
    }
    throw error;
  }
  if (!isDeepStrictEqual(current, markerValue)) {
    fail('LOCK_AMBIGUOUS', 'marcador de recovery nao pertence a esta tentativa.');
  }
  await unlink(recoveryPath);
}

async function acquireRecoveryMarker(lockPath, adapters, markerValue) {
  const recoveryPath = join(lockPath, 'recovery.lock');
  try {
    await writeExclusiveJson(recoveryPath, markerValue);
    return;
  } catch (error) {
    if (error?.code !== 'EEXIST') {
      fail('LOCK_AMBIGUOUS', 'nao foi possivel criar o marcador de recovery.');
    }
  }

  const existing = validateLock(await readJson(recoveryPath, 'LOCK_AMBIGUOUS'));
  const status = await processStatus(adapters, existing);
  if (status === 'alive') {
    fail('LOCK_LIVE', 'outro processo recupera o lock morto.');
  }
  if (status !== 'dead') {
    fail('LOCK_AMBIGUOUS', 'estado do recuperador anterior e incerto.');
  }

  const tombstone = join(lockPath, `recovery.dead-${existing.nonce}-${markerValue.nonce}`);
  try {
    await rename(recoveryPath, tombstone);
  } catch {
    fail('LOCK_AMBIGUOUS', 'marcador de recovery mudou durante a eleicao.');
  }
  const moved = validateLock(await readJson(tombstone, 'LOCK_AMBIGUOUS'));
  if (!isDeepStrictEqual(moved, existing)) {
    fail('LOCK_AMBIGUOUS', 'marcador de recovery movido diverge do verificado.');
  }
  try {
    await writeExclusiveJson(recoveryPath, markerValue);
  } catch {
    await rm(tombstone, { force: true });
    fail('LOCK_AMBIGUOUS', 'outro processo venceu a recuperacao do marcador.');
  }
  await rm(tombstone, { force: true });
}

async function acquireLock(workspace, adapters, nonce) {
  const lockPath = join(workspace, LOCK_FILE);
  const lockValue = {
    version: 1,
    pid: adapters.pid,
    nonce,
    createdAt: adapters.now(),
  };
  if (await installLockDirectory(workspace, lockPath, lockValue)) {
    return { lockPath, lockValue };
  }

  const existing = await readLockOwner(lockPath);
  const status = await processStatus(adapters, existing);
  if (status === 'alive') {
    fail('LOCK_LIVE', 'outro processo controla a geracao.');
  }
  if (status !== 'dead') {
    fail('LOCK_AMBIGUOUS', 'estado do processo do lock e incerto.');
  }

  const recoveryValue = {
    version: 1,
    pid: adapters.pid,
    nonce,
    createdAt: adapters.now(),
  };
  await acquireRecoveryMarker(lockPath, adapters, recoveryValue);

  let current;
  try {
    current = await readLockOwner(lockPath);
    if (!isDeepStrictEqual(current, existing)) {
      fail('LOCK_AMBIGUOUS', 'lock mudou depois da verificacao de processo.');
    }
    if (await processStatus(adapters, current) !== 'dead') {
      fail('LOCK_AMBIGUOUS', 'dono do lock deixou de estar comprovadamente morto.');
    }
  } catch (error) {
    await releaseRecoveryMarker(lockPath, recoveryValue);
    throw error;
  }

  const tombstone = join(workspace, `pipeline.lock.dead-${existing.nonce}-${nonce}`);
  try {
    await rename(lockPath, tombstone);
  } catch {
    await releaseRecoveryMarker(lockPath, recoveryValue);
    fail('LOCK_AMBIGUOUS', 'lock mudou durante a recuperacao.');
  }

  const movedValue = await readLockOwner(tombstone);
  if (!isDeepStrictEqual(movedValue, existing)) {
    fail('LOCK_AMBIGUOUS', 'lock mudou depois da verificacao de processo.');
  }

  const installed = await installLockDirectory(workspace, lockPath, lockValue);
  await removeKnownTree(workspace, tombstone);
  if (!installed) {
    fail('LOCK_AMBIGUOUS', 'outro processo adquiriu o lock durante a recuperacao.');
  }
  return { lockPath, lockValue };
}

async function releaseLock(lockPath, lockValue) {
  const releasePath = `${lockPath}.release-${lockValue.nonce}`;
  try {
    await rename(lockPath, releasePath);
  } catch (error) {
    if (isMissing(error)) {
      fail('LOCK_OWNERSHIP', 'lock desapareceu antes da liberacao.');
    }
    fail('LOCK_OWNERSHIP', 'lock nao pode ser isolado para liberacao.');
  }

  const current = await readLockOwner(releasePath);
  if (!isDeepStrictEqual(current, lockValue)) {
    fail('LOCK_OWNERSHIP', 'lock nao pertence a esta operacao.');
  }
  await rm(releasePath, { recursive: true, force: false });
}

function journalPaths(frontendRoot, workspace, configuration, journal) {
  if (
    journal?.version !== 1
    || !NONCE_PATTERN.test(journal.nonce ?? '')
    || !['prepared', 'old-moved', 'new-installed'].includes(journal.state)
    || typeof journal.hadTarget !== 'boolean'
    || typeof journal.createdAt !== 'string'
  ) {
    fail('JOURNAL_INVALID', 'journal invalido.');
  }

  const expectedOperation = `${WORKSPACE_RELATIVE_PATH}/operations/${journal.nonce}`;
  const expectedCandidate = `${expectedOperation}/candidate`;
  const expectedBackup = `${expectedOperation}/backup`;
  if (
    journal.operation !== expectedOperation
    || journal.candidate !== expectedCandidate
    || journal.backup !== expectedBackup
    || journal.target !== configuration.generator.target
  ) {
    fail('UNSAFE_PATH', 'journal referencia caminho nao aprovado.');
  }

  const operation = resolveSafeRelative(frontendRoot, journal.operation);
  const candidate = resolveSafeRelative(frontendRoot, journal.candidate);
  const backup = resolveSafeRelative(frontendRoot, journal.backup);
  const target = resolveSafeRelative(frontendRoot, journal.target);
  if (!isContained(workspace, operation) || operation === workspace) {
    fail('UNSAFE_PATH', 'operacao do journal fora da area temporaria.');
  }
  return { backup, candidate, operation, target };
}

async function recoverJournal(context) {
  const { configuration, frontendRoot, journalPath, projectRoot, workspace } = context;
  const journal = await readJson(journalPath, 'JOURNAL_INVALID');
  const paths = journalPaths(frontendRoot, workspace, configuration, journal);
  for (const path of Object.values(paths)) {
    await assertNoLinks(projectRoot, path);
  }

  const targetExists = await pathExists(paths.target);
  const backupExists = await pathExists(paths.backup);
  const candidateExists = await pathExists(paths.candidate);
  const operationExists = await pathExists(paths.operation);

  if (!operationExists) {
    const priorTreeRestored = ['prepared', 'old-moved'].includes(journal.state)
      && targetExists === journal.hadTarget;
    const newTreeCommitted = journal.state === 'new-installed' && targetExists;
    if (backupExists || candidateExists || (!priorTreeRestored && !newTreeCommitted)) {
      fail('RECOVERY_AMBIGUOUS', 'journal sem operacao nao corresponde a um recovery concluido.');
    }
    if (targetExists) {
      await readTreeManifest(paths.target);
    }
    await unlink(journalPath);
    return;
  }

  if (journal.state === 'prepared') {
    if (journal.hadTarget && targetExists && !backupExists && candidateExists) {
      await readTreeManifest(paths.target);
      await readTreeManifest(paths.candidate);
    } else if (journal.hadTarget && !targetExists && backupExists && candidateExists) {
      await readTreeManifest(paths.backup);
      await readTreeManifest(paths.candidate);
      await mkdir(dirname(paths.target), { recursive: true });
      await rename(paths.backup, paths.target);
    } else if (!journal.hadTarget && !targetExists && !backupExists && candidateExists) {
      await readTreeManifest(paths.candidate);
    } else if (!journal.hadTarget && targetExists && !backupExists && !candidateExists) {
      await readTreeManifest(paths.target);
      await rm(paths.target, { recursive: true, force: false });
    } else if (!journal.hadTarget && !targetExists && !backupExists && !candidateExists) {
      // Rollback sem arvore anterior ja concluido; resta somente limpar a operacao.
    } else if (journal.hadTarget && targetExists && !backupExists && !candidateExists) {
      await readTreeManifest(paths.target);
    } else {
      fail('RECOVERY_AMBIGUOUS', 'estado prepared nao pode ser restaurado com seguranca.');
    }
    await removeKnownTree(workspace, paths.operation);
  } else if (journal.state === 'old-moved') {
    if (!journal.hadTarget) {
      fail('RECOVERY_AMBIGUOUS', 'estado old-moved exige uma arvore anterior.');
    }
    if (!targetExists && backupExists && candidateExists) {
      await readTreeManifest(paths.backup);
      await readTreeManifest(paths.candidate);
      await mkdir(dirname(paths.target), { recursive: true });
      await rename(paths.backup, paths.target);
    } else if (targetExists && backupExists && !candidateExists) {
      await readTreeManifest(paths.target);
      await readTreeManifest(paths.backup);
      await rm(paths.target, { recursive: true, force: false });
      await rename(paths.backup, paths.target);
    } else if (targetExists && !backupExists) {
      await readTreeManifest(paths.target);
      if (candidateExists) {
        await readTreeManifest(paths.candidate);
      }
    } else if (!targetExists && backupExists && !candidateExists) {
      await readTreeManifest(paths.backup);
      await mkdir(dirname(paths.target), { recursive: true });
      await rename(paths.backup, paths.target);
    } else {
      fail('RECOVERY_AMBIGUOUS', 'estado old-moved nao pode ser restaurado com seguranca.');
    }
    await removeKnownTree(workspace, paths.operation);
  } else {
    if (
      !targetExists
      || candidateExists
      || (!journal.hadTarget && backupExists)
    ) {
      fail('RECOVERY_AMBIGUOUS', 'estado new-installed nao pode ser finalizado com seguranca.');
    }
    await readTreeManifest(paths.target);
    if (backupExists) {
      await readTreeManifest(paths.backup);
      await removeKnownTree(workspace, paths.backup);
    }
    await removeKnownTree(workspace, paths.operation);
  }
  await unlink(journalPath);
}

function safeJavaEnvironment() {
  const blockedJavaEnvironment = new Set([
    '_JAVA_OPTIONS',
    'CLASSPATH',
    'JAVA_TOOL_OPTIONS',
    'JDK_JAVA_OPTIONS',
  ]);
  return Object.fromEntries(
    Object.entries(process.env).filter(([name]) => !blockedJavaEnvironment.has(name.toUpperCase())),
  );
}

async function resolveJavaExecutable(environment) {
  const pathValue = Object.entries(environment)
    .find(([name]) => name.toUpperCase() === 'PATH')?.[1];
  if (typeof pathValue !== 'string' || pathValue.length === 0) {
    fail('JAVA_MISSING', 'PATH nao permite resolver um launcher Java aprovado.');
  }
  const executableName = process.platform === 'win32' ? 'java.exe' : 'java';
  for (const pathEntry of pathValue.split(delimiter)) {
    if (pathEntry.length === 0) {
      continue;
    }
    const candidate = resolve(pathEntry, executableName);
    try {
      const metadata = await lstat(candidate);
      if (!metadata.isFile() && !metadata.isSymbolicLink()) {
        continue;
      }
      const canonicalCandidate = await realpath(candidate);
      const canonicalMetadata = await lstat(canonicalCandidate);
      if (canonicalMetadata.isSymbolicLink() || !canonicalMetadata.isFile()) {
        fail('JAVA_UNSAFE', 'destino canonico do launcher Java nao e arquivo regular.');
      }
      return canonicalCandidate;
    } catch (error) {
      if (!isMissing(error)) {
        throw error;
      }
    }
  }
  fail('JAVA_MISSING', 'launcher Java nao encontrado no PATH.');
}

async function captureProcess(executable, argumentsList, options) {
  return new Promise((accept, reject) => {
    const child = spawn(executable, argumentsList, {
      ...options,
      shell: false,
      stdio: ['ignore', 'pipe', 'pipe'],
      windowsHide: true,
    });
    const chunks = [];
    let length = 0;
    const capture = (chunk) => {
      length += chunk.length;
      if (length > 256 * 1024) {
        child.kill();
        reject(new Error('saida do preflight Java excedeu o limite.'));
        return;
      }
      chunks.push(chunk);
    };
    child.stdout.on('data', capture);
    child.stderr.on('data', capture);
    child.once('error', reject);
    child.once('exit', (code, signal) => {
      if (code === 0 && signal === null) {
        accept(Buffer.concat(chunks).toString('utf8'));
      } else {
        reject(new Error('preflight Java terminou com falha.'));
      }
    });
  });
}

function assertApprovedJavaRuntime(output) {
  const properties = new Map();
  for (const line of output.split(/\r?\n/u)) {
    const match = /^\s*([a-zA-Z0-9._-]+)\s*=\s*(.*?)\s*$/u.exec(line);
    if (match) {
      properties.set(match[1], match[2]);
    }
  }
  if (
    properties.get('java.specification.version') !== APPROVED_JAVA_RUNTIME.specificationVersion
    || properties.get('java.version') !== APPROVED_JAVA_RUNTIME.version
    || properties.get('java.vendor') !== APPROVED_JAVA_RUNTIME.vendor
  ) {
    fail('JAVA_RUNTIME_DRIFT', 'runtime Java diverge da evidencia aceita no Gate.');
  }
}

async function runOpenApiGenerator({ configuration, enginePath, frontendRoot, inputSpec, outputDirectory }) {
  const properties = Object.entries(configuration.generator.additionalProperties)
    .map(([key, value]) => `${key}=${String(value)}`)
    .join(',');
  const argumentsList = [
    '-jar',
    enginePath,
    'generate',
    '--generator-name',
    configuration.generator.name,
    '--input-spec',
    inputSpec,
    '--output',
    outputDirectory,
    '--additional-properties',
    properties,
  ];

  const javaEnvironment = safeJavaEnvironment();
  const javaExecutable = await resolveJavaExecutable(javaEnvironment);
  const preflightOutput = await captureProcess(
    javaExecutable,
    ['-XshowSettings:properties', '-version'],
    { cwd: frontendRoot, env: javaEnvironment },
  );
  assertApprovedJavaRuntime(preflightOutput);

  await new Promise((accept, reject) => {
    const child = spawn(javaExecutable, argumentsList, {
      cwd: frontendRoot,
      env: javaEnvironment,
      shell: false,
      stdio: ['ignore', 'ignore', 'ignore'],
      windowsHide: true,
    });
    child.once('error', reject);
    child.once('exit', (code, signal) => {
      if (code === 0 && signal === null) {
        accept();
      } else {
        reject(new Error('OpenAPI Generator terminou com falha.'));
      }
    });
  });
}

async function materializeTypeScript(rawDirectory, candidate) {
  await mkdir(candidate, { recursive: false });
  const decoder = new TextDecoder('utf-8', { fatal: true });
  let count = 0;
  let baseServiceCount = 0;

  async function visit(current) {
    const children = await readdir(current, { withFileTypes: true });
    children.sort((left, right) => left.name.localeCompare(right.name, 'en'));
    for (const child of children) {
      const source = join(current, child.name);
      const metadata = await lstat(source);
      if (metadata.isSymbolicLink()) {
        fail('UNSAFE_LINK', 'link simbolico na saida bruta do gerador.');
      }
      if (metadata.isDirectory()) {
        await visit(source);
        continue;
      }
      if (!metadata.isFile()) {
        fail('GENERATED_ENTRY', 'entrada nao regular na saida do gerador.');
      }
      if (!child.name.endsWith('.ts')) {
        continue;
      }

      const relativePath = relative(rawDirectory, source);
      const portableRelativePath = toPosixPath(relativePath);
      const destination = resolve(candidate, relativePath);
      if (!isContained(candidate, destination)) {
        fail('UNSAFE_PATH', 'arquivo gerado escapou da arvore candidata.');
      }
      let text;
      try {
        text = decoder.decode(await readFile(source));
      } catch {
        fail('GENERATED_ENCODING', 'arquivo gerado nao e UTF-8 valido.');
      }
      await mkdir(dirname(destination), { recursive: true });
      const hardenedText = hardenGeneratedTypeScript(portableRelativePath, text);
      const validatedText = validateGeneratedTypeScript(portableRelativePath, hardenedText);
      await writeFile(destination, validatedText, 'utf8');
      if (portableRelativePath === GENERATED_BASE_SERVICE_PATH) {
        baseServiceCount += 1;
      }
      count += 1;
    }
  }

  await visit(rawDirectory);
  if (count === 0) {
    fail('GENERATED_EMPTY', 'gerador nao produziu arquivos TypeScript.');
  }
  if (baseServiceCount !== 1) {
    fail(
      'GENERATED_BASE_PATH',
      'saida gerada deve conter exatamente um api.base.service.ts canonico.',
    );
  }
}

function manifestsEqual(left, right) {
  if (left.entries.length !== right.entries.length) {
    return false;
  }
  return left.entries.every((entry, index) => (
    entry.path === right.entries[index].path
    && entry.bytes.equals(right.entries[index].bytes)
  ));
}

async function compareWithTarget(candidateManifest, target) {
  let targetManifest;
  try {
    targetManifest = await readTreeManifest(target);
  } catch (error) {
    if (isMissing(error)) {
      fail('GENERATED_DRIFT', 'arvore versionada esta ausente.');
    }
    throw error;
  }
  if (!manifestsEqual(candidateManifest, targetManifest)) {
    fail('GENERATED_DRIFT', 'arvore versionada diverge da geracao deterministica.');
  }
}

async function preservePublishDiagnostic(workspace, nonce, now) {
  const diagnostics = join(workspace, 'diagnostics');
  await assertNoLinks(workspace, diagnostics);
  await mkdir(diagnostics, { recursive: true });
  await assertNoLinks(workspace, diagnostics);
  const path = join(diagnostics, `publish-failure-${nonce}.json`);
  const handle = await open(path, 'wx', 0o600);
  try {
    await handle.writeFile(`${JSON.stringify({
      version: 1,
      rule: 'PUBLISH_FAILED',
      nonce,
      createdAt: now,
    }, null, 2)}\n`, 'utf8');
  } finally {
    await handle.close();
  }
}

function assertPublishManifest(actual, expected, contract) {
  if (!manifestsEqual(expected, actual)) {
    fail(
      'GENERATED_MANIFEST_RACE',
      'candidato gerado mudou entre validacao e publicacao.',
    );
  }
  if (contract !== null) {
    assertApprovedGeneratedManifest(actual, contract);
  }
}

async function publishCandidate(
  context,
  operation,
  expectedManifest,
  generatedManifestContract,
) {
  const {
    adapters,
    configuration,
    frontendRoot,
    journalPath,
    target,
    workspace,
  } = context;
  const hadTarget = await pathExists(target);
  if (hadTarget) {
    await readTreeManifest(target);
  }
  const journal = {
    version: 1,
    nonce: operation.nonce,
    state: 'prepared',
    operation: toPosixPath(relative(frontendRoot, operation.root)),
    candidate: toPosixPath(relative(frontendRoot, operation.candidate)),
    backup: toPosixPath(relative(frontendRoot, operation.backup)),
    target: configuration.generator.target,
    hadTarget,
    createdAt: adapters.now(),
  };
  journalPaths(frontendRoot, workspace, configuration, journal);
  await writeJsonAtomically(journalPath, journal, operation.nonce);
  operation.preserve = true;

  try {
    if (hadTarget) {
      await adapters.beforeRename({ role: 'move-current', from: target, to: operation.backup });
      await rename(target, operation.backup);
      journal.state = 'old-moved';
      await writeJsonAtomically(journalPath, journal, operation.nonce);
    }

    await adapters.beforeRename({ role: 'install-candidate', from: operation.candidate, to: target });
    const installManifest = await readTreeManifest(operation.candidate);
    assertPublishManifest(
      installManifest,
      expectedManifest,
      generatedManifestContract,
    );
    await mkdir(dirname(target), { recursive: true });
    await rename(operation.candidate, target);
    const installedManifest = await readTreeManifest(target);
    assertPublishManifest(
      installedManifest,
      expectedManifest,
      generatedManifestContract,
    );
    journal.state = 'new-installed';
    await writeJsonAtomically(journalPath, journal, operation.nonce);
  } catch {
    let rollbackComplete = false;
    try {
      const targetExists = await pathExists(target);
      const backupExists = await pathExists(operation.backup);
      if (hadTarget) {
        if (backupExists) {
          await readTreeManifest(operation.backup);
          if (targetExists) {
            await readTreeManifest(target);
            await rm(target, { recursive: true, force: false });
          }
          await rename(operation.backup, target);
        }
        rollbackComplete = await pathExists(target) && !await pathExists(operation.backup);
      } else {
        if (targetExists) {
          await readTreeManifest(target);
          await rm(target, { recursive: true, force: false });
        }
        rollbackComplete = !await pathExists(target) && !backupExists;
      }
      await preservePublishDiagnostic(workspace, operation.nonce, adapters.now());
      if (rollbackComplete) {
        await rm(journalPath, { force: true });
        operation.preserve = false;
      }
    } catch {
      // O journal e o backup sao mantidos quando a restauracao nao e comprovada.
    }
    fail('PUBLISH_FAILED', 'publicacao falhou; a arvore anterior foi restaurada quando seguro.');
  }

  if (await pathExists(operation.backup)) {
    await removeKnownTree(workspace, operation.backup);
  }
  await rm(journalPath, { force: true });
  operation.preserve = false;
}

async function createOperation(workspace, nonce) {
  const operations = join(workspace, 'operations');
  const root = join(operations, nonce);
  const raw = join(root, 'raw');
  const candidate = join(root, 'candidate');
  const backup = join(root, 'backup');
  await assertNoLinks(workspace, operations);
  await mkdir(operations, { recursive: true });
  await assertNoLinks(workspace, operations);
  try {
    await mkdir(root, { recursive: false });
  } catch (error) {
    if (error?.code === 'EEXIST') {
      fail('OPERATION_EXISTS', 'area de operacao ja existe.');
    }
    throw error;
  }
  await assertNoLinks(workspace, root);
  await mkdir(raw, { recursive: false });
  await assertNoLinks(workspace, raw);
  const executionEngine = join(root, 'openapi-generator-cli.jar');
  return { backup, candidate, executionEngine, nonce, preserve: false, raw, root };
}

export async function executeGenerateApi({
  projectRoot = defaultProjectRoot,
  mode = 'generate',
  contract = APPROVED_ENGINE,
  generatedManifestContract = APPROVED_GENERATED_MANIFEST,
  adapters: adapterOverrides = {},
} = {}) {
  if (!['generate', 'check'].includes(mode)) {
    fail('MODE_INVALID', 'modo deve ser generate ou check.');
  }
  const adapters = { ...createDefaultAdapters(), ...adapterOverrides };
  const nonce = adapters.nonce();
  validateNonce(nonce);

  const context = {
    ...(await loadConfiguration(resolve(projectRoot), contract)),
    adapters,
    contract,
    projectRoot: resolve(projectRoot),
  };
  context.journalPath = join(context.workspace, JOURNAL_FILE);

  const targetVolume = await adapters.volumeOf(context.target);
  const workspaceVolume = await adapters.volumeOf(context.workspace);
  if (targetVolume !== workspaceVolume) {
    fail('VOLUME_MISMATCH', 'alvo e area transacional estao em volumes diferentes.');
  }

  await verifyRegularFile(context.inputSpec, 'SPEC_MISSING', 'SPEC_INVALID');
  await verifyEngine(context.enginePath, contract);
  await mkdir(context.workspace, { recursive: true });
  await assertNoLinks(context.projectRoot, context.workspace);

  const lock = await acquireLock(context.workspace, adapters, nonce);
  let operation;
  let primaryError;
  try {
    if (await pathExists(context.journalPath)) {
      if (mode === 'check') {
        fail('RECOVERY_REQUIRED', 'journal pendente exige execucao generate controlada.');
      }
      await recoverJournal(context);
    }

    operation = await createOperation(context.workspace, nonce);
    await copyFile(
      context.enginePath,
      operation.executionEngine,
      fileSystemConstants.COPYFILE_EXCL,
    );
    await verifyEngine(operation.executionEngine, contract);
    let generatorError;
    try {
      await adapters.generate({
        configuration: context.configuration,
        enginePath: operation.executionEngine,
        frontendRoot: context.frontendRoot,
        inputSpec: context.inputSpec,
        outputDirectory: operation.raw,
      });
    } catch (error) {
      generatorError = error;
    }
    let executionEngineError;
    try {
      await verifyEngine(operation.executionEngine, contract);
    } catch (error) {
      executionEngineError = error;
    }
    let cacheEngineError;
    try {
      await verifyEngine(context.enginePath, contract);
    } catch (error) {
      cacheEngineError = error;
    }
    if (executionEngineError) {
      throw executionEngineError;
    }
    if (cacheEngineError) {
      throw cacheEngineError;
    }
    if (generatorError) {
      if (generatorError instanceof GenerateApiContractError) {
        throw generatorError;
      }
      fail('GENERATOR_FAILED', 'motor OpenAPI terminou com falha.');
    }

    await materializeTypeScript(operation.raw, operation.candidate);
    const candidateManifest = await readTreeManifest(operation.candidate);
    if (generatedManifestContract !== null) {
      assertApprovedGeneratedManifest(candidateManifest, generatedManifestContract);
    }
    if (mode === 'check') {
      await compareWithTarget(candidateManifest, context.target);
      return {
        mode,
        drift: false,
        digest: candidateManifest.digest,
        files: candidateManifest.files,
      };
    }

    await publishCandidate(
      context,
      operation,
      candidateManifest,
      generatedManifestContract,
    );
    return {
      mode,
      digest: candidateManifest.digest,
      files: candidateManifest.files,
    };
  } catch (error) {
    primaryError = error;
    throw error;
  } finally {
    try {
      if (operation && !operation.preserve && await pathExists(operation.root)) {
        await removeKnownTree(context.workspace, operation.root);
      }
    } catch (cleanupError) {
      if (!primaryError) {
        throw cleanupError;
      }
    } finally {
      try {
        await releaseLock(lock.lockPath, lock.lockValue);
      } catch (lockError) {
        if (!primaryError) {
          throw lockError;
        }
      }
    }
  }
}

export function sanitizeError(error) {
  if (error instanceof GenerateApiContractError) {
    return error.message;
  }
  return 'INTERNAL_ERROR: falha interna no pipeline OpenAPI.';
}

async function main() {
  const argumentsList = process.argv.slice(2);
  const mode = argumentsList.length === 0
    ? 'generate'
    : argumentsList.length === 1 && argumentsList[0] === '--check'
      ? 'check'
      : null;

  if (mode === null) {
    console.error('Uso: node frontend/scripts/generate-api.mjs [--check]');
    process.exitCode = 2;
    return;
  }

  try {
    await executeGenerateApi({ projectRoot: defaultProjectRoot, mode });
    console.log(`[generate-api] OK: modo ${mode} concluido.`);
  } catch (error) {
    console.error(`[generate-api] ERRO: ${sanitizeError(error)}`);
    process.exitCode = 1;
  }
}

const invokedAsScript = process.argv[1]
  && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (invokedAsScript) {
  await main();
}
