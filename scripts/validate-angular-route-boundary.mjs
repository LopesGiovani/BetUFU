#!/usr/bin/env node

import { readFile } from 'node:fs/promises';
import { dirname, posix, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { isDeepStrictEqual } from 'node:util';
import {
  APPROVED_GENERATED_DIGEST,
  APPROVED_GENERATED_FILES,
  readTreeManifest,
} from '../frontend/scripts/generate-api.mjs';

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
  tracerComponent: 'frontend/src/app/features/tracer/tracer-page.component.ts',
  tracerTemplate: 'frontend/src/app/features/tracer/tracer-page.component.html',
  runtimeRoot: 'frontend/src',
  generatedRoot: 'frontend/src/app/api/generated',
  generatedBaseService: 'frontend/src/app/api/generated/api.base.service.ts',
  generatedApiModule: 'frontend/src/app/api/generated/api.module.ts',
  generatedConfiguration: 'frontend/src/app/api/generated/configuration.ts',
  generatedIdentityService: 'frontend/src/app/api/generated/api/identity.service.ts',
  generatedProvider: 'frontend/src/app/api/generated/provide-api.ts',
});

const EXPECTED_REPLACEMENT = Object.freeze({
  replace: 'src/app/app.routes.development.ts',
  with: 'src/app/app.routes.production.ts',
});

const CANONICAL_TRACER_MODULE_PATTERN_SOURCE =
  String.raw`'\./features/tracer/tracer-page\.component'`;
const CANONICAL_TRACER_DYNAMIC_IMPORT_PATTERN_SOURCE = [
  String.raw`\bimport\s*\(\s*`,
  CANONICAL_TRACER_MODULE_PATTERN_SOURCE,
  String.raw`\s*\)`,
].join('');
const CANONICAL_TRACER_DYNAMIC_IMPORT_PATTERN = new RegExp(
  CANONICAL_TRACER_DYNAMIC_IMPORT_PATTERN_SOURCE,
  'u',
);
const BUILD_SPECIFIC_ROUTES_PREFIX_PATTERN_SOURCE = [
  String.raw`\s*import\s+type\s*\{\s*Routes\s*\}\s*from\s*'@angular/router'\s*;`,
  String.raw`\s*export\s+const\s+BUILD_SPECIFIC_ROUTES\s*:\s*Routes\s*=\s*\[`,
].join('');
const EMPTY_BUILD_SPECIFIC_ROUTES_PATTERN = new RegExp(
  [
    '^',
    BUILD_SPECIFIC_ROUTES_PREFIX_PATTERN_SOURCE,
    String.raw`\s*\]\s*;\s*$`,
  ].join(''),
  'u',
);
const CANONICAL_TRACER_ROUTES_PATTERN = new RegExp(
  [
    '^',
    BUILD_SPECIFIC_ROUTES_PREFIX_PATTERN_SOURCE,
    String.raw`\s*\{`,
    String.raw`\s*path\s*:\s*'tracer'\s*,`,
    String.raw`\s*loadComponent\s*:\s*\(\s*\)\s*=>\s*`,
    CANONICAL_TRACER_DYNAMIC_IMPORT_PATTERN_SOURCE,
    String.raw`\s*\.then\s*\(`,
    String.raw`\s*\(\s*\{\s*TracerPageComponent\s*\}\s*\)`,
    String.raw`\s*=>\s*TracerPageComponent\s*,?\s*\)\s*,?`,
    String.raw`\s*\}\s*,?\s*\]\s*;\s*$`,
  ].join(''),
  'u',
);

const HOST_SOURCE_PATHS = Object.freeze([
  PATHS.main,
  PATHS.config,
  PATHS.seam,
  PATHS.development,
  PATHS.production,
  PATHS.component,
  PATHS.template,
  PATHS.tracerComponent,
  PATHS.tracerTemplate,
]);

const TRACER_FEATURE_PREFIX = 'frontend/src/app/features/tracer/';
const TRACER_RUNTIME_EXTENSIONS = Object.freeze(['.html', '.ts']);
const TRACER_DIRECT_CLIENT_PATTERNS = Object.freeze([
  {
    rule: 'TRACER_FETCH_FORBIDDEN',
    label: 'fetch direto',
    target: 'executable',
    pattern: /\bfetch\b/u,
  },
  {
    rule: 'TRACER_HTTP_CLIENT_FORBIDDEN',
    label: 'HttpClient direto',
    target: 'executable',
    pattern: /\bHttpClient\b/u,
  },
  {
    rule: 'TRACER_HTTP_CLIENT_FORBIDDEN',
    label: 'import direto de @angular/common/http',
    target: 'decoded',
    pattern: /['"]@angular\/common\/http['"]/u,
  },
  {
    rule: 'TRACER_RETRY_FORBIDDEN',
    label: 'retry automatico',
    target: 'executable',
    pattern: /\bretry(?:When)?\b/u,
  },
  {
    rule: 'TRACER_XSRF_MANUAL_FORBIDDEN',
    label: 'XSRF/CSRF manual',
    target: 'decoded',
    pattern: /\b(?:XSRF|CSRF)\b/iu,
  },
  {
    rule: 'TRACER_ENDPOINT_MANUAL_FORBIDDEN',
    label: 'endpoint relativo montado manualmente',
    target: 'decoded',
    pattern: /(?:^|['"\x60])\s*\/{1,2}(?:api|oauth2|login)(?=\/|['"\x60]|\s|$)/imu,
  },
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
  {
    rule: 'WITH_CREDENTIALS',
    label: 'withCredentials',
    pattern: /\bwithCredentials\b/u,
  },
  {
    rule: 'MANUAL_COOKIE',
    label: 'Cookie manual',
    pattern: /\bCookie\b/iu,
  },
  {
    rule: 'ACCESS_TOKEN',
    label: 'accessToken',
    pattern: /\baccessToken\b/u,
  },
]);

const GENERATED_ACTIVE_AUTH_PATTERNS = Object.freeze([
  {
    rule: 'GENERATED_AUTHORIZATION',
    label: 'Authorization',
    pattern: /\bAuthorization\b/iu,
  },
  {
    rule: 'GENERATED_BEARER',
    label: 'Bearer',
    pattern: /\bBearer\b/iu,
  },
  {
    rule: 'GENERATED_MANUAL_COOKIE',
    label: 'Cookie manual',
    pattern: /\bCookie\b/iu,
  },
  {
    rule: 'GENERATED_AUTH_CONFIGURATION',
    label: 'configuracao ativa de autenticacao',
    pattern: /\b(?:accessToken|apiKeys|credentials|username|password)\b/u,
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
  return text.replace(/\r\n?|\u2028|\u2029/gu, '\n');
}

function maskCharacter(character) {
  return character === '\n' ? '\n' : ' ';
}

function previousNonWhitespaceIndex(source, startIndex) {
  let cursor = startIndex;
  while (cursor >= 0 && /\s/u.test(source[cursor])) {
    cursor -= 1;
  }
  return cursor;
}

function transformScriptSource(source, { maskLiterals = false, stripComments = false } = {}) {
  let result = '';
  const contexts = [{ type: 'code', templateExpression: false, braceDepth: 0 }];

  for (let index = 0; index < source.length; index += 1) {
    const current = source[index];
    const next = source[index + 1];
    const context = contexts.at(-1);

    if (context.type === 'line-comment') {
      if (current === '\n') {
        result += '\n';
        contexts.pop();
      } else {
        result += ' ';
      }
      continue;
    }

    if (context.type === 'block-comment') {
      if (current === '*' && next === '/') {
        result += '  ';
        index += 1;
        contexts.pop();
      } else {
        result += maskCharacter(current);
      }
      continue;
    }

    if (context.type === 'string') {
      if (current === '\\' && next !== undefined) {
        result += maskLiterals ? `${maskCharacter(current)}${maskCharacter(next)}` : `${current}${next}`;
        index += 1;
      } else if (current === context.quote) {
        result += current;
        contexts.pop();
      } else {
        result += maskLiterals ? maskCharacter(current) : current;
      }
      continue;
    }

    if (context.type === 'template') {
      if (current === '\\' && next !== undefined) {
        result += maskLiterals ? `${maskCharacter(current)}${maskCharacter(next)}` : `${current}${next}`;
        index += 1;
      } else if (current === '`') {
        result += current;
        contexts.pop();
      } else if (current === '$' && next === '{') {
        result += '${';
        index += 1;
        contexts.push({ type: 'code', templateExpression: true, braceDepth: 1 });
      } else {
        result += maskLiterals ? maskCharacter(current) : current;
      }
      continue;
    }

    if (stripComments && current === '/' && next === '/') {
      result += '  ';
      index += 1;
      contexts.push({ type: 'line-comment' });
      continue;
    }

    if (stripComments && current === '/' && next === '*') {
      result += '  ';
      index += 1;
      contexts.push({ type: 'block-comment' });
      continue;
    }

    if (current === "'" || current === '"') {
      result += current;
      contexts.push({ type: 'string', quote: current });
      continue;
    }

    if (current === '`') {
      result += current;
      contexts.push({ type: 'template' });
      continue;
    }

    if (context.templateExpression && current === '{') {
      context.braceDepth += 1;
    } else if (context.templateExpression && current === '}') {
      context.braceDepth -= 1;
      result += current;
      if (context.braceDepth === 0) {
        contexts.pop();
      }
      continue;
    }

    result += current;
  }

  return result;
}

function stripScriptComments(source) {
  return transformScriptSource(source, { stripComments: true });
}

function maskScriptStrings(source) {
  return transformScriptSource(source, { maskLiterals: true });
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

function validateScriptLexicalSafety(sources) {
  for (const [relativePath, source] of sources) {
    if (!relativePath.endsWith('.ts')) {
      continue;
    }

    const code = stripScriptComments(source);
    const executableCode = maskScriptStrings(code);
    expect(
      !executableCode.includes('/'),
      'UNSUPPORTED_SCRIPT_SLASH',
      `${relativePath} nao pode conter regex ou divisao; barras sao aceitas somente em strings e comentarios.`,
    );
    expect(
      !code.includes('\\'),
      'UNSUPPORTED_SCRIPT_ESCAPE',
      `${relativePath} nao pode conter escape fora de comentario; o subconjunto do host nao aceita escapes em codigo nem strings.`,
    );
    const dynamicImportCount = countMatches(executableCode, /\bimport\s*\(/u);

    if (relativePath === PATHS.development) {
      const canonicalTracerImportCount = countActiveImports(
        code,
        CANONICAL_TRACER_DYNAMIC_IMPORT_PATTERN,
      );
      expect(
        dynamicImportCount === canonicalTracerImportCount
          && dynamicImportCount <= 1,
        'DYNAMIC_IMPORT_FORBIDDEN',
        `${relativePath} aceita no maximo o import dinamico literal canonico do tracer.`,
      );
    } else {
      expect(
        dynamicImportCount === 0,
        'DYNAMIC_IMPORT_FORBIDDEN',
        `${relativePath} nao pode usar import dinamico fora da seam development aprovada.`,
      );
    }
  }
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

function findMatchingDelimiter(source, openIndex, openDelimiter, closeDelimiter) {
  let depth = 0;

  for (let index = openIndex; index < source.length; index += 1) {
    if (source[index] === openDelimiter) {
      depth += 1;
    } else if (source[index] === closeDelimiter) {
      depth -= 1;
      if (depth === 0) {
        return index;
      }
    }
  }

  return -1;
}

function findTopLevelArrayProperties(source, objectOpenIndex, objectCloseIndex, propertyName) {
  const matches = [];
  let curlyDepth = 0;
  let squareDepth = 0;
  let parenthesisDepth = 0;
  const propertyPattern = new RegExp(`^${propertyName}\\s*:\\s*\\[`, 'u');

  for (let index = objectOpenIndex + 1; index < objectCloseIndex; index += 1) {
    const current = source[index];

    if (
      curlyDepth === 0
      && squareDepth === 0
      && parenthesisDepth === 0
      && current === propertyName[0]
      && !/[\p{ID_Continue}$]/u.test(source[index - 1] ?? '')
    ) {
      const propertyMatch = propertyPattern.exec(source.slice(index));
      if (propertyMatch !== null) {
        matches.push(index + propertyMatch[0].lastIndexOf('['));
      }
    }

    if (current === '{') {
      curlyDepth += 1;
    } else if (current === '}') {
      curlyDepth -= 1;
    } else if (current === '[') {
      squareDepth += 1;
    } else if (current === ']') {
      squareDepth -= 1;
    } else if (current === '(') {
      parenthesisDepth += 1;
    } else if (current === ')') {
      parenthesisDepth -= 1;
    }
  }

  return matches;
}

function extractAppConfigProviders(code, executableCode) {
  const declarationPattern = /\bexport\s+const\s+appConfig\s*:\s*ApplicationConfig\s*=\s*\{/gu;
  const declarations = [...executableCode.matchAll(declarationPattern)];

  expect(
    declarations.length === 1,
    'APP_CONFIG_DECLARATION',
    'appConfig: ApplicationConfig deve ser declarado exatamente uma vez.',
  );

  const declaration = declarations[0];
  const objectOpenIndex = declaration.index + declaration[0].lastIndexOf('{');
  const objectCloseIndex = findMatchingDelimiter(executableCode, objectOpenIndex, '{', '}');

  expect(
    objectCloseIndex >= 0,
    'APP_CONFIG_DECLARATION',
    'o objeto appConfig deve possuir fechamento valido.',
  );

  const providerArrays = findTopLevelArrayProperties(
    executableCode,
    objectOpenIndex,
    objectCloseIndex,
    'providers',
  );
  expect(
    providerArrays.length === 1,
    'APP_CONFIG_PROVIDERS',
    'appConfig deve declarar exatamente um array providers no nivel principal.',
  );

  const providersOpenIndex = providerArrays[0];
  const providersCloseIndex = findMatchingDelimiter(
    executableCode,
    providersOpenIndex,
    '[',
    ']',
  );
  expect(
    providersCloseIndex >= 0 && providersCloseIndex < objectCloseIndex,
    'APP_CONFIG_PROVIDERS',
    'appConfig.providers deve possuir fechamento valido dentro de appConfig.',
  );

  const objectPrefix = executableCode.slice(objectOpenIndex + 1, providersOpenIndex);
  const objectSuffix = executableCode.slice(providersCloseIndex + 1, objectCloseIndex);
  expect(
    /^\s*providers\s*:\s*$/u.test(objectPrefix)
      && /^\s*,?\s*$/u.test(objectSuffix),
    'APP_CONFIG_SHAPE',
    'appConfig deve conter somente a propriedade canonica providers, sem spread ou override.',
  );

  return {
    code: code.slice(providersOpenIndex, providersCloseIndex + 1),
    executableCode: executableCode.slice(providersOpenIndex, providersCloseIndex + 1),
  };
}

function expectExactlyOnceInsideProviders(
  executableCode,
  providersExecutableCode,
  pattern,
  rule,
  label,
) {
  expect(
    countMatches(executableCode, pattern) === 1
      && countMatches(providersExecutableCode, pattern) === 1,
    rule,
    `${label} deve aparecer exatamente uma vez e dentro de appConfig.providers.`,
  );
}

function isDirectProviderArrayElement(source, match) {
  let curlyDepth = 0;
  let squareDepth = 0;
  let parenthesisDepth = 0;

  for (let index = 1; index < match.index; index += 1) {
    const current = source[index];
    if (current === '{') {
      curlyDepth += 1;
    } else if (current === '}') {
      curlyDepth -= 1;
    } else if (current === '[') {
      squareDepth += 1;
    } else if (current === ']') {
      squareDepth -= 1;
    } else if (current === '(') {
      parenthesisDepth += 1;
    } else if (current === ')') {
      parenthesisDepth -= 1;
    }
  }

  if (curlyDepth !== 0 || squareDepth !== 0 || parenthesisDepth !== 0) {
    return false;
  }

  const previousIndex = previousNonWhitespaceIndex(source, match.index - 1);
  const nextIndex = (() => {
    let cursor = match.index + match[0].length;
    while (cursor < source.length && /\s/u.test(source[cursor])) {
      cursor += 1;
    }
    return cursor;
  })();

  return '[,'.includes(source[previousIndex])
    && ',]'.includes(source[nextIndex]);
}

function expectExactlyOneDirectProvider(
  executableCode,
  providersExecutableCode,
  pattern,
  rule,
  label,
) {
  const flags = pattern.flags.includes('g') ? pattern.flags : `${pattern.flags}g`;
  const globalMatches = [...executableCode.matchAll(new RegExp(pattern.source, flags))];
  const providerMatches = [
    ...providersExecutableCode.matchAll(new RegExp(pattern.source, flags)),
  ];

  expect(
    globalMatches.length === 1
      && providerMatches.length === 1
      && isDirectProviderArrayElement(providersExecutableCode, providerMatches[0]),
    rule,
    `${label} deve aparecer exatamente uma vez como elemento direto de appConfig.providers.`,
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

async function readGeneratedTypeScriptSources(projectRoot) {
  const generatedRoot = resolve(projectRoot, PATHS.generatedRoot);
  let manifest;

  try {
    manifest = await readTreeManifest(generatedRoot);
  } catch {
    contractError(
      'GENERATED_MANIFEST_READ',
      `nao foi possivel ler com seguranca ${PATHS.generatedRoot}.`,
    );
  }

  expect(
    isDeepStrictEqual(manifest.files, APPROVED_GENERATED_FILES),
    'GENERATED_MANIFEST_PATHS',
    'a arvore gerada deve conter exatamente os 13 caminhos aprovados, na ordem canonica.',
  );
  expect(
    manifest.digest === APPROVED_GENERATED_DIGEST,
    'GENERATED_MANIFEST_DIGEST',
    `a arvore gerada deve possuir o digest canonico ${APPROVED_GENERATED_DIGEST}.`,
  );

  return new Map(manifest.entries.map(({ path, bytes }) => [
    `${PATHS.generatedRoot}/${path}`,
    normalizeText(bytes.toString('utf8')),
  ]));
}

async function readFrontendRuntimeSources(projectRoot) {
  let manifest;

  try {
    manifest = await readTreeManifest(resolve(projectRoot, PATHS.runtimeRoot));
  } catch {
    contractError(
      'RUNTIME_MANIFEST_READ',
      `nao foi possivel ler com seguranca ${PATHS.runtimeRoot}.`,
    );
  }

  return new Map(
    manifest.entries
      .filter(({ path }) => (
        TRACER_RUNTIME_EXTENSIONS.some((extension) => path.endsWith(extension))
        && !/\.(?:spec|test)\.ts$/u.test(path)
        && !path.startsWith('app/api/generated/')
      ))
      .map(({ path, bytes }) => [
        `${PATHS.runtimeRoot}/${path}`,
        normalizeText(bytes.toString('utf8')),
      ]),
  );
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
  const providers = extractAppConfigProviders(code, executableCode);
  const routerCallPattern = /\bprovideRouter\s*\(\s*APP_ROUTES\s*\)/u;
  const xsrfCallPattern = /\bprovideHttpClient\s*\(\s*withXsrfConfiguration\s*\(\s*\{[\s\S]*?\}\s*\)\s*,?\s*\)/u;
  const apiCallPattern = /\bprovideApi\s*\(\s*(['"])\1\s*\)/u;

  expect(
    countMatches(executableCode, /\bappConfig\b/u) === 1,
    'APP_CONFIG_IMMUTABLE',
    'appConfig nao pode ser referenciado ou alterado depois da declaracao canonica.',
  );

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
  expectExactlyOnceActiveImport(
    code,
    /^\s*import\s*\{\s*provideApi\s*\}\s*from\s*['"]\.\/api\/generated\/provide-api['"]\s*;/mu,
    'API_IMPORT',
    'import direto de provideApi da arvore gerada',
  );
  expectExactlyOnceActiveImport(
    code,
    /^\s*import\s*\{(?=[^}]*\bApplicationConfig\b)(?=[^}]*\bprovideBrowserGlobalErrorListeners\b)(?=[^}]*\bprovideZonelessChangeDetection\b)[^}]*\}\s*from\s*['"]@angular\/core['"]\s*;/mu,
    'CORE_PROVIDER_IMPORT',
    'import canonico dos providers core',
  );
  expectExactlyOneDirectProvider(
    executableCode,
    providers.executableCode,
    routerCallPattern,
    'ROUTER_PROVIDER',
    'provideRouter(APP_ROUTES)',
  );
  expectExactlyOnceInsideProviders(
    executableCode,
    providers.executableCode,
    /\bprovideRouter\s*\(/u,
    'ROUTER_PROVIDER',
    'provideRouter',
  );
  expectExactlyOneDirectProvider(
    executableCode,
    providers.executableCode,
    xsrfCallPattern,
    'HTTP_PROVIDER',
    'provideHttpClient(withXsrfConfiguration(...))',
  );
  expectExactlyOnceInsideProviders(
    executableCode,
    providers.executableCode,
    /\bprovideHttpClient\s*\(/u,
    'HTTP_PROVIDER',
    'provideHttpClient',
  );
  expectExactlyOnceInsideProviders(
    executableCode,
    providers.executableCode,
    /\bwithXsrfConfiguration\s*\(/u,
    'XSRF_PROVIDER',
    'withXsrfConfiguration',
  );
  expectExactlyOneDirectProvider(
    executableCode,
    providers.executableCode,
    apiCallPattern,
    'API_PROVIDER',
    "provideApi('')",
  );
  expectExactlyOnceInsideProviders(
    executableCode,
    providers.executableCode,
    /\bprovideApi\s*\(/u,
    'API_PROVIDER',
    'provideApi',
  );

  const xsrfCallMatch = xsrfCallPattern.exec(providers.executableCode);
  const xsrfCall = xsrfCallMatch === null
    ? undefined
    : providers.code.slice(
      xsrfCallMatch.index,
      xsrfCallMatch.index + xsrfCallMatch[0].length,
    );
  const xsrfOptions = xsrfCall?.match(/\{(?<options>[\s\S]*?)\}/u)?.groups?.options;

  const providersWithHttpMarker = xsrfCallMatch === null
    ? providers.code
    : `${providers.code.slice(0, xsrfCallMatch.index)}__HTTP_PROVIDER__${providers.code.slice(
      xsrfCallMatch.index + xsrfCallMatch[0].length,
    )}`;
  const providersWithApiMarker = providersWithHttpMarker.replace(
    apiCallPattern,
    '__API_PROVIDER__',
  );
  expect(
    /^\[provideBrowserGlobalErrorListeners\(\),provideZonelessChangeDetection\(\),provideRouter\(APP_ROUTES\),__HTTP_PROVIDER__,__API_PROVIDER__,?\]$/u.test(
      providersWithApiMarker.replace(/\s/gu, ''),
    ),
    'APP_CONFIG_PROVIDER_LIST',
    'appConfig.providers deve preservar os quatro providers canonicos e terminar com provideApi(\'\').',
  );

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

function validateGeneratedClient(generatedSources) {
  const baseServiceSource = stripScriptComments(
    generatedSources.get(PATHS.generatedBaseService),
  );
  const baseServiceExecutableCode = maskScriptStrings(baseServiceSource);
  expect(
    countMatches(
      baseServiceExecutableCode,
      /\bprotected[ \t]+basePath[ \t]*=/gu,
    ) === 1
      && countMatches(
        baseServiceExecutableCode,
        /^export class BaseService \{\n    protected basePath = '[ \t]*';[ \t]*$/gmu,
      ) === 1
      && countMatches(
        baseServiceSource,
        /^export class BaseService \{\n    protected basePath = '';[ \t]*$/gmu,
      ) === 1,
    'GENERATED_BASE_PATH',
    'BaseService gerado deve declarar exatamente um fallback basePath vazio e canonico.',
  );

  for (const [relativePath, source] of generatedSources) {
    let activeSource = stripScriptComments(source);
    if (relativePath === PATHS.generatedApiModule) {
      const diagnosticBlock = [
        '        if (!http) {',
        "            throw new Error('You need to import the HttpClientModule in your AppModule! \\n' +",
        "            'See also https://github.com/angular/angular/issues/20575');",
        '        }',
      ].join('\n');
      const occurrences = activeSource.split(diagnosticBlock).length - 1;
      expect(
        occurrences === 1,
        'GENERATED_ABSOLUTE_URL',
        'ApiModule deve manter somente a URL diagnostica conhecida do gerador.',
      );
      activeSource = activeSource.replace(diagnosticBlock, '');
    }
    const comparableSource = decodeUrlEscapes(activeSource);
    expect(
      !/(?::\/\/|['"\x60]\/\/)/iu.test(comparableSource),
      'GENERATED_ABSOLUTE_URL',
      'URL HTTP(S) absoluta capaz de escapar da origem e proibida (' + relativePath + ').',
    );
  }

  const configurationSource = stripScriptComments(
    generatedSources.get(PATHS.generatedConfiguration),
  );
  const configurationExecutableCode = maskScriptStrings(configurationSource);
  const conditionalFields = [
    'username',
    'password',
    'accessToken',
    'basePath',
    'withCredentials',
  ];
  const guardedOptionalFields = [...conditionalFields, 'apiKeys', 'credentials'];

  expect(
    /constructor\s*\(\s*\{\s*accessToken\s*,\s*apiKeys\s*,\s*basePath\s*,\s*credentials\s*,\s*encodeParam\s*,\s*encoder\s*,\s*password\s*,\s*username\s*,\s*withCredentials\s*\}\s*:\s*ConfigurationParameters\s*=\s*\{\s*\}\s*\)/u.test(
      configurationExecutableCode,
    ),
    'GENERATED_CONFIGURATION_AUTH',
    'o construtor de Configuration deve manter parametros opcionais sem defaults ativos.',
  );
  const optionalPropertyDeclarations = [
    [/\bapiKeys\?\s*:\s*\{\s*\[\s*key\s*:\s*string\s*\]\s*:\s*string\s*\}\s*;/gu, 2, 'apiKeys'],
    [/\busername\?\s*:\s*string\s*;/gu, 2, 'username'],
    [/\bpassword\?\s*:\s*string\s*;/gu, 2, 'password'],
    [/\baccessToken\?\s*:\s*string\s*\|\s*\(\s*\(\s*\)\s*=>\s*string\s*\)\s*;/gu, 2, 'accessToken'],
    [/\bbasePath\?\s*:\s*string\s*;/gu, 2, 'basePath'],
    [/\bwithCredentials\?\s*:\s*boolean\s*;/gu, 2, 'withCredentials'],
  ];
  for (const [pattern, expectedCount, field] of optionalPropertyDeclarations) {
    expect(
      countMatches(configurationExecutableCode, pattern) === expectedCount,
      'GENERATED_CONFIGURATION_AUTH',
      `${field} deve permanecer opcional e sem initializer na interface e na classe.`,
    );
  }
  expect(
    countMatches(
      configurationExecutableCode,
      /\bcredentials\?\s*:\s*\{\s*\[\s*key\s*:\s*string\s*\]\s*:\s*string\s*\|\s*\(\s*\(\s*\)\s*=>\s*string\s*\|\s*undefined\s*\)\s*\}\s*;/gu,
    ) === 1
      && countMatches(
        configurationExecutableCode,
        /\bcredentials\s*:\s*\{\s*\[\s*key\s*:\s*string\s*\]\s*:\s*string\s*\|\s*\(\s*\(\s*\)\s*=>\s*string\s*\|\s*undefined\s*\)\s*\}\s*;/gu,
      ) === 1,
    'GENERATED_CONFIGURATION_AUTH',
    'credentials deve permanecer sem initializer e opcional somente na interface.',
  );

  for (const field of conditionalFields) {
    const conditionalAssignment = new RegExp(
      `if\\s*\\(\\s*${field}\\s*!==\\s*undefined\\s*\\)\\s*\\{\\s*this\\.${field}\\s*=\\s*${field}\\s*;\\s*\\}`,
      'u',
    );
    const propertyAssignment = new RegExp(`\\bthis\\.${field}\\s*=`, 'gu');
    expect(
      conditionalAssignment.test(configurationExecutableCode)
        && countMatches(configurationExecutableCode, propertyAssignment) === 1,
      'GENERATED_CONFIGURATION_AUTH',
      `${field} deve permanecer uma opcao dormente atribuida somente quando fornecida.`,
    );
  }
  for (const field of guardedOptionalFields) {
    expect(
      countMatches(
        configurationExecutableCode,
        new RegExp(`\\b${field}\\b`, 'gu'),
      ) === 6,
      'GENERATED_CONFIGURATION_AUTH',
      `${field} nao pode ganhar uso adicional fora do suporte generico aprovado.`,
    );
  }

  expect(
    /if\s*\(\s*apiKeys\s*\)\s*\{\s*this\.apiKeys\s*=\s*apiKeys\s*;\s*\}/u.test(
      configurationExecutableCode,
    )
      && countMatches(configurationExecutableCode, /\bthis\.apiKeys\s*=/gu) === 1,
    'GENERATED_CONFIGURATION_AUTH',
    'apiKeys deve permanecer dormente e sem valor padrao.',
  );
  expect(
    /\bthis\.credentials\s*=\s*credentials\s*\?\?\s*\{\s*\}\s*;/u.test(
      configurationExecutableCode,
    )
      && countMatches(configurationExecutableCode, /\bthis\.credentials\s*=/gu) === 1,
    'GENERATED_CONFIGURATION_AUTH',
    'credentials deve usar somente o mapa vazio quando nenhuma credencial for fornecida.',
  );
  for (const forbiddenPattern of GENERATED_ACTIVE_AUTH_PATTERNS.slice(0, 3)) {
    expect(
      !forbiddenPattern.pattern.test(configurationSource),
      'GENERATED_CONFIGURATION_AUTH',
      `${forbiddenPattern.label} nao pode ser ativado por Configuration.`,
    );
  }

  const providerSource = stripScriptComments(generatedSources.get(PATHS.generatedProvider));
  for (const forbiddenPattern of GENERATED_ACTIVE_AUTH_PATTERNS) {
    expect(
      !forbiddenPattern.pattern.test(providerSource),
      'GENERATED_PROVIDER_AUTH',
      `${forbiddenPattern.label} nao pode ser ativado por provideApi.`,
    );
  }
  expect(
    !/\bwithCredentials\b/u.test(providerSource)
      && /typeof\s+configOrBasePath\s*===\s*(['"])string\1\s*\?\s*\{\s*provide\s*:\s*BASE_PATH\s*,\s*useValue\s*:\s*configOrBasePath\s*\}\s*:\s*\{\s*provide\s*:\s*Configuration\s*,\s*useValue\s*:\s*new\s+Configuration\s*\(\s*\{\s*\.\.\.configOrBasePath\s*\}\s*\)\s*,?\s*\}/u.test(
        providerSource,
      ),
    'GENERATED_PROVIDER_AUTH',
    'provideApi deve mapear string somente para BASE_PATH e nao ativar credenciais.',
  );

  const identityServiceSource = stripScriptComments(
    generatedSources.get(PATHS.generatedIdentityService),
  );
  const identityServiceExecutableCode = maskScriptStrings(identityServiceSource);
  expect(
    countMatches(
      identityServiceSource,
      /let\s+localVarPath\s*=\s*\x60\/api\/v1\/tracer\/identity\/current\x60\s*;/gu,
    ) === 1
      && countMatches(
      identityServiceSource,
      /this\.httpClient\.request<CurrentIdentityResponse>\s*\(\s*(['"])put\1\s*,\s*\x60\$\{basePath\}\$\{localVarPath\}\x60\s*,/gu,
      ) === 1
      && countMatches(
        identityServiceExecutableCode,
        /\bthis\.httpClient\.request\b/gu,
      ) === 1
      && countMatches(identityServiceExecutableCode, /\blocalVarPath\b/gu) === 2
      && countMatches(identityServiceExecutableCode, /\bbasePath\b/gu) === 4
      && countMatches(
        identityServiceExecutableCode,
        /const\s*\{\s*basePath\s*,\s*withCredentials\s*\}\s*=\s*this\.configuration\s*;/gu,
      ) === 1,
    'GENERATED_REQUEST_URL',
    'IdentityService deve montar o PUT somente por basePath + path relativo canonico.',
  );

  for (const [relativePath, source] of generatedSources) {
    if (!relativePath.endsWith('.service.ts')) {
      continue;
    }

    const activeSource = stripScriptComments(source);
    for (const forbiddenPattern of GENERATED_ACTIVE_AUTH_PATTERNS) {
      expect(
        !forbiddenPattern.pattern.test(activeSource),
        forbiddenPattern.rule,
        `${forbiddenPattern.label} nao pode ficar ativa no cliente gerado (${relativePath}).`,
      );
    }

    if (/\bwithCredentials\b/u.test(activeSource)) {
      expect(
        relativePath === PATHS.generatedIdentityService
          && countMatches(activeSource, /\bwithCredentials\b/u) === 3
          && /const\s*\{\s*basePath\s*,\s*withCredentials\s*\}\s*=\s*this\.configuration\s*;/u.test(activeSource)
          && /\.\.\.\(withCredentials\s*\?\s*\{\s*withCredentials\s*\}\s*:\s*\{\s*\}\s*\)/u.test(activeSource)
          && !/withCredentials\s*:\s*true\b/u.test(activeSource),
        'GENERATED_WITH_CREDENTIALS',
        'withCredentials gerado deve permanecer apenas no encadeamento dormente aprovado.',
      );
    }
  }
}

function validateAppConfigUsage(sources) {
  const scriptSources = [...sources.entries()]
    .filter(([relativePath]) => relativePath.endsWith('.ts'))
    .map(([relativePath, source]) => {
      const code = stripScriptComments(source);
      return [relativePath, code, maskScriptStrings(code)];
    });
  const main = scriptSources.find(([relativePath]) => relativePath === PATHS.main);

  expect(main !== undefined, 'APP_CONFIG_USAGE', 'main.ts deve participar da validacao do appConfig.');
  expect(
    /^\s*import\s*\{\s*bootstrapApplication\s*\}\s*from\s*['"]@angular\/platform-browser['"]\s*;\s*import\s*\{\s*appConfig\s*\}\s*from\s*['"]\.\/app\/app\.config['"]\s*;\s*import\s*\{\s*AppComponent\s*\}\s*from\s*['"]\.\/app\/app\.component['"]\s*;\s*bootstrapApplication\s*\(\s*AppComponent\s*,\s*appConfig\s*\)\s*\.catch\s*\(\s*\(\s*error\s*:\s*unknown\s*\)\s*=>\s*\{\s*console\.error\s*\(\s*error\s*\)\s*;\s*\}\s*\)\s*;\s*$/u.test(main[1]),
    'MAIN_BOOTSTRAP_SHAPE',
    'main.ts deve conter somente imports canonicos e o bootstrap top-level aprovado.',
  );
  expectExactlyOnceActiveImport(
    main[1],
    /^\s*import\s*\{\s*appConfig\s*\}\s*from\s*['"]\.\/app\/app\.config['"]\s*;/mu,
    'APP_CONFIG_USAGE',
    'import canonico de appConfig em main.ts',
  );
  expectExactlyOnce(
    main[2],
    /\bbootstrapApplication\s*\(\s*AppComponent\s*,\s*appConfig\s*\)/u,
    'APP_CONFIG_USAGE',
    'bootstrapApplication(AppComponent, appConfig)',
  );

  for (const [relativePath, source, executableSource] of scriptSources) {
    const expectedIdentifierCount = relativePath === PATHS.config
      ? 1
      : (relativePath === PATHS.main ? 2 : 0);
    const expectedModuleReferenceCount = Number(relativePath === PATHS.main);

    expect(
      countMatches(executableSource, /\bappConfig\b/u) === expectedIdentifierCount,
      'APP_CONFIG_USAGE',
      'appConfig so pode ser declarado no config e usado no bootstrap canonico de main.ts.',
    );
    expect(
      countActiveModuleReferences(source, executableSource, 'app.config')
        === expectedModuleReferenceCount,
      'APP_CONFIG_USAGE',
      'main.ts deve ser o unico consumidor direto do modulo app.config.',
    );
  }
}

function validateRootComponent(sources) {
  const component = stripScriptComments(sources.get(PATHS.component));
  const template = stripHtmlComments(sources.get(PATHS.template)).trim();

  expect(
    /^\s*import\s*\{\s*Component\s*\}\s*from\s*['"]@angular\/core['"]\s*;\s*import\s*\{\s*RouterOutlet\s*\}\s*from\s*['"]@angular\/router['"]\s*;\s*@Component\s*\(\s*\{\s*selector\s*:\s*['"]app-root['"]\s*,\s*standalone\s*:\s*true\s*,\s*imports\s*:\s*\[\s*RouterOutlet\s*\]\s*,\s*templateUrl\s*:\s*['"]\.\/app\.component\.html['"]\s*,\s*styleUrl\s*:\s*['"]\.\/app\.component\.scss['"]\s*,?\s*\}\s*\)\s*export\s+class\s+AppComponent\s*\{\s*\}\s*$/u.test(component),
    'ROOT_COMPONENT_SHAPE',
    'AppComponent deve permanecer standalone, sem logica e com somente RouterOutlet.',
  );
  expect(
    template === '<router-outlet />',
    'ROOT_TEMPLATE_SHAPE',
    'app.component.html deve conter somente um router-outlet autocontido.',
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
  expect(
    EMPTY_BUILD_SPECIFIC_ROUTES_PATTERN.test(development)
      || CANONICAL_TRACER_ROUTES_PATTERN.test(development),
    'DEVELOPMENT_ROUTES_SHAPE',
    'development deve conter somente a lista vazia ou a unica lazy route canonica do tracer.',
  );
  expect(
    !/(?:\btracer\b|\bTracerPageComponent\b)/iu.test(production),
    'PRODUCTION_TRACER_FORBIDDEN',
    'production nao pode importar, mencionar nem expor o tracer.',
  );
  expect(
    EMPTY_BUILD_SPECIFIC_ROUTES_PATTERN.test(production),
    'PRODUCTION_ROUTES_SHAPE',
    'production deve conter somente o import type Routes e a lista vazia canonica.',
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
    const expectedBuildSpecificRoutesCount = relativePath === PATHS.seam
      ? 2
      : Number(
        relativePath === PATHS.development
          || relativePath === PATHS.production,
      );
    expect(
      countMatches(executableSource, /\bBUILD_SPECIFIC_ROUTES\b/u)
        === expectedBuildSpecificRoutesCount,
      'BUILD_SPECIFIC_ROUTES_USAGE',
      'BUILD_SPECIFIC_ROUTES deve aparecer somente nas declaracoes de ambiente, no import e no spread da seam.',
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

function activeRuntimeSource(relativePath, source) {
  return relativePath.endsWith('.html')
    ? stripHtmlComments(source)
    : stripScriptComments(source);
}

function validateTracerRuntimeBoundary(runtimeSources) {
  for (const [relativePath, source] of runtimeSources) {
    if (
      relativePath === PATHS.development
      || relativePath.startsWith(TRACER_FEATURE_PREFIX)
    ) {
      continue;
    }

    const activeSource = activeRuntimeSource(relativePath, source);
    const decodedSource = decodeUrlEscapes(activeSource);

    if (relativePath.endsWith('.ts')) {
      expect(
        countMatches(maskScriptStrings(activeSource), /\bimport\s*\(/u) === 0,
        'DYNAMIC_IMPORT_FORBIDDEN',
        `${relativePath} nao pode usar import dinamico fora da lazy route development canonica.`,
      );
    }

    expect(
      !/tracer/iu.test(decodedSource),
      'TRACER_EAGER_REFERENCE',
      `${relativePath} nao pode referenciar o tracer fora da lazy route development canonica.`,
    );
  }

  for (const [relativePath, source] of runtimeSources) {
    if (!relativePath.startsWith(TRACER_FEATURE_PREFIX)) {
      continue;
    }

    const activeSource = activeRuntimeSource(relativePath, source);
    const decodedSource = decodeUrlEscapes(activeSource);
    const executableSource = relativePath.endsWith('.ts')
      ? maskScriptStrings(decodedSource)
      : '';

    for (const forbiddenPattern of FORBIDDEN_PATTERNS) {
      expect(
        !forbiddenPattern.pattern.test(decodedSource),
        forbiddenPattern.rule,
        `${forbiddenPattern.label} nao e permitido no componente tracer (${relativePath}).`,
      );
    }

    for (const forbiddenPattern of TRACER_DIRECT_CLIENT_PATTERNS) {
      const targetSource = forbiddenPattern.target === 'executable'
        ? executableSource
        : decodedSource;

      expect(
        !forbiddenPattern.pattern.test(targetSource),
        forbiddenPattern.rule,
        `${forbiddenPattern.label} nao e permitido no componente tracer (${relativePath}); use somente IdentityService gerado.`,
      );
    }
  }
}

export async function validateAngularRouteBoundary(projectRoot = defaultProjectRoot) {
  const resolvedProjectRoot = resolve(projectRoot);
  const angularConfiguration = await readAngularConfiguration(resolvedProjectRoot);
  const sources = new Map();
  const generatedSources = await readGeneratedTypeScriptSources(resolvedProjectRoot);
  const runtimeSources = await readFrontendRuntimeSources(resolvedProjectRoot);

  for (const relativePath of HOST_SOURCE_PATHS) {
    sources.set(relativePath, await readRequiredText(resolvedProjectRoot, relativePath));
  }

  validateScriptLexicalSafety(sources);
  validateFileReplacements(angularConfiguration);
  validateTracerRuntimeBoundary(runtimeSources);
  validateAppConfiguration(sources.get(PATHS.config));
  validateAppConfigUsage(sources);
  validateRootComponent(sources);
  validateRouteSources(sources);
  validateForbiddenPatterns(sources);
  validateGeneratedClient(generatedSources);

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
