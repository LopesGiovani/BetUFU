#!/usr/bin/env node

import { readFile } from 'node:fs/promises';
import { dirname, posix, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { isDeepStrictEqual } from 'node:util';

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
const defaultProjectRoot = resolve(scriptsDirectory, '..');

const PATHS = Object.freeze({
  angular: 'frontend/angular.json',
  main: 'frontend/src/main.ts',
  config: 'frontend/src/app/app.config.ts',
  seam: 'frontend/src/app/app.routes.ts',
  development: 'frontend/src/app/app.routes.development.ts',
  production: 'frontend/src/app/app.routes.production.ts',
  component: 'frontend/src/app/app.component.ts',
  template: 'frontend/src/app/app.component.html',
});

const EXPECTED_REPLACEMENT = Object.freeze({
  replace: 'src/app/app.routes.development.ts',
  with: 'src/app/app.routes.production.ts',
});

const HOST_SOURCE_PATHS = Object.freeze([
  PATHS.main,
  PATHS.config,
  PATHS.seam,
  PATHS.development,
  PATHS.production,
  PATHS.component,
  PATHS.template,
]);

const FORBIDDEN_PATTERNS = Object.freeze([
  {
    rule: 'ABSOLUTE_HTTP_URL',
    label: 'URL HTTP(S) absoluta',
    pattern: /\bhttps?:\/\//iu,
  },
  {
    rule: 'AUTHORIZATION_HEADER',
    label: 'Authorization',
    pattern: /\bAuthorization\b/iu,
  },
  {
    rule: 'BEARER_TOKEN',
    label: 'Bearer',
    pattern: /\bBearer\b/iu,
  },
  {
    rule: 'LOCAL_STORAGE',
    label: 'localStorage',
    pattern: /\blocalStorage\b/u,
  },
  {
    rule: 'SESSION_STORAGE',
    label: 'sessionStorage',
    pattern: /\bsessionStorage\b/u,
  },
  {
    rule: 'INDEXED_DB',
    label: 'indexedDB',
    pattern: /\bindexedDB\b/u,
  },
  {
    rule: 'LEGACY_HTTP_INTERCEPTOR',
    label: 'HTTP_INTERCEPTORS',
    pattern: /\bHTTP_INTERCEPTORS\b/u,
  },
  {
    rule: 'HTTP_INTERCEPTOR',
    label: 'withInterceptors',
    pattern: /\bwithInterceptors(?:FromDi)?\b/u,
  },
  {
    rule: 'XSRF_DISABLED',
    label: 'withNoXsrfProtection',
    pattern: /\bwithNoXsrfProtection\b/u,
  },
]);

class RouteBoundaryContractError extends Error {
  constructor(rule, message) {
    super(`${rule}: ${message}`);
    this.name = 'RouteBoundaryContractError';
  }
}

function contractError(rule, message) {
  throw new RouteBoundaryContractError(rule, message);
}

function expect(condition, rule, message) {
  if (!condition) {
    contractError(rule, message);
  }
}

function expectPlainObject(value, rule, label) {
  expect(
    value !== null && typeof value === 'object' && !Array.isArray(value),
    rule,
    `${label} deve ser um objeto.`,
  );
}

function normalizeText(text) {
  return text.replaceAll('\r\n', '\n');
}

function stripScriptComments(source) {
  let result = '';
  let state = 'code';
  let quote = '';

  for (let index = 0; index < source.length; index += 1) {
    const current = source[index];
    const next = source[index + 1];

    if (state === 'line-comment') {
      if (current === '\n') {
        result += '\n';
        state = 'code';
      } else {
        result += ' ';
      }
      continue;
    }

    if (state === 'block-comment') {
      if (current === '*' && next === '/') {
        result += '  ';
        index += 1;
        state = 'code';
      } else {
        result += current === '\n' ? '\n' : ' ';
      }
      continue;
    }

    if (state === 'string') {
      result += current;
      if (current === '\\' && next !== undefined) {
        result += next;
        index += 1;
      } else if (current === quote) {
        state = 'code';
        quote = '';
      }
      continue;
    }

    if (current === '/' && next === '/') {
      result += '  ';
      index += 1;
      state = 'line-comment';
      continue;
    }

    if (current === '/' && next === '*') {
      result += '  ';
      index += 1;
      state = 'block-comment';
      continue;
    }

    if (current === "'" || current === '"' || current === '`') {
      result += current;
      quote = current;
      state = 'string';
      continue;
    }

    result += current;
  }

  return result;
}

function maskScriptStrings(source) {
  let result = '';
  let state = 'code';
  let quote = '';

  for (let index = 0; index < source.length; index += 1) {
    const current = source[index];
    const next = source[index + 1];

    if (state === 'string') {
      if (current === '\\' && next !== undefined) {
        result += '  ';
        index += 1;
      } else if (current === quote) {
        result += current;
        state = 'code';
        quote = '';
      } else {
        result += current === '\n' ? '\n' : ' ';
      }
      continue;
    }

    if (current === "'" || current === '"' || current === '`') {
      result += current;
      state = 'string';
      quote = current;
      continue;
    }

    result += current;
  }

  return result;
}

function stripHtmlComments(source) {
  return source.replace(/<!--[\s\S]*?-->/gu, '');
}

function countMatches(source, pattern) {
  const flags = pattern.flags.includes('g') ? pattern.flags : `${pattern.flags}g`;
  return [...source.matchAll(new RegExp(pattern.source, flags))].length;
}

function countActiveImports(source, pattern) {
  const executableSource = maskScriptStrings(source);
  const flags = pattern.flags.includes('g') ? pattern.flags : `${pattern.flags}g`;
  const matches = [...source.matchAll(new RegExp(pattern.source, flags))];

  return matches.filter((match) => {
    const importOffset = match[0].search(/\bimport\b/u);
    return importOffset >= 0
      && executableSource.slice(match.index + importOffset, match.index + importOffset + 6) === 'import';
  }).length;
}

function countActiveModuleReferences(source, executableSource, moduleSuffix) {
  const escapedSuffix = moduleSuffix.replace(/[.*+?^${}()|[\]\\]/gu, '\\$&');
  const pattern = new RegExp(
    `\\b(?:from\\s*|import\\s*(?:\\(\\s*)?)(['"\\x60])[^'"\\x60]*${escapedSuffix}\\1`,
    'gu',
  );

  return [...source.matchAll(pattern)].filter((match) => {
    const keyword = /\b(?:from|import)\b/u.exec(match[0]);
    return keyword !== null
      && executableSource.slice(
        match.index + keyword.index,
        match.index + keyword.index + keyword[0].length,
      ) === keyword[0];
  }).length;
}

function expectExactlyOnce(source, pattern, rule, label) {
  expect(
    countMatches(source, pattern) === 1,
    rule,
    `${label} deve aparecer exatamente uma vez em codigo ativo.`,
  );
}

function expectExactlyOnceActiveImport(source, pattern, rule, label) {
  expect(
    countActiveImports(source, pattern) === 1,
    rule,
    `${label} deve aparecer exatamente uma vez em codigo ativo.`,
  );
}

async function readRequiredText(projectRoot, relativePath) {
  try {
    return normalizeText(await readFile(resolve(projectRoot, relativePath), 'utf8'));
  } catch {
    contractError('READ_REQUIRED_FILE', `nao foi possivel ler ${relativePath}.`);
  }
}

async function readAngularConfiguration(projectRoot) {
  const text = await readRequiredText(projectRoot, PATHS.angular);

  try {
    return JSON.parse(text);
  } catch {
    contractError('ANGULAR_JSON', 'frontend/angular.json nao contem JSON valido.');
  }
}

function validateFileReplacements(angularConfiguration) {
  const configurations = angularConfiguration
    ?.projects
    ?.betufu
    ?.architect
    ?.build
    ?.configurations;

  expectPlainObject(configurations, 'ANGULAR_BUILD_CONFIG', 'build.configurations');

  const production = configurations.production;
  const homolog = configurations.homolog;
  const development = configurations.development;

  expectPlainObject(production, 'ANGULAR_BUILD_CONFIG', 'production');
  expectPlainObject(homolog, 'ANGULAR_BUILD_CONFIG', 'homolog');
  expectPlainObject(development, 'ANGULAR_BUILD_CONFIG', 'development');

  expect(
    isDeepStrictEqual(production.fileReplacements, [EXPECTED_REPLACEMENT]),
    'PRODUCTION_ROUTE_REPLACEMENT',
    'production deve substituir somente app.routes.development.ts por app.routes.production.ts.',
  );
  validateNonProductionRouteReplacements(homolog, 'HOMOLOG_ROUTE_REPLACEMENT', 'homolog');
  validateNonProductionRouteReplacements(
    development,
    'DEVELOPMENT_ROUTE_REPLACEMENT',
    'development',
  );
}

function validateNonProductionRouteReplacements(configuration, rule, label) {
  if (!Object.hasOwn(configuration, 'fileReplacements')) {
    return;
  }

  expect(
    Array.isArray(configuration.fileReplacements),
    rule,
    `${label}.fileReplacements deve ser uma lista quando declarado.`,
  );

  const normalizeWorkspacePath = (value) => (
    typeof value === 'string'
      ? posix.normalize(value.replaceAll('\\', '/')).toLowerCase()
      : null
  );
  const routeBoundaryPaths = new Set([
    normalizeWorkspacePath(EXPECTED_REPLACEMENT.replace),
    normalizeWorkspacePath(EXPECTED_REPLACEMENT.with),
  ]);
  const touchesRouteBoundary = configuration.fileReplacements.some((replacement) => (
    replacement !== null
    && typeof replacement === 'object'
    && (
      routeBoundaryPaths.has(normalizeWorkspacePath(replacement.replace))
      || routeBoundaryPaths.has(normalizeWorkspacePath(replacement.with))
    )
  ));

  expect(
    !touchesRouteBoundary,
    rule,
    `${label} nao pode substituir arquivos da fronteira de rotas.`,
  );
}

function validateAppConfiguration(source) {
  const code = stripScriptComments(source);
  const executableCode = maskScriptStrings(code);

  expectExactlyOnceActiveImport(
    code,
    /^\s*import\s*\{(?=[^}]*\bprovideHttpClient\b)(?=[^}]*\bwithXsrfConfiguration\b)[^}]*\}\s*from\s*['"]@angular\/common\/http['"]\s*;/mu,
    'HTTP_IMPORT',
    'import de provideHttpClient e withXsrfConfiguration',
  );
  expectExactlyOnceActiveImport(
    code,
    /^\s*import\s*\{\s*provideRouter\s*\}\s*from\s*['"]@angular\/router['"]\s*;/mu,
    'ROUTER_IMPORT',
    'import de provideRouter',
  );
  expectExactlyOnceActiveImport(
    code,
    /^\s*import\s*\{\s*APP_ROUTES\s*\}\s*from\s*['"]\.\/app\.routes['"]\s*;/mu,
    'APP_ROUTES_IMPORT',
    'import da seam APP_ROUTES',
  );
  expectExactlyOnce(
    executableCode,
    /\bprovideRouter\s*\(\s*APP_ROUTES\s*\)/u,
    'ROUTER_PROVIDER',
    'provideRouter(APP_ROUTES)',
  );
  expectExactlyOnce(
    executableCode,
    /\bprovideHttpClient\s*\(/u,
    'HTTP_PROVIDER',
    'provideHttpClient',
  );
  expectExactlyOnce(
    executableCode,
    /\bwithXsrfConfiguration\s*\(/u,
    'XSRF_PROVIDER',
    'withXsrfConfiguration',
  );

  const xsrfCallPattern = /\bprovideHttpClient\s*\(\s*withXsrfConfiguration\s*\(\s*\{[\s\S]*?\}\s*\)\s*,?\s*\)/u;
  const xsrfCallMatch = xsrfCallPattern.exec(executableCode);
  const xsrfCall = xsrfCallMatch === null
    ? undefined
    : code.slice(xsrfCallMatch.index, xsrfCallMatch.index + xsrfCallMatch[0].length);
  const xsrfOptions = xsrfCall?.match(/\{(?<options>[\s\S]*?)\}/u)?.groups?.options;

  expect(xsrfOptions !== undefined, 'XSRF_PROVIDER', 'configuracao XSRF deve estar dentro de provideHttpClient.');
  expectExactlyOnce(
    xsrfOptions,
    /\bcookieName\s*:\s*(['"])XSRF-TOKEN\1/u,
    'XSRF_COOKIE',
    'cookie XSRF-TOKEN',
  );
  expectExactlyOnce(
    xsrfOptions,
    /\bheaderName\s*:\s*(['"])X-XSRF-TOKEN\1/u,
    'XSRF_HEADER',
    'header X-XSRF-TOKEN',
  );

  const xsrfOptionsShape = xsrfOptions.trim();
  const cookieThenHeader = /^cookieName\s*:\s*(['"])XSRF-TOKEN\1\s*,\s*headerName\s*:\s*(['"])X-XSRF-TOKEN\2\s*,?$/u;
  const headerThenCookie = /^headerName\s*:\s*(['"])X-XSRF-TOKEN\1\s*,\s*cookieName\s*:\s*(['"])XSRF-TOKEN\2\s*,?$/u;
  expect(
    cookieThenHeader.test(xsrfOptionsShape) || headerThenCookie.test(xsrfOptionsShape),
    'XSRF_OPTIONS_SHAPE',
    'withXsrfConfiguration deve conter somente cookieName e headerName aprovados.',
  );
}

function expectRoutesTypeImport(source, relativePath) {
  expectExactlyOnceActiveImport(
    source,
    /^\s*import\s+type\s*\{\s*Routes\s*\}\s*from\s*['"]@angular\/router['"]\s*;/mu,
    'ROUTES_TYPE_IMPORT',
    `import type Routes em ${relativePath}`,
  );
}

function validateRouteSources(sources) {
  const seam = stripScriptComments(sources.get(PATHS.seam));
  const development = stripScriptComments(sources.get(PATHS.development));
  const production = stripScriptComments(sources.get(PATHS.production));
  const scriptSources = [...sources.entries()]
    .filter(([relativePath]) => relativePath.endsWith('.ts'))
    .map(([relativePath, source]) => {
      const code = stripScriptComments(source);
      return [relativePath, code, maskScriptStrings(code)];
    });

  for (const [relativePath, source, executableSource] of scriptSources) {
    expect(
      countActiveModuleReferences(source, executableSource, 'app.routes.production') === 0,
      'DIRECT_PRODUCTION_IMPORT',
      `${relativePath} nao pode importar app.routes.production diretamente.`,
    );
  }

  expectRoutesTypeImport(seam, PATHS.seam);
  expectRoutesTypeImport(development, PATHS.development);
  expectRoutesTypeImport(production, PATHS.production);

  expectExactlyOnceActiveImport(
    seam,
    /^\s*import\s*\{\s*BUILD_SPECIFIC_ROUTES\s*\}\s*from\s*['"]\.\/app\.routes\.development['"]\s*;/mu,
    'DEVELOPMENT_ROUTE_IMPORT',
    'import da lista development pela seam',
  );
  expectExactlyOnce(
    maskScriptStrings(seam),
    /\bexport\s+const\s+APP_ROUTES\s*:\s*Routes\s*=\s*\[\s*\.\.\.\s*BUILD_SPECIFIC_ROUTES\s*\]\s*;/u,
    'APP_ROUTES_EXPORT',
    'export APP_ROUTES: Routes',
  );
  expectExactlyOnce(
    maskScriptStrings(development),
    /\bexport\s+const\s+BUILD_SPECIFIC_ROUTES\s*:\s*Routes\s*=\s*\[/u,
    'DEVELOPMENT_ROUTES_EXPORT',
    'export BUILD_SPECIFIC_ROUTES: Routes em development',
  );
  expectExactlyOnce(
    maskScriptStrings(production),
    /\bexport\s+const\s+BUILD_SPECIFIC_ROUTES\s*:\s*Routes\s*=\s*\[\s*\]\s*;/u,
    'PRODUCTION_ROUTES_EXPORT',
    'lista production vazia e tipada',
  );
  expect(
    countMatches(maskScriptStrings(production), /\bBUILD_SPECIFIC_ROUTES\b/u) === 1,
    'PRODUCTION_ROUTES_EMPTY',
    'a lista production deve permanecer vazia e nao pode ser alterada depois da declaracao.',
  );

  for (const [relativePath, source, executableSource] of scriptSources) {
    const developmentImportCount = countActiveModuleReferences(
      source,
      executableSource,
      'app.routes.development',
    );
    const expectedCount = relativePath === PATHS.seam ? 1 : 0;
    expect(
      developmentImportCount === expectedCount,
      'ROUTE_SEAM_UNIQUENESS',
      'app.routes.ts deve ser a unica seam que importa a lista development.',
    );
  }

  for (const [relativePath, , executableSource] of scriptSources) {
    const expectedAppRoutesCount = relativePath === PATHS.config
      ? 2
      : Number(relativePath === PATHS.seam);
    expect(
      countMatches(executableSource, /\bAPP_ROUTES\b/u) === expectedAppRoutesCount,
      'APP_ROUTES_USAGE',
      'APP_ROUTES deve aparecer somente na declaracao da seam e no provider Router.',
    );
  }
}

function validateForbiddenPatterns(sources) {
  for (const [relativePath, source] of sources) {
    const activeSource = relativePath.endsWith('.html')
      ? stripHtmlComments(source)
      : stripScriptComments(source);

    for (const forbiddenPattern of FORBIDDEN_PATTERNS) {
      expect(
        !forbiddenPattern.pattern.test(activeSource),
        forbiddenPattern.rule,
        `${forbiddenPattern.label} nao e permitido no host (${relativePath}).`,
      );
    }
  }
}

export async function validateAngularRouteBoundary(projectRoot = defaultProjectRoot) {
  const resolvedProjectRoot = resolve(projectRoot);
  const angularConfiguration = await readAngularConfiguration(resolvedProjectRoot);
  const sources = new Map();

  for (const relativePath of HOST_SOURCE_PATHS) {
    sources.set(relativePath, await readRequiredText(resolvedProjectRoot, relativePath));
  }

  validateFileReplacements(angularConfiguration);
  validateAppConfiguration(sources.get(PATHS.config));
  validateRouteSources(sources);
  validateForbiddenPatterns(sources);

  return true;
}

function parseProjectRoot(argumentsList) {
  if (argumentsList.length === 0) {
    return defaultProjectRoot;
  }

  if (
    argumentsList.length === 2
    && argumentsList[0] === '--root'
    && argumentsList[1].trim() !== ''
  ) {
    return resolve(argumentsList[1]);
  }

  return null;
}

function sanitizeError(error) {
  if (error instanceof RouteBoundaryContractError) {
    return error.message;
  }

  return 'INTERNAL_ERROR: falha interna durante a validacao.';
}

async function main() {
  const projectRoot = parseProjectRoot(process.argv.slice(2));

  if (projectRoot === null) {
    console.error('Uso: node scripts/validate-angular-route-boundary.mjs [--root <project-root>]');
    process.exitCode = 2;
    return;
  }

  try {
    await validateAngularRouteBoundary(projectRoot);
    console.log('[validate-angular-route-boundary] OK: fronteira Angular segue o contrato aprovado.');
  } catch (error) {
    console.error(`[validate-angular-route-boundary] ERRO: ${sanitizeError(error)}`);
    process.exitCode = 1;
  }
}

const invokedAsScript = process.argv[1]
  && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (invokedAsScript) {
  await main();
}
