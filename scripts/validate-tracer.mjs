#!/usr/bin/env node

import { spawn } from 'node:child_process';
import { createHash, randomUUID } from 'node:crypto';
import { createReadStream } from 'node:fs';
import {
  lstat,
  mkdir,
  readFile,
  readdir,
  realpath,
  rm,
} from 'node:fs/promises';
import { createServer } from 'node:http';
import { createServer as createNetServer } from 'node:net';
import {
  dirname,
  extname,
  isAbsolute,
  join,
  posix,
  relative,
  resolve,
  sep,
} from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  RuntimePrerequisiteContractError,
  buildBackendEnvironment,
  childHasExited,
  requestLocal,
  validateStaticContract,
  waitForBackendReady,
} from './validate-runtime-prerequisites.mjs';

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(scriptsDirectory, '..');
const frontendRoot = resolve(projectRoot, 'frontend');
const backendRoot = resolve(projectRoot, 'backend');
const temporaryRoot = resolve(frontendRoot, 'tmp');
const OWNED_BUILD_PREFIX = 'browser-boundary-';

const CLIENT_ORIGIN = 'http://localhost:4200';
const KEYCLOAK_ORIGIN = 'http://localhost:8081';
const CLIENT_RUNTIME_ORIGIN = 'http://127.0.0.1:4200';
const KEYCLOAK_RUNTIME_ORIGIN = 'http://127.0.0.1:8081';
const PLAYWRIGHT_VERSION = '1.62.1';
const CHROMIUM_REVISION = '1234';
const APPROVED_PACKAGE_JSON_CANONICAL_SHA256 =
  '550D9FBE769F990BE649A8615CA6E4D4EE29ECF347FB05B28F6169EA31EDA41E';
const APPROVED_PACKAGE_LOCK_CANONICAL_SHA256 =
  'A471B9954695C0A8857A8432FD7AD025C71CDE44CCF2D538FCE97D78A7233520';
const OLD_REDIS_EXCLUSION =
  'org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration';
const COMMAND_OUTPUT_LIMIT = 2 * 1024 * 1024;
const BUILD_FILE_LIMIT = 5_000;
const BUILD_BYTE_LIMIT = 256 * 1024 * 1024;
const GLOBAL_TIMEOUT_MILLISECONDS = 20 * 60 * 1_000;
const SAFE_PLAYWRIGHT_STEPS = new Set([
  'walking: bootstrap XSRF',
  'walking: bootstrap cookie unico',
  'walking: bootstrap cookie host-only',
  'walking: bootstrap cookie legivel',
  'walking: bootstrap cookie SameSite',
  'walking: bootstrap cookie sem Secure',
  'walking: bootstrap cookie path',
  'walking: bootstrap cookie presente',
  'walking: bootstrap origem cliente',
  'walking: bootstrap resposta valida',
  'walking: pagina Keycloak hospedada',
  'walking: D-15 validacao obrigatoria',
  'walking: D-15 campos presentes',
  'walking: D-15 formulario hospedado',
  'walking: D-15 erro associado a senha',
  'walking: D-15 senha invalida',
  'walking: D-15 senha vazia',
  'walking: D-15 senha obrigatoria',
  'walking: D-15 usuario preservado',
  'walking: D-15 usuario sem erro',
  'walking: D-15 foco na senha',
  'security: response cookie names',
  'security: response cookie paths',
  'security: response SESSION HttpOnly',
  'security: response XSRF HttpOnly',
  'security: response AUTH_SESSION_ID HttpOnly',
  'security: response KC_AUTH_SESSION_HASH HttpOnly',
  'security: response KC_RESTART HttpOnly',
  'security: response KEYCLOAK_IDENTITY HttpOnly',
  'security: response KEYCLOAK_SESSION HttpOnly',
  'security: response client cookie Secure',
  'security: response Keycloak cookie Secure',
  'security: response cookie tokens',
  'security: current cookies allowlisted',
  'security: no script cookie writes',
  'security: cookie inspection',
  'security: DOM without tokens',
  'security: IndexedDB empty',
  'security: main-frame URL without tokens',
  'security: observers active',
  'security: request URLs without tokens',
  'security: storage empty',
  'walking: autenticacao hospedada',
  'walking: retorno sem token',
  'walking: primeiro PUT',
  'walking: antes do PUT sem chamada',
  'walking: PUT sem Authorization',
  'walking: PUT XSRF unico',
  'walking: PUT XSRF legivel',
  'walking: PUT XSRF SameSite',
  'walking: PUT XSRF sem Secure',
  'walking: PUT XSRF path',
  'walking: PUT identidade UUID',
  'walking: PUT XSRF automatico',
  'walking: PUT contagem correta',
  'walking: PUT mesma origem',
  'walking: PUT resposta 200',
  'walking: PUT shape permitido',
  'walking: PUT status persistido',
  'walking: PUT identidade renderizada',
  'walking: reload',
  'walking: segundo PUT',
  'walking: exposicao final',
  'production: bootstrap da aplicacao',
  'production: DOM sem harness',
  'production: rede e storage sem tracer',
]);

const APPROVED_EXECUTABLE_TREES = Object.freeze([
  Object.freeze({
    path: 'node_modules/@playwright/test',
    files: 11,
    bytes: 28_544,
    sha256: '6286F0D52F3E4073AEAED289D8E93098302194F1A876B23FDD7D600752228E92',
  }),
  Object.freeze({
    path: 'node_modules/playwright',
    files: 62,
    bytes: 5_074_152,
    sha256: '0ABB07EBCDCE953651451E4E2F9D52A340997AD16C7283C4E905377E963A382F',
  }),
  Object.freeze({
    path: 'node_modules/playwright-core',
    excludedTopLevel: Object.freeze(['.local-browsers']),
    files: 111,
    bytes: 13_442_086,
    sha256: '76592A19CA08CC0048C147AB4BE7299AFBB7B158C40EAB4421CFC52481B67D8D',
  }),
  Object.freeze({
    path: 'node_modules/playwright-core/.local-browsers/chromium-1234',
    files: 310,
    bytes: 447_417_940,
    sha256: '048BAEDD1E2B9793F3FEFC595BF754F95E7A8A35CC70B6407199655DA8FA7DD9',
  }),
  Object.freeze({
    path: 'node_modules/playwright-core/.local-browsers/chromium_headless_shell-1234',
    files: 292,
    bytes: 283_891_695,
    sha256: 'D421FB90FD0C8B7772C152487397B683D8773D04F4E863C422AF19EB3BEB8FE2',
  }),
  Object.freeze({
    path: 'node_modules/playwright-core/.local-browsers/ffmpeg-1011',
    files: 4,
    bytes: 3_517_342,
    sha256: '39E63E84A173143F1DBE81F1EAF849E8CF4A099D077DE79F30C11A0F6C9760BC',
  }),
  Object.freeze({
    path: 'node_modules/playwright-core/.local-browsers/winldd-1007',
    files: 3,
    bytes: 258_560,
    sha256: 'EE94921B09B3502728E27BEE5604392DD090FD0A6E148BFA33F7A3DF57E52016',
  }),
]);

const APPROVED_INSTALL_SCRIPT_PACKAGES = Object.freeze([
  'node_modules/@openapitools/openapi-generator-cli',
  'node_modules/@parcel/watcher',
  'node_modules/esbuild',
  'node_modules/fsevents',
  'node_modules/lmdb',
  'node_modules/msgpackr-extract',
  'node_modules/playwright/node_modules/fsevents',
]);

const PLAYWRIGHT_PACKAGES = Object.freeze({
  'node_modules/@playwright/test': Object.freeze({
    version: PLAYWRIGHT_VERSION,
    integrity:
      'sha512-DTcUc8qii+cpHvtOwggMtBRMjKZHXYWdw8syRYu2vtzuq4Wxphqq4NfCs5Zt44L6mA8rfDfj+PHnxFc/FeK6mQ==',
  }),
  'node_modules/playwright': Object.freeze({
    version: PLAYWRIGHT_VERSION,
    integrity:
      'sha512-0M+L3LAD8/nm554LOla9Ayx0j0tmFZ0FBcoQ7F1VuVHpM/XpiC8RcDzBQB8W5+hA8L22THxELzeF+2WcUzvcLg==',
  }),
  'node_modules/playwright-core': Object.freeze({
    version: PLAYWRIGHT_VERSION,
    integrity:
      'sha512-wPYSwEBJY9GHraISXqyqtx0na0LpO3XEX7jNDhntbex7tzUS7kLnZsOlFruFJB4Hi/rhDMjXGqHewDZ68nYZVw==',
  }),
});

const FROZEN_SERVICES = Object.freeze([
  Object.freeze({
    name: 'postgres',
    port: 5432,
    containerPort: 5432,
    network: 'infra_default',
    mounts: Object.freeze([
      Object.freeze({ type: 'volume', destination: '/var/lib/postgresql', readOnly: false }),
      Object.freeze({
        type: 'bind',
        destination: '/docker-entrypoint-initdb.d/init-databases.sh',
        readOnly: true,
      }),
    ]),
    image:
      'docker.io/library/postgres:18.4-trixie@sha256:a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636',
  }),
  Object.freeze({
    name: 'redis',
    port: 6379,
    containerPort: 6379,
    network: 'infra_default',
    mounts: Object.freeze([
      Object.freeze({ type: 'volume', destination: '/data', readOnly: false }),
    ]),
    image:
      'docker.io/library/redis:8.10.0-trixie@sha256:344e3945a0b431c8ff1eecd58c5573538126bd756f02fc7e218ddf1fc2546366',
  }),
  Object.freeze({
    name: 'keycloak',
    port: 8081,
    containerPort: 8080,
    network: 'infra_default',
    mounts: Object.freeze([
      Object.freeze({
        type: 'bind',
        destination: '/opt/keycloak/data/import/betufu-local-realm.json',
        readOnly: true,
      }),
      Object.freeze({
        type: 'bind',
        destination: '/opt/keycloak/themes/betufu',
        readOnly: true,
      }),
    ]),
    image:
      'quay.io/keycloak/keycloak:26.7.1@sha256:f1f1f01e472c8a78df40d8f2a49a925274eda4d3d80d5f6edbb5c880ee3c01c6',
  }),
]);

class TracerValidationError extends Error {
  constructor(rule, detail) {
    super(rule + ': ' + detail);
    this.name = 'TracerValidationError';
    this.rule = rule;
  }
}

function fail(rule, detail) {
  throw new TracerValidationError(rule, detail);
}

function expect(condition, rule, detail) {
  if (!condition) {
    fail(rule, detail);
  }
}

function safeEnvironment(source = process.env) {
  const allowedNames = [
    'APPDATA',
    'ComSpec',
    'JAVA_HOME',
    'LOCALAPPDATA',
    'PATH',
    'PATHEXT',
    'PROGRAMDATA',
    'ProgramFiles',
    'ProgramFiles(x86)',
    'ProgramW6432',
    'SystemRoot',
    'TEMP',
    'TMP',
    'USERPROFILE',
    'WINDIR',
  ];
  const result = Object.create(null);
  for (const allowedName of allowedNames) {
    const actualName = Object.keys(source)
      .find((name) => name.toLowerCase() === allowedName.toLowerCase());
    if (actualName !== undefined && source[actualName] !== undefined) {
      result[actualName] = source[actualName];
    }
  }
  result.CI = '1';
  result.NO_COLOR = '1';
  return result;
}

function delay(milliseconds, signal) {
  if (signal?.aborted) {
    return Promise.reject(signal.reason);
  }
  let timeout;
  let onAbort;
  return new Promise((resolvePromise, rejectPromise) => {
    timeout = setTimeout(resolvePromise, milliseconds);
    onAbort = () => {
      clearTimeout(timeout);
      rejectPromise(signal.reason);
    };
    signal?.addEventListener('abort', onAbort, { once: true });
  }).finally(() => {
    clearTimeout(timeout);
    signal?.removeEventListener('abort', onAbort);
  });
}

function runWindowsTreeKill(pid, rule) {
  return new Promise((resolvePromise, rejectPromise) => {
    const killer = spawn(
      'taskkill.exe',
      ['/PID', String(pid), '/T', '/F'],
      {
        env: safeEnvironment(),
        shell: false,
        windowsHide: true,
        stdio: 'ignore',
      },
    );
    let settled = false;
    const finish = (callback, value) => {
      if (settled) {
        return;
      }
      settled = true;
      clearTimeout(timeout);
      callback(value);
    };
    const timeout = setTimeout(() => {
      killer.kill('SIGKILL');
      finish(
        rejectPromise,
        new TracerValidationError(rule, 'encerramento da arvore excedeu o prazo.'),
      );
    }, 30_000);
    timeout.unref?.();
    killer.once('error', () => {
      finish(
        rejectPromise,
        new TracerValidationError(rule, 'taskkill da arvore nao iniciou.'),
      );
    });
    killer.once('close', (code) => {
      if (code === 0) {
        finish(resolvePromise);
      } else {
        finish(
          rejectPromise,
          new TracerValidationError(rule, 'taskkill da arvore terminou nao zero.'),
        );
      }
    });
  });
}

async function terminateProcessTree(child, rule) {
  if (childHasExited(child)) {
    return;
  }
  const pid = child.pid;
  expect(Number.isInteger(pid) && pid > 0, rule, 'PID proprio invalido para cleanup.');

  if (process.platform === 'win32') {
    try {
      await runWindowsTreeKill(pid, rule);
    } catch (error) {
      if (!childHasExited(child)) {
        child.kill('SIGKILL');
      }
      throw error;
    }
    return;
  }

  try {
    process.kill(-pid, 'SIGKILL');
  } catch (error) {
    if (error?.code !== 'ESRCH') {
      throw new TracerValidationError(rule, 'grupo de processo proprio nao encerrou.');
    }
  }
}

function runCaptured(command, args, options = {}) {
  return new Promise((resolvePromise, rejectPromise) => {
    if (options.signal?.aborted) {
      rejectPromise(options.signal.reason);
      return;
    }

    const child = spawn(command, args, {
      cwd: options.cwd,
      env: options.env ?? safeEnvironment(),
      shell: false,
      detached: process.platform !== 'win32',
      windowsHide: true,
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    const stdoutChunks = [];
    let outputBytes = 0;
    let settled = false;
    let timedOut = false;
    let overflowed = false;
    let terminationError;
    let terminationPromise;

    const finish = (callback, value) => {
      if (settled) {
        return;
      }
      settled = true;
      clearTimeout(timeout);
      options.signal?.removeEventListener('abort', onAbort);
      callback(value);
    };
    const terminate = () => {
      if (terminationPromise === undefined && !childHasExited(child)) {
        terminationPromise = terminateProcessTree(
          child,
          options.rule ?? 'COMMAND_CLEANUP',
        ).catch((error) => {
          terminationError = error;
        });
      }
      return terminationPromise ?? Promise.resolve();
    };
    const onData = (chunk, keep) => {
      outputBytes += chunk.length;
      if (outputBytes > (options.outputLimit ?? COMMAND_OUTPUT_LIMIT)) {
        overflowed = true;
        terminate();
        return;
      }
      if (keep) {
        stdoutChunks.push(chunk);
      }
    };
    const onAbort = () => terminate();
    const timeout = setTimeout(() => {
      timedOut = true;
      terminate();
    }, options.timeoutMilliseconds ?? 120_000);
    timeout.unref?.();
    options.signal?.addEventListener('abort', onAbort, { once: true });

    child.stdout.on('data', (chunk) => onData(chunk, true));
    child.stderr.on('data', (chunk) => onData(chunk, false));
    child.once('error', () => {
      finish(
        rejectPromise,
        new TracerValidationError(options.rule ?? 'COMMAND', 'comando nao iniciou.'),
      );
    });
    child.once('close', async (code) => {
      await (terminationPromise ?? Promise.resolve());
      if (terminationError !== undefined) {
        finish(rejectPromise, terminationError);
        return;
      }
      if (options.signal?.aborted) {
        finish(rejectPromise, options.signal.reason);
        return;
      }
      if (timedOut) {
        finish(
          rejectPromise,
          new TracerValidationError(options.rule ?? 'COMMAND', 'comando excedeu o prazo.'),
        );
        return;
      }
      if (overflowed) {
        finish(
          rejectPromise,
          new TracerValidationError(
            options.rule ?? 'COMMAND',
            'saida do comando excedeu o limite.',
          ),
        );
        return;
      }
      if (code !== 0 && options.acceptNonZero !== true) {
        finish(
          rejectPromise,
          new TracerValidationError(options.rule ?? 'COMMAND', 'comando terminou nao zero.'),
        );
        return;
      }
      const stdout = Buffer.concat(stdoutChunks).toString('utf8').trim();
      finish(
        resolvePromise,
        options.acceptNonZero === true ? { exitCode: code, stdout } : stdout,
      );
    });
  });
}

async function readJson(path, rule) {
  let value;
  try {
    value = JSON.parse(await readFile(path, 'utf8'));
  } catch {
    fail(rule, 'JSON ausente ou invalido.');
  }
  return value;
}

async function readRegularFile(path, rule) {
  let metadata;
  try {
    metadata = await lstat(path);
  } catch {
    fail(rule, 'arquivo obrigatorio ausente.');
  }
  expect(metadata.isFile() && !metadata.isSymbolicLink(), rule, 'path deve ser arquivo regular.');
  return readFile(path, 'utf8');
}

async function assertRegularDirectory(path, rule) {
  let metadata;
  try {
    metadata = await lstat(path);
  } catch {
    fail(rule, 'diretorio obrigatorio ausente.');
  }
  expect(
    metadata.isDirectory() && !metadata.isSymbolicLink(),
    rule,
    'path deve ser diretorio regular.',
  );
}

function assertInside(parent, child, rule) {
  const pathFromParent = relative(parent, child);
  expect(
    pathFromParent !== ''
      && pathFromParent !== '..'
      && !pathFromParent.startsWith('..' + sep)
      && !isAbsolute(pathFromParent),
    rule,
    'path saiu do diretorio controlado.',
  );
}

async function hashRegularTree(root, excludedTopLevel = []) {
  const resolvedRoot = resolve(root);
  const rootMetadata = await lstat(resolvedRoot);
  expect(
    rootMetadata.isDirectory() && !rootMetadata.isSymbolicLink(),
    'SUPPLY_TREE',
    'raiz executavel deve ser diretorio regular.',
  );
  expect(
    await realpath(resolvedRoot) === resolvedRoot,
    'SUPPLY_TREE',
    'raiz executavel resolveu para outro path.',
  );

  const excluded = new Set(excludedTopLevel);
  const files = [];
  async function visit(directory) {
    const entries = await readdir(directory, { withFileTypes: true });
    entries.sort((left, right) => left.name < right.name ? -1 : Number(left.name > right.name));

    for (const entry of entries) {
      if (directory === resolvedRoot && excluded.has(entry.name)) {
        continue;
      }
      const entryPath = resolve(directory, entry.name);
      assertInside(resolvedRoot, entryPath, 'SUPPLY_TREE');
      const metadata = await lstat(entryPath);
      expect(
        !metadata.isSymbolicLink(),
        'SUPPLY_TREE',
        'arvore executavel nao pode conter link ou reparse point.',
      );
      if (metadata.isDirectory()) {
        await visit(entryPath);
      } else {
        expect(metadata.isFile(), 'SUPPLY_TREE', 'arvore executavel contem entrada especial.');
        files.push({
          path: entryPath,
          relativePath: relative(resolvedRoot, entryPath).replaceAll('\\', '/'),
          size: metadata.size,
        });
      }
      expect(files.length <= 10_000, 'SUPPLY_TREE', 'arvore executavel excedeu o limite de arquivos.');
    }
  }
  await visit(resolvedRoot);
  files.sort((left, right) => (
    left.relativePath < right.relativePath
      ? -1
      : Number(left.relativePath > right.relativePath)
  ));

  const hash = createHash('sha256');
  let totalBytes = 0;
  for (const file of files) {
    const digest = await sha256File(file.path);
    hash.update(file.relativePath);
    hash.update('\0');
    hash.update(String(file.size));
    hash.update('\0');
    hash.update(digest);
    hash.update('\0');
    totalBytes += file.size;
    expect(totalBytes <= 2 * 1024 * 1024 * 1024, 'SUPPLY_TREE', 'arvore executavel excedeu o limite de bytes.');
  }

  return {
    files: files.length,
    bytes: totalBytes,
    sha256: hash.digest('hex').toUpperCase(),
  };
}

async function validateSupplyChain() {
  const packageJsonPath = resolve(frontendRoot, 'package.json');
  const packageLockPath = resolve(frontendRoot, 'package-lock.json');
  expect(
    await sha256CanonicalTextFile(packageJsonPath, 'SUPPLY_PACKAGE')
      === APPROVED_PACKAGE_JSON_CANONICAL_SHA256,
    'SUPPLY_PACKAGE',
    'SHA-256 canonico do package.json divergiu.',
  );
  expect(
    await sha256CanonicalTextFile(packageLockPath, 'SUPPLY_LOCK')
      === APPROVED_PACKAGE_LOCK_CANONICAL_SHA256,
    'SUPPLY_LOCK',
    'SHA-256 canonico do lockfile divergiu.',
  );
  const packageJson = await readJson(packageJsonPath, 'SUPPLY_PACKAGE');
  const lockfile = await readJson(packageLockPath, 'SUPPLY_LOCK');
  expect(
    packageJson?.devDependencies?.['@playwright/test'] === PLAYWRIGHT_VERSION,
    'SUPPLY_PACKAGE',
    'pin Playwright divergiu.',
  );
  expect(
    lockfile?.lockfileVersion === 3
      && lockfile?.packages?.['']?.devDependencies?.['@playwright/test']
        === PLAYWRIGHT_VERSION,
    'SUPPLY_LOCK',
    'lockfile raiz divergiu.',
  );
  for (const [packagePath, expectedPackage] of Object.entries(PLAYWRIGHT_PACKAGES)) {
    const actualPackage = lockfile.packages?.[packagePath];
    expect(
      actualPackage?.version === expectedPackage.version
        && actualPackage?.integrity === expectedPackage.integrity
        && typeof actualPackage?.resolved === 'string'
        && actualPackage.resolved.startsWith('https://registry.npmjs.org/'),
      'SUPPLY_LOCK',
      'artefato Playwright resolvido divergiu.',
    );
  }
  const installScriptPackages = Object.entries(lockfile.packages ?? {})
    .filter(([, value]) => value?.hasInstallScript === true)
    .map(([path]) => path)
    .sort();
  expect(
    sameJson(installScriptPackages, [...APPROVED_INSTALL_SCRIPT_PACKAGES].sort()),
    'SUPPLY_LIFECYCLE',
    'allowlist integral de pacotes com lifecycle divergiu.',
  );

  const browsers = await readJson(
    resolve(frontendRoot, 'node_modules/playwright-core/browsers.json'),
    'SUPPLY_BROWSER',
  );
  const chromium = browsers?.browsers?.find((browser) => browser?.name === 'chromium');
  const headless = browsers?.browsers?.find(
    (browser) => browser?.name === 'chromium-headless-shell',
  );
  expect(
    chromium?.revision === CHROMIUM_REVISION
      && chromium?.installByDefault === true
      && headless?.revision === CHROMIUM_REVISION
      && headless?.installByDefault === true,
    'SUPPLY_BROWSER',
    'manifesto Chromium divergiu.',
  );
  const browserRoot = resolve(frontendRoot, 'node_modules/playwright-core/.local-browsers');
  await assertRegularDirectory(resolve(browserRoot, 'chromium-' + CHROMIUM_REVISION), 'SUPPLY_BROWSER');
  await assertRegularDirectory(
    resolve(browserRoot, 'chromium_headless_shell-' + CHROMIUM_REVISION),
    'SUPPLY_BROWSER',
  );
  await readRegularFile(
    resolve(browserRoot, 'chromium-' + CHROMIUM_REVISION, 'INSTALLATION_COMPLETE'),
    'SUPPLY_BROWSER',
  );
  await readRegularFile(
    resolve(
      browserRoot,
      'chromium_headless_shell-' + CHROMIUM_REVISION,
      'INSTALLATION_COMPLETE',
    ),
    'SUPPLY_BROWSER',
  );

  const browserInventory = (await readdir(browserRoot, { withFileTypes: true }))
    .map((entry) => entry.name)
    .sort();
  expect(
    sameJson(browserInventory, [
      '.links',
      'chromium-1234',
      'chromium_headless_shell-1234',
      'ffmpeg-1011',
      'winldd-1007',
    ]),
    'SUPPLY_BROWSER',
    'inventario hermetico do browser divergiu.',
  );

  for (const expectedTree of APPROVED_EXECUTABLE_TREES) {
    const evidence = await hashRegularTree(
      resolve(frontendRoot, expectedTree.path),
      expectedTree.excludedTopLevel ?? [],
    );
    expect(
      evidence.files === expectedTree.files
        && evidence.bytes === expectedTree.bytes
        && evidence.sha256 === expectedTree.sha256,
      'SUPPLY_TREE',
      'bytes executaveis aprovados divergiram.',
    );
  }
}

function expectedProxyRule() {
  return {
    target: 'http://127.0.0.1:8080',
    changeOrigin: false,
    headers: {
      host: 'localhost:4200',
      'x-forwarded-host': 'localhost:4200',
      'x-forwarded-port': '4200',
      'x-forwarded-proto': 'http',
      'x-forwarded-for': '127.0.0.1',
    },
  };
}

function sameJson(left, right) {
  return JSON.stringify(left) === JSON.stringify(right);
}

function countMatches(source, pattern) {
  return [...source.matchAll(pattern)].length;
}

async function validateBrowserSources() {
  const proxy = await readJson(resolve(frontendRoot, 'proxy.conf.json'), 'BROWSER_PROXY');
  const expectedContexts = [
    '^/api(?:/|$)',
    '^/oauth2(?:/|$)',
    '^/login(?:/|$)',
  ];
  expect(
    sameJson(Object.keys(proxy), expectedContexts)
      && expectedContexts.every((context) => sameJson(proxy[context], expectedProxyRule())),
    'BROWSER_PROXY',
    'proxy deve conter somente os tres segmentos BFF e headers publicos fixos.',
  );

  const config = await readRegularFile(
    resolve(frontendRoot, 'playwright.config.ts'),
    'BROWSER_CONFIG',
  );
  const configRequirements = [
    /retries\s*:\s*0\b/u,
    /workers\s*:\s*1\b/u,
    /fullyParallel\s*:\s*false\b/u,
    /baseURL\s*:\s*['"]http:\/\/localhost:4200['"]/u,
    /browserName\s*:\s*['"]chromium['"]/u,
    /trace\s*:\s*['"]off['"]/u,
    /screenshot\s*:\s*['"]off['"]/u,
    /video\s*:\s*['"]off['"]/u,
    /serviceWorkers\s*:\s*['"]block['"]/u,
    /preserveOutput\s*:\s*['"]never['"]/u,
    /launchOptions\s*:\s*\{\s*args\s*:\s*\[\s*['"]--host-resolver-rules=MAP localhost 127\.0\.0\.1['"]\s*\]\s*,?\s*\}/u,
  ];
  expect(
    configRequirements.every((pattern) => pattern.test(config))
      && countMatches(config, /\bhost-resolver-rules\b/gu) === 1
      && !/\bwebServer\s*:/u.test(config)
      && !/\bstorageState\s*:/u.test(config)
      && !/\bextraHTTPHeaders\s*:/u.test(config)
      && !/\bproxy\s*:/u.test(config),
    'BROWSER_CONFIG',
    'config Playwright divergiu do modo hermetico sem retry.',
  );

  const spec = await readRegularFile(
    resolve(frontendRoot, 'e2e/walking-skeleton.spec.ts'),
    'BROWSER_SPEC',
  );
  const forbiddenPatterns = [
    /\btest\.(?:skip|fixme|slow)\b/u,
    /\bexpect\.poll\b/u,
    /\.toPass\s*\(/u,
    /\bcontext\.addCookies\s*\(/u,
    /\bdocument\.cookie\s*=/u,
    /\bsetExtraHTTPHeaders\s*\(/u,
    /\broute\.continue\s*\(/u,
    /\bpage\.route\s*\(/u,
    /\bpage\.request\b/u,
    /\bcontext\.request\b/u,
    /\bAPIRequestContext\b/u,
    /\bfetch\s*\(/u,
    /\bAuthorization\s*:\s*['"]Bearer/u,
  ];
  expect(
    spec.includes("BETUFU_E2E_SCENARIO")
      && spec.includes('walking-skeleton')
      && spec.includes('production-absence')
      && forbiddenPatterns.every((pattern) => !pattern.test(spec)),
    'BROWSER_SPEC',
    'spec contem atalho, retry ou fabricacao proibida.',
  );
}

function parseEnvironment(source) {
  const values = Object.create(null);
  for (const sourceLine of source.split(/\r?\n/u)) {
    const line = sourceLine.trim();
    if (line === '' || line.startsWith('#')) {
      continue;
    }
    const match = /^([A-Z][A-Z0-9_]*)=([^\r\n]*)$/u.exec(line);
    expect(match !== null, 'RUNTIME_ENV', 'linha invalida no ambiente local aprovado.');
    expect(values[match[1]] === undefined, 'RUNTIME_ENV', 'chave duplicada no ambiente local.');
    values[match[1]] = match[2];
  }
  for (const name of [
    'BETUFU_DB_JDBC_URL',
    'BETUFU_DB_USER',
    'BETUFU_DB_PASSWORD',
    'BETUFU_FLYWAY_USER',
    'BETUFU_FLYWAY_PASSWORD',
    'BETUFU_REDIS_HOST',
    'BETUFU_REDIS_PORT',
    'BETUFU_REDIS_PASSWORD',
    'BETUFU_OIDC_CLIENT_ID',
    'BETUFU_OIDC_CLIENT_SECRET',
    'BETUFU_OIDC_ISSUER_URI',
    'BETUFU_TECHNICAL_USERNAME',
    'BETUFU_TECHNICAL_PASSWORD',
  ]) {
    expect(typeof values[name] === 'string' && values[name] !== '', 'RUNTIME_ENV', 'chave local ausente.');
  }
  expect(
    values.BETUFU_TECHNICAL_USERNAME === 'technical'
      && values.BETUFU_OIDC_ISSUER_URI === KEYCLOAK_ORIGIN + '/realms/betufu-local',
    'RUNTIME_ENV',
    'identidade tecnica ou issuer local divergiram.',
  );
  return values;
}

async function assertPortFree(port) {
  for (const address of ['127.0.0.1', '::1']) {
    await new Promise((resolvePromise, rejectPromise) => {
      const server = createNetServer();
      let settled = false;
      const finish = (callback, value) => {
        if (settled) {
          return;
        }
        settled = true;
        callback(value);
      };
      server.once('error', () => {
        finish(
          rejectPromise,
          new TracerValidationError(
            'RUNTIME_PORT',
            'porta loopback obrigatoria esta ocupada ou indisponivel.',
          ),
        );
      });
      server.listen({
        port,
        host: address,
        exclusive: true,
        ipv6Only: address === '::1',
      }, () => {
        server.close((error) => {
          if (error) {
            finish(
              rejectPromise,
              new TracerValidationError('RUNTIME_PORT', 'sonda de porta nao fechou.'),
            );
          } else {
            finish(resolvePromise);
          }
        });
      });
    });
  }
}

async function allocateLoopbackPort() {
  return new Promise((resolvePromise, rejectPromise) => {
    const server = createNetServer();
    server.once('error', () => {
      rejectPromise(new TracerValidationError('PROCESS_TREE_PROBE', 'porta efemera falhou.'));
    });
    server.listen({ host: '127.0.0.1', port: 0, exclusive: true }, () => {
      const address = server.address();
      if (address === null || typeof address === 'string') {
        server.close();
        rejectPromise(new TracerValidationError('PROCESS_TREE_PROBE', 'porta efemera invalida.'));
        return;
      }
      server.close((error) => {
        if (error) {
          rejectPromise(new TracerValidationError('PROCESS_TREE_PROBE', 'porta efemera nao fechou.'));
        } else {
          resolvePromise(address.port);
        }
      });
    });
  });
}

async function validateCapturedTreeTermination(signal) {
  const port = await allocateLoopbackPort();
  const grandchildSource = [
    "const { createServer } = require('node:net');",
    'const port = Number(process.argv[1]);',
    'const server = createServer();',
    "server.listen(port, '127.0.0.1', () => process.stdout.write('ready'));",
    'setInterval(() => undefined, 1000);',
  ].join('');
  const parentSource = [
    "const { spawn } = require('node:child_process');",
    'const child = spawn(process.execPath, [\'-e\', process.argv[1], process.argv[2]], ',
    "{ stdio: ['ignore', 'pipe', 'ignore'] });",
    "child.stdout.once('data', () => process.stdout.write('x'.repeat(4096)));",
    'setInterval(() => undefined, 1000);',
  ].join('');
  let expectedOverflow = false;
  try {
    await runCaptured(
      process.execPath,
      ['-e', parentSource, grandchildSource, String(port)],
      {
        env: safeEnvironment(),
        outputLimit: 64,
        rule: 'PROCESS_TREE_PROBE',
        signal,
        timeoutMilliseconds: 10_000,
      },
    );
  } catch (error) {
    expectedOverflow = error instanceof TracerValidationError
      && error.rule === 'PROCESS_TREE_PROBE'
      && error.message.endsWith('saida do comando excedeu o limite.');
  }
  expect(expectedOverflow, 'PROCESS_TREE_PROBE', 'overflow controlado nao encerrou como esperado.');
  await assertPortFree(port);
}

async function validateDockerContext(signal) {
  const context = await runCaptured('docker', ['context', 'show'], {
    rule: 'RUNTIME_DOCKER',
    signal,
    timeoutMilliseconds: 30_000,
  });
  const endpoint = await runCaptured(
    'docker',
    ['context', 'inspect', context, '--format', '{{(index .Endpoints "docker").Host}}'],
    {
      rule: 'RUNTIME_DOCKER',
      signal,
      timeoutMilliseconds: 30_000,
    },
  );
  expect(
    endpoint.startsWith('npipe://') || endpoint.startsWith('unix://'),
    'RUNTIME_DOCKER',
    'daemon Docker deve ser local.',
  );
  await runCaptured(
    'docker',
    [
      'compose',
      '--env-file',
      resolve(projectRoot, 'infra/.env.example'),
      '-f',
      resolve(projectRoot, 'infra/compose.yaml'),
      '--project-name',
      'infra',
      'config',
      '--quiet',
    ],
    {
      rule: 'RUNTIME_COMPOSE',
      signal,
      timeoutMilliseconds: 30_000,
    },
  );
}

async function inspectContainerField(id, format, signal) {
  return runCaptured(
    'docker',
    ['container', 'inspect', '--format', format, id],
    { rule: 'RUNTIME_CONTAINER', signal, timeoutMilliseconds: 30_000 },
  );
}

async function parseRestrictedDockerJson(value) {
  try {
    return JSON.parse(value);
  } catch {
    fail('RUNTIME_CONTAINER', 'inspect restrito retornou JSON invalido.');
  }
}

async function inspectContainer(id, signal) {
  const [output, configHash, imageId, portBindingsJson, mountsJson, networksJson, healthcheckJson] =
    await Promise.all([
      inspectContainerField(
        id,
        '{{.Id}}|{{.State.Status}}|{{.State.StartedAt}}|{{.Config.Image}}|{{if eq .State.Status "running"}}{{.State.Health.Status}}{{else}}none{{end}}',
        signal,
      ),
      inspectContainerField(
        id,
        '{{index .Config.Labels "com.docker.compose.config-hash"}}',
        signal,
      ),
      inspectContainerField(id, '{{.Image}}', signal),
      inspectContainerField(id, '{{json .HostConfig.PortBindings}}', signal),
      inspectContainerField(id, '{{json .Mounts}}', signal),
      inspectContainerField(id, '{{json .NetworkSettings.Networks}}', signal),
      inspectContainerField(id, '{{json .Config.Healthcheck}}', signal),
    ]);
  const [containerId, status, startedAt, image, health, ...extra] = output.split('|');
  const [portBindings, mounts, networks, healthcheck] = await Promise.all([
    parseRestrictedDockerJson(portBindingsJson),
    parseRestrictedDockerJson(mountsJson),
    parseRestrictedDockerJson(networksJson),
    parseRestrictedDockerJson(healthcheckJson),
  ]);
  expect(
    extra.length === 0
      && /^[0-9a-f]{64}$/u.test(containerId)
      && /^[0-9a-f]{64}$/u.test(configHash)
      && /^sha256:[0-9a-f]{64}$/u.test(imageId)
      && status !== ''
      && startedAt !== ''
      && image !== ''
      && health !== ''
      && typeof portBindings === 'object'
      && portBindings !== null
      && Array.isArray(mounts)
      && typeof networks === 'object'
      && networks !== null
      && typeof healthcheck === 'object'
      && healthcheck !== null,
    'RUNTIME_CONTAINER',
    'inspect restrito retornou forma invalida.',
  );
  return {
    id: containerId,
    configHash,
    health,
    healthcheck,
    image,
    imageId,
    mounts,
    networks,
    portBindings,
    startedAt,
    status,
  };
}

async function expectedComposeHash(service, signal) {
  const output = await runCaptured(
    'docker',
    [
      'compose',
      '--env-file',
      resolve(projectRoot, 'infra/.env.example'),
      '-f',
      resolve(projectRoot, 'infra/compose.yaml'),
      '--project-name',
      'infra',
      'config',
      '--hash',
      service,
    ],
    { rule: 'RUNTIME_COMPOSE', signal, timeoutMilliseconds: 30_000 },
  );
  const match = new RegExp(`^${service} ([0-9a-f]{64})$`, 'u').exec(output);
  expect(match !== null, 'RUNTIME_COMPOSE', 'hash do servico Compose retornou forma invalida.');
  return match[1];
}

function validateFrozenContainerShape(service, state, expectedConfigHash, expectedImageId) {
  const expectedPorts = {
    [`${service.containerPort}/tcp`]: [
      { HostIp: '127.0.0.1', HostPort: String(service.port) },
    ],
  };
  const actualMounts = state.mounts
    .map((mount) => ({
      destination: mount.Destination,
      readOnly: mount.RW === false,
      type: mount.Type,
    }))
    .sort((left, right) => left.destination.localeCompare(right.destination));
  const expectedMounts = service.mounts
    .map((mount) => ({
      destination: mount.destination,
      readOnly: mount.readOnly,
      type: mount.type,
    }))
    .sort((left, right) => left.destination.localeCompare(right.destination));

  expect(
    state.configHash === expectedConfigHash
      && state.imageId === expectedImageId
      && sameJson(state.portBindings, expectedPorts)
      && sameJson(actualMounts, expectedMounts)
      && sameJson(Object.keys(state.networks).sort(), [service.network])
      && Array.isArray(state.healthcheck.Test)
      && state.healthcheck.Test.length > 0,
    'RUNTIME_CONTAINER_SHAPE',
    'container congelado divergiu do Compose atual.',
  );
}

async function discoverFrozenContainers(signal) {
  const containers = [];
  for (const service of FROZEN_SERVICES) {
    const output = await runCaptured(
      'docker',
      [
        'ps',
        '-aq',
        '--filter',
        'label=com.docker.compose.project=infra',
        '--filter',
        'label=com.docker.compose.service=' + service.name,
      ],
      { rule: 'RUNTIME_CONTAINER', signal, timeoutMilliseconds: 30_000 },
    );
    const ids = output.split(/\r?\n/u).filter((value) => value !== '');
    expect(ids.length === 1, 'RUNTIME_CONTAINER', 'container congelado ausente ou ambiguo.');
    const [state, configHash, imageId] = await Promise.all([
      inspectContainer(ids[0], signal),
      expectedComposeHash(service.name, signal),
      runCaptured('docker', ['image', 'inspect', '--format', '{{.Id}}', service.image], {
        rule: 'RUNTIME_CONTAINER',
        signal,
        timeoutMilliseconds: 30_000,
      }),
    ]);
    expect(
      ['created', 'exited'].includes(state.status) && state.image === service.image,
      'RUNTIME_CONTAINER',
      'container congelado deve estar nao executando e usar a imagem exata.',
    );
    validateFrozenContainerShape(service, state, configHash, imageId);
    containers.push({
      ...service,
      id: state.id,
      beforeStartedAt: state.startedAt,
      startedAt: undefined,
      startRequested: false,
    });
  }
  return containers;
}

async function waitForContainerHealthy(container, signal) {
  const deadline = Date.now() + 360_000;
  while (Date.now() < deadline) {
    const state = await inspectContainer(container.id, signal);
    expect(
      state.id === container.id
        && state.image === container.image
        && state.startedAt === container.startedAt,
      'RUNTIME_CONTAINER',
      'ownership do container mudou durante o startup.',
    );
    if (state.status === 'running' && state.health === 'healthy') {
      return;
    }
    expect(
      state.status === 'running' && ['starting', 'unhealthy'].includes(state.health),
      'RUNTIME_CONTAINER',
      'container encerrou ou perdeu healthcheck durante o startup.',
    );
    await delay(1_000, signal);
  }
  fail('RUNTIME_CONTAINER', 'container nao ficou healthy no prazo.');
}

async function startFrozenContainers(containers, signal) {
  for (const container of containers) {
    await assertPortFree(container.port);
    container.startRequested = true;
    await runCaptured('docker', ['container', 'start', container.id], {
      rule: 'RUNTIME_CONTAINER',
      signal,
      timeoutMilliseconds: 60_000,
    });
    const state = await inspectContainer(container.id, signal);
    expect(
      state.status === 'running'
        && state.image === container.image
        && state.startedAt !== container.beforeStartedAt,
      'RUNTIME_CONTAINER',
      'container nao iniciou sob ownership desta prova.',
    );
    container.startedAt = state.startedAt;
    await waitForContainerHealthy(container, signal);
  }
}

async function cleanupFrozenContainers(containers) {
  const failures = [];
  for (const container of [...containers].reverse()) {
    if (!container.startRequested) {
      continue;
    }
    try {
      const state = await inspectContainer(container.id);
      if (container.startedAt === undefined
        && ['created', 'exited'].includes(state.status)
        && state.startedAt === container.beforeStartedAt
        && state.image === container.image) {
        continue;
      }
      const expectedStartedAt = container.startedAt ?? state.startedAt;
      expect(
        state.id === container.id
          && state.image === container.image
          && state.startedAt === expectedStartedAt
          && state.startedAt !== container.beforeStartedAt,
        'RUNTIME_CLEANUP',
        'ownership do container divergiu no cleanup.',
      );
      if (state.status === 'running') {
        await runCaptured(
          'docker',
          ['container', 'stop', '--time', '30', container.id],
          { rule: 'RUNTIME_CLEANUP', timeoutMilliseconds: 60_000 },
        );
      } else {
        expect(state.status === 'exited', 'RUNTIME_CLEANUP', 'estado inesperado no cleanup.');
      }
      const stopped = await inspectContainer(container.id);
      expect(
        stopped.status === 'exited'
          && stopped.id === container.id
          && stopped.image === container.image
          && stopped.startedAt === expectedStartedAt,
        'RUNTIME_CLEANUP',
        'container proprio nao terminou parado.',
      );
    } catch {
      failures.push(container.name);
    }
  }
  expect(failures.length === 0, 'RUNTIME_CLEANUP', 'cleanup de container falhou.');
}

function spawnOwned(command, args, options) {
  const child = spawn(command, args, {
    cwd: options.cwd,
    env: options.env,
    shell: false,
    detached: process.platform !== 'win32',
    windowsHide: true,
    stdio: ['ignore', 'ignore', 'ignore'],
  });
  const spawnOutcome = new Promise((resolvePromise, rejectPromise) => {
    child.once('spawn', resolvePromise);
    child.once('error', () => {
      rejectPromise(new TracerValidationError(options.rule, 'processo nao iniciou.'));
    });
  });
  return {
    child,
    pid: child.pid,
    nonce: randomUUID(),
    rule: options.rule,
    spawnOutcome,
  };
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

async function stopOwned(handle) {
  if (handle === undefined || childHasExited(handle.child)) {
    return;
  }
  expect(
    Number.isInteger(handle.pid) && handle.pid > 0 && handle.nonce !== '',
    'RUNTIME_CLEANUP',
    'ownership do processo proprio ausente.',
  );
  await terminateProcessTree(handle.child, 'RUNTIME_CLEANUP');
  expect(
    await waitForExit(handle.child, 8_000),
    'RUNTIME_CLEANUP',
    'processo proprio nao encerrou.',
  );
}

async function waitForFrontend(handle, signal) {
  await handle.spawnOutcome;
  const deadline = Date.now() + 90_000;
  while (Date.now() < deadline) {
    expect(!childHasExited(handle.child), 'RUNTIME_FRONTEND', 'Angular encerrou antes do readiness.');
    try {
      const response = await requestLocal(CLIENT_RUNTIME_ORIGIN + '/tracer', {
        allowedOrigins: new Set([CLIENT_RUNTIME_ORIGIN]),
        timeoutMilliseconds: 1_500,
        signal,
      });
      if (response.status === 200) {
        return;
      }
    } catch (error) {
      if (!(error instanceof RuntimePrerequisiteContractError) || error.rule !== 'RUNTIME_HTTP') {
        throw error;
      }
    }
    await delay(250, signal);
  }
  fail('RUNTIME_FRONTEND', 'Angular nao ficou pronto no prazo.');
}

async function sha256File(path) {
  return new Promise((resolvePromise, rejectPromise) => {
    const hash = createHash('sha256');
    const stream = createReadStream(path);
    stream.on('data', (chunk) => hash.update(chunk));
    stream.once('error', () => rejectPromise(new TracerValidationError('ARTIFACT', 'hash falhou.')));
    stream.once('end', () => resolvePromise(hash.digest('hex').toUpperCase()));
  });
}

async function sha256CanonicalTextFile(path, rule) {
  let content;
  try {
    content = await readFile(path);
  } catch {
    fail(rule, 'arquivo textual aprovado ausente.');
  }
  const text = content.toString('utf8');
  expect(
    Buffer.from(text, 'utf8').equals(content),
    rule,
    'arquivo textual aprovado deve usar UTF-8 valido.',
  );
  expect(
    !/\r(?!\n)/u.test(text),
    rule,
    'arquivo textual aprovado contem CR isolado.',
  );
  return createHash('sha256')
    .update(text.replace(/\r\n/gu, '\n'), 'utf8')
    .digest('hex')
    .toUpperCase();
}

async function findBackendJar() {
  const target = resolve(backendRoot, 'target');
  await assertRegularDirectory(target, 'BACKEND_JAR');
  const entries = await readdir(target, { withFileTypes: true });
  const candidates = entries.filter(
    (entry) => entry.isFile()
      && /^betufu-backend-[0-9].*\.jar$/u.test(entry.name)
      && !entry.name.endsWith('.original.jar'),
  );
  expect(candidates.length === 1, 'BACKEND_JAR', 'JAR executavel ausente ou ambiguo.');
  const jarPath = resolve(target, candidates[0].name);
  assertInside(target, jarPath, 'BACKEND_JAR');
  const metadata = await lstat(jarPath);
  expect(metadata.isFile() && !metadata.isSymbolicLink(), 'BACKEND_JAR', 'JAR deve ser regular.');
  return {
    path: jarPath,
    size: metadata.size,
    sha256: await sha256File(jarPath),
  };
}

async function collectBuildFiles(root) {
  await assertRegularDirectory(root, 'BUILD_OUTPUT');
  const files = [];
  let totalBytes = 0;
  async function visit(directory) {
    const entries = await readdir(directory, { withFileTypes: true });
    entries.sort((left, right) => left.name.localeCompare(right.name));
    for (const entry of entries) {
      const path = resolve(directory, entry.name);
      assertInside(root, path, 'BUILD_OUTPUT');
      const metadata = await lstat(path);
      expect(!metadata.isSymbolicLink(), 'BUILD_OUTPUT', 'build nao pode conter symlink.');
      if (metadata.isDirectory()) {
        await visit(path);
      } else {
        expect(metadata.isFile(), 'BUILD_OUTPUT', 'build contem arquivo especial.');
        totalBytes += metadata.size;
        files.push({ path, relativePath: relative(root, path).replaceAll('\\', '/'), size: metadata.size });
        expect(
          files.length <= BUILD_FILE_LIMIT && totalBytes <= BUILD_BYTE_LIMIT,
          'BUILD_OUTPUT',
          'build excedeu os limites de inspeção.',
        );
      }
    }
  }
  await visit(root);
  expect(files.length > 0, 'BUILD_OUTPUT', 'build ficou vazio.');
  return { files, totalBytes };
}

function resolveStatsImport(outputKey, importedPath, outputKeys) {
  const normalizedImport = importedPath.replaceAll('\\', '/');
  if (outputKeys.has(normalizedImport)) {
    return normalizedImport;
  }
  const relativeCandidate = posix.normalize(
    posix.join(posix.dirname(outputKey), normalizedImport),
  );
  if (outputKeys.has(relativeCandidate)) {
    return relativeCandidate;
  }
  const suffixMatches = [...outputKeys].filter((candidate) =>
    candidate === normalizedImport || candidate.endsWith('/' + normalizedImport),
  );
  return suffixMatches.length === 1 ? suffixMatches[0] : undefined;
}

async function validateProductionStats(files) {
  const statsFiles = files.filter((file) => file.relativePath.endsWith('stats.json'));
  expect(statsFiles.length === 1, 'PRODUCTION_STATS', 'deve existir um unico stats.json production.');
  const stats = await readJson(statsFiles[0].path, 'PRODUCTION_STATS');
  expect(
    typeof stats?.inputs === 'object'
      && stats.inputs !== null
      && !Array.isArray(stats.inputs)
      && typeof stats?.outputs === 'object'
      && stats.outputs !== null
      && !Array.isArray(stats.outputs),
    'PRODUCTION_STATS',
    'stats.json nao possui o grafo esbuild esperado.',
  );

  const normalizedOutputs = new Map(
    Object.entries(stats.outputs).map(([path, value]) => [path.replaceAll('\\', '/'), value]),
  );
  const outputKeys = new Set(normalizedOutputs.keys());
  const entryOutputs = [...normalizedOutputs]
    .filter(([, value]) => typeof value?.entryPoint === 'string')
    .map(([path]) => path);
  expect(entryOutputs.length > 0, 'PRODUCTION_STATS', 'grafo production nao possui entrypoint.');

  const reachable = new Set();
  const queue = [...entryOutputs];
  while (queue.length > 0) {
    const outputKey = queue.shift();
    if (reachable.has(outputKey)) {
      continue;
    }
    reachable.add(outputKey);
    const output = normalizedOutputs.get(outputKey);
    expect(
      output !== null && typeof output === 'object' && Array.isArray(output.imports),
      'PRODUCTION_STATS',
      'saida production possui forma invalida.',
    );
    for (const imported of output.imports) {
      expect(
        imported !== null
          && typeof imported === 'object'
          && typeof imported.path === 'string'
          && (imported.external === undefined || typeof imported.external === 'boolean'),
        'PRODUCTION_STATS',
        'aresta de import production possui forma invalida.',
      );
      if (imported.external === true) {
        continue;
      }
      const target = resolveStatsImport(outputKey, imported.path, outputKeys);
      expect(target !== undefined, 'PRODUCTION_STATS', 'aresta aponta para chunk ausente ou ambiguo.');
      queue.push(target);
    }
  }

  const tracerInputSeen = [
    ...Object.keys(stats.inputs),
    ...[...reachable].flatMap((outputKey) => {
      const inputs = normalizedOutputs.get(outputKey)?.inputs;
      return inputs !== null && typeof inputs === 'object' ? Object.keys(inputs) : [];
    }),
  ].some((inputPath) => /(?:^|\/)features\/tracer(?:\/|$)/iu.test(inputPath.replaceAll('\\', '/')));
  expect(!tracerInputSeen, 'PRODUCTION_STATS', 'grafo production alcanca input do tracer.');

  const browserJavaScriptFiles = files.filter(
    (file) => file.relativePath.startsWith('browser/') && file.relativePath.endsWith('.js'),
  );
  expect(browserJavaScriptFiles.length > 0, 'PRODUCTION_STATS', 'build production nao possui JavaScript.');
  for (const file of browserJavaScriptFiles) {
    const basename = posix.basename(file.relativePath);
    const matches = [...outputKeys].filter((outputKey) => posix.basename(outputKey) === basename);
    expect(
      matches.length === 1 && reachable.has(matches[0]),
      'PRODUCTION_STATS',
      'chunk JavaScript production ficou fora do grafo alcancavel.',
    );
  }
}

async function inspectBuild(root, production) {
  const browserRoot = resolve(root, 'browser');
  assertInside(root, browserRoot, 'BUILD_OUTPUT');
  await readRegularFile(resolve(browserRoot, 'index.html'), 'BUILD_OUTPUT');
  const { files, totalBytes } = await collectBuildFiles(root);
  const hash = createHash('sha256');
  const forbiddenMarkers = [
    'tracer',
    'verificar integração',
    'verificando…',
  ];
  for (const file of files) {
    const content = await readFile(file.path);
    hash.update(file.relativePath);
    hash.update('\0');
    hash.update(content);
    hash.update('\0');
    if (production) {
      const text = content.toString('utf8').toLowerCase();
      const lowerRelativePath = file.relativePath.toLowerCase();
      expect(
        forbiddenMarkers.every(
          (marker) => !lowerRelativePath.includes(marker) && !text.includes(marker),
        ),
        'PRODUCTION_ABSENCE',
        'build production divulgou o harness tracer.',
      );
    }
  }
  if (production) {
    await validateProductionStats(files);
  }
  return {
    browserRoot,
    fileCount: files.length,
    totalBytes,
    sha256: hash.digest('hex').toUpperCase(),
  };
}

async function runPlaywright(scenario, values, outputDirectory, signal) {
  await validateSupplyChain();
  const cli = resolve(frontendRoot, 'node_modules/@playwright/test/cli.js');
  await readRegularFile(cli, 'BROWSER_CLI');
  const environment = safeEnvironment();
  environment.PLAYWRIGHT_BROWSERS_PATH = '0';
  environment.BETUFU_E2E_SCENARIO = scenario;
  environment.BETUFU_PLAYWRIGHT_OUTPUT_DIR = outputDirectory;
  if (scenario === 'walking-skeleton') {
    environment.BETUFU_TECHNICAL_PASSWORD = values.BETUFU_TECHNICAL_PASSWORD;
  }
  const result = await runCaptured(
    process.execPath,
    [cli, 'test', '--config', 'playwright.config.ts', '--reporter=json'],
    {
      cwd: frontendRoot,
      env: environment,
      rule: scenario === 'walking-skeleton' ? 'BROWSER_E2E' : 'PRODUCTION_BROWSER',
      signal,
      timeoutMilliseconds: scenario === 'walking-skeleton' ? 240_000 : 90_000,
      outputLimit: COMMAND_OUTPUT_LIMIT,
      acceptNonZero: true,
    },
  );
  let report;
  try {
    report = JSON.parse(result.stdout);
  } catch {
    fail('BROWSER_REPORT', 'reporter Playwright retornou JSON invalido.');
  }
  if (result.exitCode !== 0) {
    let failedStep = 'passo nao identificado';
    const visit = (value) => {
      if (Array.isArray(value)) {
        for (const item of value) {
          visit(item);
        }
        return;
      }
      if (typeof value !== 'object' || value === null) {
        return;
      }
      if (value.error !== undefined
        && typeof value.title === 'string'
        && SAFE_PLAYWRIGHT_STEPS.has(value.title)) {
        failedStep = value.title;
      }
      for (const [key, child] of Object.entries(value)) {
        if (['suites', 'specs', 'tests', 'results', 'steps'].includes(key)) {
          visit(child);
        }
      }
    };
    visit(report);
    fail(
      scenario === 'walking-skeleton' ? 'BROWSER_E2E' : 'PRODUCTION_BROWSER',
      'falha em ' + failedStep + '.',
    );
  }
  expect(
    report?.stats?.expected === 1
      && report?.stats?.unexpected === 0
      && report?.stats?.flaky === 0
      && report?.stats?.skipped === 0,
    'BROWSER_REPORT',
    'Playwright deve concluir exatamente um teste esperado sem retry/skip.',
  );
}

function contentType(path) {
  return {
    '.css': 'text/css; charset=utf-8',
    '.html': 'text/html; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8',
    '.json': 'application/json; charset=utf-8',
    '.map': 'application/json; charset=utf-8',
    '.svg': 'image/svg+xml',
    '.woff2': 'font/woff2',
  }[extname(path).toLowerCase()] ?? 'application/octet-stream';
}

async function createProductionServer(browserRoot) {
  const realRoot = await realpath(browserRoot);
  const indexPath = resolve(realRoot, 'index.html');
  await readRegularFile(indexPath, 'PRODUCTION_SERVER');
  const server = createServer(async (request, response) => {
    try {
      if (!['GET', 'HEAD'].includes(request.method ?? '')) {
        response.writeHead(405, { Allow: 'GET, HEAD' });
        response.end();
        return;
      }
      const requestUrl = new URL(request.url ?? '/', CLIENT_ORIGIN);
      let requestedPath;
      try {
        requestedPath = decodeURIComponent(requestUrl.pathname);
      } catch {
        response.writeHead(400);
        response.end();
        return;
      }
      const candidate = resolve(realRoot, '.' + requestedPath.replaceAll('\\', '/'));
      const relativeCandidate = relative(realRoot, candidate);
      if (relativeCandidate === '..' || relativeCandidate.startsWith('..' + sep)) {
        response.writeHead(403);
        response.end();
        return;
      }
      let servedPath = candidate;
      try {
        const metadata = await lstat(candidate);
        if (!metadata.isFile() || metadata.isSymbolicLink()) {
          servedPath = indexPath;
        }
      } catch {
        servedPath = indexPath;
      }
      const body = await readFile(servedPath);
      response.writeHead(200, {
        'Cache-Control': 'no-store',
        'Content-Length': String(body.length),
        'Content-Type': contentType(servedPath),
      });
      if (request.method === 'HEAD') {
        response.end();
      } else {
        response.end(body);
      }
    } catch {
      response.writeHead(500);
      response.end();
    }
  });
  await new Promise((resolvePromise, rejectPromise) => {
    server.once('error', () => rejectPromise(new TracerValidationError('PRODUCTION_SERVER', 'bind falhou.')));
    server.listen(4200, '127.0.0.1', resolvePromise);
  });
  return server;
}

async function closeServer(server) {
  if (server === undefined || !server.listening) {
    return;
  }
  await new Promise((resolvePromise, rejectPromise) => {
    const timeout = setTimeout(() => {
      server.closeAllConnections?.();
      rejectPromise(new TracerValidationError('RUNTIME_CLEANUP', 'servidor production nao fechou.'));
    }, 8_000);
    server.closeIdleConnections?.();
    server.close((error) => {
      clearTimeout(timeout);
      if (error) {
        rejectPromise(new TracerValidationError('RUNTIME_CLEANUP', 'servidor production nao fechou.'));
      } else {
        resolvePromise();
      }
    });
  });
}

async function removeOwnedTemporaryDirectory(path, nonce) {
  if (path === undefined) {
    return;
  }
  const expectedName = OWNED_BUILD_PREFIX + nonce;
  expect(
    dirname(path) === temporaryRoot && path === resolve(temporaryRoot, expectedName),
    'RUNTIME_CLEANUP',
    'diretorio temporario perdeu ownership.',
  );
  let metadata;
  try {
    metadata = await lstat(path);
  } catch (error) {
    if (error?.code === 'ENOENT') {
      return;
    }
    fail('RUNTIME_CLEANUP', 'diretorio temporario nao pode ser inspecionado.');
  }
  expect(
    metadata.isDirectory() && !metadata.isSymbolicLink(),
    'RUNTIME_CLEANUP',
    'diretorio temporario deixou de ser regular.',
  );
  const realTemporaryRoot = await realpath(temporaryRoot);
  const realOwnedPath = await realpath(path);
  expect(
    dirname(realOwnedPath) === realTemporaryRoot,
    'RUNTIME_CLEANUP',
    'diretorio temporario resolveu fora do parent controlado.',
  );
  await rm(path, { recursive: true, force: true });
}

async function validate() {
  const controller = new AbortController();
  const signal = controller.signal;
  const nonce = randomUUID();
  const buildRoot = resolve(temporaryRoot, OWNED_BUILD_PREFIX + nonce);
  const state = {
    angular: undefined,
    backend: undefined,
    containers: [],
    productionServer: undefined,
    buildRoot: undefined,
    finalEvidence: undefined,
  };
  let interruptionCode = 1;
  let primaryError;
  const cleanupFailures = [];
  const timeout = setTimeout(() => {
    controller.abort(new TracerValidationError('GLOBAL_TIMEOUT', 'validacao excedeu o prazo global.'));
  }, GLOBAL_TIMEOUT_MILLISECONDS);
  timeout.unref?.();
  const onSigint = () => {
    interruptionCode = 130;
    controller.abort(new TracerValidationError('INTERRUPTED', 'validacao interrompida por SIGINT.'));
  };
  const onSigterm = () => {
    interruptionCode = 143;
    controller.abort(new TracerValidationError('INTERRUPTED', 'validacao interrompida por SIGTERM.'));
  };
  process.once('SIGINT', onSigint);
  process.once('SIGTERM', onSigterm);

  try {
    expect(await realpath(projectRoot) === projectRoot, 'ROOT', 'raiz real divergiu.');
    await validateSupplyChain();
    await validateBrowserSources();
    await validateStaticContract(projectRoot);
    await runCaptured(
      process.execPath,
      [resolve(scriptsDirectory, 'validate-keycloak-theme.mjs'), '--local-only'],
      {
        cwd: projectRoot,
        rule: 'KEYCLOAK_THEME_STATIC',
        signal,
        timeoutMilliseconds: 30_000,
      },
    );
    await runCaptured(process.execPath, [resolve(scriptsDirectory, 'validate-angular-route-boundary.test.mjs')], {
      cwd: projectRoot,
      rule: 'ROUTE_BOUNDARY_TEST',
      signal,
      timeoutMilliseconds: 120_000,
    });
    await runCaptured(process.execPath, [resolve(scriptsDirectory, 'validate-angular-route-boundary.mjs')], {
      cwd: projectRoot,
      rule: 'ROUTE_BOUNDARY',
      signal,
      timeoutMilliseconds: 60_000,
    });
    console.log('[validate-tracer] PASS: contratos estaticos e supply chain.');
    await validateCapturedTreeTermination(signal);
    console.log('[validate-tracer] PASS: encerramento recursivo de processos.');

    const angularCli = resolve(frontendRoot, 'node_modules/@angular/cli/bin/ng.js');
    await readRegularFile(angularCli, 'ANGULAR_CLI');
    await runCaptured(
      process.execPath,
      [
        angularCli,
        'test',
        '--watch=false',
        '--include',
        'src/app/features/tracer/tracer-page.component.spec.ts',
      ],
      {
        cwd: frontendRoot,
        rule: 'ANGULAR_TEST',
        signal,
        timeoutMilliseconds: 120_000,
      },
    );
    console.log('[validate-tracer] PASS: teste focal Angular.');

    await runCaptured(
      process.execPath,
      [
        resolve(scriptsDirectory, 'maven.mjs'),
        '-o',
        '--batch-mode',
        '--no-transfer-progress',
        '-f',
        resolve(backendRoot, 'pom.xml'),
        '-DskipTests',
        'package',
      ],
      {
        cwd: projectRoot,
        rule: 'BACKEND_PACKAGE',
        signal,
        timeoutMilliseconds: 300_000,
      },
    );
    const jarBefore = await findBackendJar();
    console.log('[validate-tracer] PASS: backend empacotado offline.');

    await mkdir(temporaryRoot, { recursive: true });
    let existingBuildRoot = false;
    try {
      await lstat(buildRoot);
      existingBuildRoot = true;
    } catch {
      // O nonce novo deve apontar para um path ainda ausente.
    }
    expect(!existingBuildRoot, 'BUILD_OUTPUT', 'path temporario ja existia.');
    await mkdir(buildRoot);
    state.buildRoot = buildRoot;
    const buildEvidence = Object.create(null);
    for (const configuration of ['development', 'homolog', 'production']) {
      const outputPath = resolve(buildRoot, configuration);
      const args = [
        angularCli,
        'build',
        '--configuration',
        configuration,
        '--output-path',
        outputPath,
      ];
      if (configuration === 'production') {
        args.push('--stats-json');
      }
      await runCaptured(process.execPath, args, {
        cwd: frontendRoot,
        rule: 'BUILD_' + configuration.toUpperCase(),
        signal,
        timeoutMilliseconds: 180_000,
      });
      buildEvidence[configuration] = await inspectBuild(
        outputPath,
        configuration === 'production',
      );
      console.log('[validate-tracer] PASS: build ' + configuration + '.');
    }

    await validateDockerContext(signal);
    await assertPortFree(8080);
    await assertPortFree(4200);
    state.containers = await discoverFrozenContainers(signal);
    await startFrozenContainers(state.containers, signal);
    const environmentSource = await readRegularFile(
      resolve(projectRoot, 'infra/.env.example'),
      'RUNTIME_ENV',
    );
    const values = parseEnvironment(environmentSource);
    const discovery = await requestLocal(
      KEYCLOAK_RUNTIME_ORIGIN + '/realms/betufu-local/.well-known/openid-configuration',
      {
        allowedOrigins: new Set([KEYCLOAK_RUNTIME_ORIGIN]),
        timeoutMilliseconds: 10_000,
        signal,
      },
    );
    expect(discovery.status === 200, 'RUNTIME_KEYCLOAK', 'discovery Keycloak falhou.');

    const backendEnvironment = buildBackendEnvironment(values, nonce, process.env);
    expect(
      backendEnvironment.SPRING_AUTOCONFIGURE_EXCLUDE === OLD_REDIS_EXCLUSION,
      'RUNTIME_REDIS',
      'base historica da prova Redis divergiu.',
    );
    delete backendEnvironment.SPRING_AUTOCONFIGURE_EXCLUDE;
    expect(
      !Object.hasOwn(backendEnvironment, 'SPRING_AUTOCONFIGURE_EXCLUDE'),
      'RUNTIME_REDIS',
      'Spring Session Redis continuou excluido.',
    );
    state.backend = spawnOwned('java', ['-jar', jarBefore.path], {
      cwd: backendRoot,
      env: backendEnvironment,
      rule: 'RUNTIME_BACKEND',
    });
    await waitForBackendReady(state.backend, signal, 90_000);

    const frontendEnvironment = safeEnvironment();
    state.angular = spawnOwned(
      process.execPath,
      [
        angularCli,
        'serve',
        '--configuration',
        'development',
        '--host',
        '127.0.0.1',
        '--port',
        '4200',
        '--proxy-config',
        'proxy.conf.json',
      ],
      {
        cwd: frontendRoot,
        env: frontendEnvironment,
        rule: 'RUNTIME_FRONTEND',
      },
    );
    await waitForFrontend(state.angular, signal);
    const walkingOutput = resolve(buildRoot, 'playwright-walking');
    assertInside(buildRoot, walkingOutput, 'BROWSER_OUTPUT');
    await runPlaywright('walking-skeleton', values, walkingOutput, signal);
    console.log('[validate-tracer] PASS: E2E hospedado, D-15, XSRF e dois PUTs.');

    await stopOwned(state.angular);
    state.angular = undefined;
    await stopOwned(state.backend);
    state.backend = undefined;
    await cleanupFrozenContainers(state.containers);
    state.containers = [];
    await assertPortFree(4200);

    state.productionServer = await createProductionServer(buildEvidence.production.browserRoot);
    const productionOutput = resolve(buildRoot, 'playwright-production');
    assertInside(buildRoot, productionOutput, 'BROWSER_OUTPUT');
    await runPlaywright('production-absence', values, productionOutput, signal);
    console.log('[validate-tracer] PASS: ausencia production estatica e no browser.');
    await closeServer(state.productionServer);
    state.productionServer = undefined;

    const jarAfter = await findBackendJar();
    expect(
      jarAfter.path === jarBefore.path
        && jarAfter.size === jarBefore.size
        && jarAfter.sha256 === jarBefore.sha256,
      'BACKEND_JAR',
      'JAR mudou durante a prova runtime.',
    );
    state.finalEvidence = {
      status: 'ok',
      playwright: { version: PLAYWRIGHT_VERSION, chromiumRevision: CHROMIUM_REVISION },
      builds: Object.fromEntries(
        Object.entries(buildEvidence).map(([name, evidence]) => [name, {
          files: evidence.fileCount,
          bytes: evidence.totalBytes,
          sha256: evidence.sha256,
        }]),
      ),
      jar: { bytes: jarBefore.size, sha256: jarBefore.sha256 },
      services: FROZEN_SERVICES.map((service) => service.name),
      cleanup: 'complete',
    };
  } catch (error) {
    primaryError = error;
  } finally {
    clearTimeout(timeout);
    process.removeListener('SIGINT', onSigint);
    process.removeListener('SIGTERM', onSigterm);
    const cleanupSteps = [
      ['production-server', () => closeServer(state.productionServer)],
      ['angular-tree', () => stopOwned(state.angular)],
      ['backend-tree', () => stopOwned(state.backend)],
      ['containers', () => cleanupFrozenContainers(state.containers)],
      ['temporary-directory', () => removeOwnedTemporaryDirectory(state.buildRoot, nonce)],
    ];
    for (const [label, cleanup] of cleanupSteps) {
      try {
        await cleanup();
      } catch {
        cleanupFailures.push(label);
      }
    }
  }

  if (signal.aborted && interruptionCode !== 1) {
    process.exitCode = interruptionCode;
  }
  if (cleanupFailures.length > 0) {
    throw new TracerValidationError(
      'RUNTIME_CLEANUP',
      'falharam etapas controladas: ' + cleanupFailures.join(', ') + '.',
    );
  }
  if (primaryError !== undefined) {
    throw primaryError;
  }
  console.log(JSON.stringify(state.finalEvidence));
}

try {
  await validate();
} catch (error) {
  const message = error instanceof TracerValidationError
    || error instanceof RuntimePrerequisiteContractError
    ? error.message
    : 'INTERNAL_ERROR: falha interna durante a validacao.';
  console.error('[validate-tracer] ERRO: ' + message);
  if (process.exitCode === undefined) {
    process.exitCode = 1;
  }
}
