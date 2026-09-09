import { createHash } from 'node:crypto';
import { readdir, readFile } from 'node:fs/promises';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const PROJECT_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const LOGIN_THEME_ROOT = join(
  PROJECT_ROOT,
  'infra',
  'keycloak',
  'themes',
  'betufu',
  'login',
);
const REALM_PATH = join(PROJECT_ROOT, 'infra', 'keycloak', 'realm-betufu.json');
const CSS_PATH = join(LOGIN_THEME_ROOT, 'resources', 'css', 'login.css');
const VALIDATION_SCRIPT_PATH = join(
  LOGIN_THEME_ROOT,
  'resources',
  'js',
  'required-field-validation.js',
);

const ISSUER = 'http://localhost:8081/realms/betufu-local';
const KEYCLOAK_ORIGIN = 'http://localhost:8081';
const CLIENT_ID = 'betufu-bff';
const REDIRECT_URI = 'http://localhost:4200/login/oauth2/code/keycloak';
const MAX_REDIRECTS = 3;
const REQUEST_TIMEOUT_MS = 10_000;
const EXPECTED_LOGIN_CSS_SHA256 =
  'fc8b6f01f74814aeff0d6baed8714328abad6ff030562d9977ab7dc5b3912656';
const EXPECTED_VALIDATION_SCRIPT_SHA256 =
  '25f7138b8204487302b8a86f888c89080eede8326da73098df50f6775f027eae';

const NOTICE =
  'A BETUFU é uma plataforma independente e não possui vínculo oficial com a UFU.';

const EXPECTED_THEME_PROPERTIES = new Map([
  ['parent', 'keycloak.v2'],
  ['import', 'common/keycloak'],
  ['styles', 'css/styles.css css/login.css'],
  ['scripts', 'js/required-field-validation.js'],
  ['darkMode', 'false'],
  ['locales', 'pt-BR'],
]);

const EXPECTED_ASSETS = new Map([
  [
    'resources/fonts/inter-regular.woff2',
    {
      bytes: 111_268,
      sha256: 'e06f6b1bc553aaea4e4668023ed0ab0a147129c3107f511bc7d03d361b0ae085',
      signature: 'wOF2',
    },
  ],
  [
    'resources/fonts/inter-semibold.woff2',
    {
      bytes: 114_812,
      sha256: '5cb7103e4e605989afebc03d989c79201e54b21b5183db33981f70db9178a301',
      signature: 'wOF2',
    },
  ],
  [
    'resources/fonts/barlow-condensed-semibold.woff2',
    {
      bytes: 62_764,
      sha256: '21a97c42410e968fbb5bd5d413ad1da01c21d1efce0eb51f03cdcbc899c51459',
      signature: 'wOF2',
    },
  ],
  [
    'resources/fonts/inter-ofl.txt',
    {
      bytes: 4_380,
      sha256: '262481e844521b326f5ecd053e59b98c8b2da78c8ee1bdbb6e8174305e54935a',
      copyright: 'Copyright (c) 2016 The Inter Project Authors',
    },
  ],
  [
    'resources/fonts/barlow-condensed-ofl.txt',
    {
      bytes: 4_377,
      sha256: '186d750eb496a4c17a76385f82be6aea2ac1cf2de074a811d63786cf374ea73f',
      copyright: 'Copyright 2017 The Barlow Project Authors',
    },
  ],
  [
    'messages/messages_pt_BR.properties',
    {
      bytes: 613,
      sha256: '2258aaa6c86813b5dc0b515fc9d7a59febdf0a2549e769c8ef9d4b2787f86d77',
    },
  ],
]);

const EXPECTED_THEME_FILES = new Set([
  'footer.ftl',
  'theme.properties',
  'resources/css/login.css',
  'resources/js/required-field-validation.js',
  'messages/messages_pt_BR.properties',
  ...EXPECTED_ASSETS.keys(),
]);

const EXPECTED_THEME_DIRECTORIES = new Set([
  'resources',
  'resources/css',
  'resources/fonts',
  'resources/js',
  'messages',
]);

const EXPECTED_FONTS = [
  {
    family: 'Inter',
    weight: '400',
    url: '../fonts/inter-regular.woff2',
  },
  {
    family: 'Inter',
    weight: '600',
    url: '../fonts/inter-semibold.woff2',
  },
  {
    family: 'Barlow Condensed',
    weight: '600',
    url: '../fonts/barlow-condensed-semibold.woff2',
  },
];

const EXPECTED_COLOR_TOKENS = new Map([
  ['--betufu-canvas', '#120b12'],
  ['--betufu-surface', '#171018'],
  ['--betufu-surface-raised', '#21131d'],
  ['--betufu-accent', '#ff626b'],
  ['--betufu-on-accent', '#09080d'],
  ['--betufu-text', '#fff4f4'],
  ['--betufu-text-muted', '#cdbfc5'],
  ['--betufu-border', '#5d4250'],
  ['--betufu-focus', '#ffd2d4'],
  ['--betufu-error', '#ff7a82'],
]);

function invariant(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function normalizeRelativePath(path) {
  return path.split(sep).join('/');
}

function normalizeText(text) {
  return text.replace(/\r\n/g, '\n');
}

function canonicalizeUtf8Text(bytes, label) {
  const text = bytes.toString('utf8');
  invariant(
    Buffer.from(text, 'utf8').equals(bytes),
    `${label} não contém UTF-8 válido`,
  );
  invariant(!/\r(?!\n)/u.test(text), `${label} contém CR isolado`);
  return Buffer.from(normalizeText(text), 'utf8');
}

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

function exactlyOnce(haystack, needle) {
  return haystack.split(needle).length - 1 === 1;
}

async function inventoryDirectory(root) {
  const files = [];
  const directories = [];

  async function visit(directory) {
    const entries = await readdir(directory, { withFileTypes: true });
    entries.sort((left, right) => left.name.localeCompare(right.name, 'en'));

    for (const entry of entries) {
      const absolutePath = join(directory, entry.name);
      const relativePath = normalizeRelativePath(relative(root, absolutePath));

      invariant(!entry.isSymbolicLink(), `Link simbólico não permitido no tema: ${relativePath}`);

      if (entry.isDirectory()) {
        directories.push(relativePath);
        await visit(absolutePath);
      } else {
        invariant(entry.isFile(), `Entrada não regular no tema: ${relativePath}`);
        files.push(relativePath);
      }
    }
  }

  await visit(root);
  return { files, directories };
}

function assertExactSet(actualValues, expectedValues, label) {
  const actual = new Set(actualValues);
  const missing = [...expectedValues].filter((value) => !actual.has(value));
  const unexpected = [...actual].filter((value) => !expectedValues.has(value));

  invariant(
    missing.length === 0 && unexpected.length === 0,
    `${label} divergente; ausentes=[${missing.join(', ')}], inesperados=[${unexpected.join(', ')}]`,
  );
}

function parseThemeProperties(text) {
  const properties = new Map();

  for (const [index, rawLine] of normalizeText(text).split('\n').entries()) {
    const line = rawLine.trim();
    if (line === '' || line.startsWith('#') || line.startsWith('!')) {
      continue;
    }

    const separatorIndex = line.indexOf('=');
    invariant(separatorIndex > 0, `Linha inválida em theme.properties: ${index + 1}`);

    const key = line.slice(0, separatorIndex).trim();
    const value = line.slice(separatorIndex + 1).trim();
    invariant(!properties.has(key), `Propriedade duplicada em theme.properties: ${key}`);
    properties.set(key, value);
  }

  return properties;
}

function assertThemeProperties(text) {
  const actual = parseThemeProperties(text);
  assertExactSet(actual.keys(), new Set(EXPECTED_THEME_PROPERTIES.keys()), 'Propriedades do tema');

  for (const [key, expectedValue] of EXPECTED_THEME_PROPERTIES) {
    invariant(
      actual.get(key) === expectedValue,
      `Valor inválido para ${key}; esperado=${expectedValue}`,
    );
  }
}

function assertFooter(text) {
  const expected = [
    '<#macro content>',
    `  <p class="betufu-independence-notice">${NOTICE}</p>`,
    '</#macro>',
    '',
  ].join('\n');
  const normalized = normalizeText(text);

  invariant(normalized === expected, 'footer.ftl diverge do macro mínimo aprovado');
  invariant(exactlyOnce(normalized, NOTICE), 'O aviso institucional deve aparecer exatamente uma vez');
  invariant(!/<a\b/i.test(normalized), 'O aviso institucional não pode ser um link');
}

function assertValidationScript(bytes) {
  const canonicalBytes = canonicalizeUtf8Text(bytes, 'Script D-15');
  const source = canonicalBytes.toString('utf8');
  invariant(canonicalBytes.length === 1_782, 'Tamanho inválido para o script D-15');
  invariant(
    sha256(canonicalBytes) === EXPECTED_VALIDATION_SCRIPT_SHA256,
    'SHA-256 inválido para o script D-15',
  );
  invariant(/password\.required\s*=\s*true/u.test(source), 'Senha não foi marcada como required');
  invariant(/form\.noValidate\s*=\s*false/u.test(source), 'Formulário não habilita validação nativa');
  invariant(/password\.validity\.valueMissing/u.test(source), 'D-15 não usa valueMissing nativo');
  invariant(/password\.validationMessage/u.test(source), 'D-15 não usa mensagem nativa localizada');
  invariant(/aria-errormessage/u.test(source), 'D-15 não associa aria-errormessage');
  invariant(/aria-describedby/u.test(source), 'D-15 não preserva aria-describedby');
  invariant(/password\.focus\(\)/u.test(source), 'D-15 não move foco para a senha');
  invariant(
    !/(?:fetch|XMLHttpRequest|WebSocket|localStorage|sessionStorage|indexedDB|document\.cookie|eval|new\s+Function)\b/u.test(source),
    'Script D-15 contém API proibida',
  );
  invariant(!/password\.value\b/u.test(source), 'Script D-15 não pode ler o valor da senha');
}

function parseCssDeclarations(block) {
  const declarations = new Map();
  const declarationPattern = /([a-z-]+)\s*:\s*([^;]+);/gi;

  for (const match of block.matchAll(declarationPattern)) {
    const property = match[1].toLowerCase();
    invariant(!declarations.has(property), `Declaração CSS duplicada no @font-face: ${property}`);
    declarations.set(property, match[2].trim());
  }

  return declarations;
}

function unquote(value) {
  const trimmed = value.trim();
  if (
    trimmed.length >= 2 &&
    ((trimmed.startsWith("'") && trimmed.endsWith("'")) ||
      (trimmed.startsWith('"') && trimmed.endsWith('"')))
  ) {
    return trimmed.slice(1, -1);
  }
  return trimmed;
}

function extractCssUrls(css) {
  return [...css.matchAll(/url\(\s*(['"]?)(.*?)\1\s*\)/gi)].map((match) => match[2].trim());
}

function stripCssComments(css) {
  const withoutComments = css.replace(/\/\*[\s\S]*?\*\//g, '');
  invariant(
    !withoutComments.includes('/*') && !withoutComments.includes('*/'),
    'Comentário CSS malformado',
  );
  return withoutComments;
}

function parseAllCssDeclarations(css) {
  return [...css.matchAll(/(?:^|[;{])\s*([-\w]+)\s*:\s*([^;{}]+);/gm)].map((match) => ({
    property: match[1].toLowerCase(),
    value: match[2].trim(),
  }));
}

function assertCssColors(css) {
  const declarations = parseAllCssDeclarations(css);
  const customProperties = new Map();

  for (const { property, value } of declarations.filter(({ property }) => property.startsWith('--'))) {
    invariant(!customProperties.has(property), `Token CSS duplicado: ${property}`);
    customProperties.set(property, value.toLowerCase());
  }

  assertExactSet(customProperties.keys(), new Set(EXPECTED_COLOR_TOKENS.keys()), 'Tokens de cor');
  for (const [property, expectedValue] of EXPECTED_COLOR_TOKENS) {
    invariant(customProperties.get(property) === expectedValue, `Valor inválido para o token ${property}`);
  }

  const actualHexColors = [...css.matchAll(/#[0-9a-f]{3,8}\b/gi)]
    .map((match) => match[0].toLowerCase())
    .sort();
  const expectedHexColors = [...EXPECTED_COLOR_TOKENS.values()].sort();
  invariant(
    JSON.stringify(actualHexColors) === JSON.stringify(expectedHexColors),
    'Cores hexadecimais devem existir somente nos tokens aprovados',
  );

  const approvedShadowPattern = /rgba\(\s*9\s*,\s*8\s*,\s*13\s*,\s*\.36\s*\)/gi;
  const shadowMatches = css.match(approvedShadowPattern) ?? [];
  invariant(shadowMatches.length === 1, 'A sombra aprovada deve aparecer exatamente uma vez');
  const withoutApprovedShadow = css.replace(approvedShadowPattern, '');
  invariant(
    !/\b(?:rgba?|hsla?|hwb|lab|lch|oklab|oklch|color|color-mix)\s*\(/i.test(withoutApprovedShadow),
    'Função de cor fora do contrato aprovado',
  );

  const colorLikeProperty = /^(?:color|background(?:-color|-image)?|border(?:-(?:top|right|bottom|left))?(?:-color)?|outline|box-shadow|text-shadow|fill|stroke|caret-color|accent-color|text-decoration-color|filter)$/;
  for (const { property, value } of declarations.filter(({ property }) => colorLikeProperty.test(property))) {
    const remainder = value
      .replace(approvedShadowPattern, '')
      .replace(/var\(--betufu-[a-z-]+\)/gi, '')
      .replace(/-?(?:\d*\.)?\d+(?:px|%|rem|em)?/gi, '')
      .replace(/\b(?:none|solid)\b/gi, '')
      .replace(/[\s,./()-]/g, '');
    invariant(remainder === '', `Valor de cor não aprovado em ${property}`);
  }
}

function assertCss(css) {
  const normalized = normalizeText(css);

  invariant(
    sha256(Buffer.from(normalized, 'utf8')) === EXPECTED_LOGIN_CSS_SHA256,
    'login.css diverge dos bytes normalizados aprovados',
  );
  invariant(stripCssComments(normalized) === normalized, 'Comentários não são permitidos em login.css');
  invariant(!/!important\b/i.test(normalized), 'login.css não pode usar !important');

  invariant(!/@import\b/i.test(normalized), 'login.css não pode importar outra folha');
  invariant(!/@media\b/i.test(normalized), 'login.css não pode conter variante por media query');
  invariant(
    !/prefers-color-scheme/i.test(normalized),
    'login.css não pode variar conforme preferência de cor do sistema',
  );
  invariant(
    !/(?:https?:|data:|javascript:|\/\/)/i.test(normalized),
    'login.css contém uma origem ou esquema remoto/proibido',
  );

  const fontFaceBlocks = [...normalized.matchAll(/@font-face\s*\{([\s\S]*?)\}/gi)];
  invariant(fontFaceBlocks.length === EXPECTED_FONTS.length, 'login.css deve conter três @font-face');

  const actualFonts = fontFaceBlocks.map((match) => {
    const declarations = parseCssDeclarations(match[1]);
    const source = declarations.get('src') ?? '';
    const sourceMatch = source.match(/^url\(\s*(['"]?)(.*?)\1\s*\)\s+format\(\s*(['"]?)woff2\3\s*\)$/i);
    invariant(sourceMatch, 'Cada @font-face deve referenciar exatamente um arquivo WOFF2 local');

    return {
      family: unquote(declarations.get('font-family') ?? ''),
      weight: declarations.get('font-weight'),
      style: declarations.get('font-style'),
      display: declarations.get('font-display'),
      url: sourceMatch[2],
      propertyCount: declarations.size,
    };
  });

  for (const [index, expectedFont] of EXPECTED_FONTS.entries()) {
    const actualFont = actualFonts[index];
    invariant(actualFont.family === expectedFont.family, `Família inválida no @font-face ${index + 1}`);
    invariant(actualFont.weight === expectedFont.weight, `Peso inválido no @font-face ${index + 1}`);
    invariant(actualFont.style === 'normal', `Estilo inválido no @font-face ${index + 1}`);
    invariant(actualFont.display === 'swap', `font-display inválido no @font-face ${index + 1}`);
    invariant(actualFont.url === expectedFont.url, `URL inválida no @font-face ${index + 1}`);
    invariant(actualFont.propertyCount === 5, `@font-face ${index + 1} possui declaração extra ou ausente`);
  }

  const cssUrls = extractCssUrls(normalized);
  invariant(
    JSON.stringify(cssUrls) === JSON.stringify(EXPECTED_FONTS.map((font) => font.url)),
    'login.css deve conter somente as três URLs de fontes aprovadas, na ordem aprovada',
  );

  const fontSizes = [...normalized.matchAll(/font-size\s*:\s*([^;]+);/gi)].map((match) => match[1].trim());
  const approvedFontSizes = new Set(['14px', '16px', '24px', '32px']);
  invariant(fontSizes.length > 0, 'login.css não declara tamanhos tipográficos');
  invariant(fontSizes.every((value) => approvedFontSizes.has(value)), 'login.css contém tamanho não aprovado');
  assertExactSet(fontSizes, approvedFontSizes, 'Tamanhos tipográficos');

  const fontWeights = [...normalized.matchAll(/font-weight\s*:\s*([^;]+);/gi)].map((match) => match[1].trim());
  const approvedFontWeights = new Set(['400', '600']);
  invariant(fontWeights.length > 0, 'login.css não declara pesos tipográficos');
  invariant(fontWeights.every((value) => approvedFontWeights.has(value)), 'login.css contém peso não aprovado');
  assertExactSet(fontWeights, approvedFontWeights, 'Pesos tipográficos');

  assertCssColors(normalized);

  invariant(/color-scheme\s*:\s*dark\s*;/i.test(normalized), 'Tema sempre escuro não declarado');
  invariant(
    /\.pf-v5-c-button\.pf-m-primary\s*\{[\s\S]*?background-color\s*:\s*var\(--betufu-accent\)\s*;[\s\S]*?color\s*:\s*var\(--betufu-on-accent\)\s*;[\s\S]*?\}/i.test(normalized),
    'CTA primário não usa o contrato laranja/on-accent',
  );
  invariant(
    /:focus-visible\s*\{[\s\S]*?outline\s*:\s*3px\s+solid\s+var\(--betufu-focus\)\s*;[\s\S]*?outline-offset\s*:\s*3px\s*;[\s\S]*?\}/i.test(normalized),
    'Foco visível não corresponde ao contrato aprovado',
  );
  invariant(/\.betufu-independence-notice\s*\{/i.test(normalized), 'Classe do footer ausente no CSS');
}

async function assertAssets() {
  for (const [relativePath, expected] of EXPECTED_ASSETS) {
    const bytes = await readFile(join(LOGIN_THEME_ROOT, ...relativePath.split('/')));
    invariant(bytes.length === expected.bytes, `Tamanho inválido para ${relativePath}`);
    invariant(sha256(bytes) === expected.sha256, `SHA-256 inválido para ${relativePath}`);

    if (expected.signature) {
      invariant(bytes.subarray(0, 4).toString('ascii') === expected.signature, `Assinatura WOFF2 inválida: ${relativePath}`);
    }

    if (expected.copyright) {
      invariant(bytes.toString('utf8').includes(expected.copyright), `Copyright obrigatório ausente: ${relativePath}`);
    }
  }
}

async function validateLocalTheme() {
  const inventory = await inventoryDirectory(LOGIN_THEME_ROOT);
  assertExactSet(inventory.files, EXPECTED_THEME_FILES, 'Allowlist de arquivos do tema');
  assertExactSet(inventory.directories, EXPECTED_THEME_DIRECTORIES, 'Estrutura de diretórios do tema');

  const ftlFiles = inventory.files.filter((path) => path.endsWith('.ftl'));
  assertExactSet(ftlFiles, new Set(['footer.ftl']), 'Allowlist de templates FTL');

  const [themeProperties, footer, css, validationScript, realmText] = await Promise.all([
    readFile(join(LOGIN_THEME_ROOT, 'theme.properties'), 'utf8'),
    readFile(join(LOGIN_THEME_ROOT, 'footer.ftl'), 'utf8'),
    readFile(CSS_PATH, 'utf8'),
    readFile(VALIDATION_SCRIPT_PATH),
    readFile(REALM_PATH, 'utf8'),
  ]);

  assertThemeProperties(themeProperties);
  assertFooter(footer);
  assertCss(css);
  assertValidationScript(validationScript);
  await assertAssets();

  const realm = JSON.parse(realmText);
  invariant(realm.loginTheme === 'betufu', 'O realm betufu-local não está associado ao tema betufu');
}

function assertSameOriginResource(value, baseUrl, label) {
  invariant(typeof value === 'string' && value.trim() !== '', `${label} contém URL vazia`);
  const trimmed = value.trim();

  if (trimmed.startsWith('#') || /^(?:data|blob):/i.test(trimmed)) {
    return null;
  }

  invariant(!/^javascript:/i.test(trimmed), `${label} usa esquema javascript proibido`);
  const url = new URL(trimmed, baseUrl);
  invariant(url.origin === KEYCLOAK_ORIGIN, `${label} aponta para outra origem: ${url.origin}`);
  invariant(!url.username && !url.password, `${label} não pode conter credenciais`);
  return url;
}

function assertSafeKeycloakRedirect(location, currentUrl) {
  const target = new URL(location, currentUrl);
  invariant(target.origin === KEYCLOAK_ORIGIN, `Redirect recusado para outra origem: ${target.origin}`);
  invariant(!target.username && !target.password, 'Redirect com credenciais embutidas recusado');
  invariant(!/(?:^|\/)broker(?:\/|$)/i.test(target.pathname), 'Redirect para provedor de identidade recusado');
  invariant(target.origin !== new URL(REDIRECT_URI).origin, 'Redirect para o cliente recusado');
  return target;
}


const COOKIE_NAME_PATTERN = /^[!#$%&'*+\-.^_`|~0-9A-Za-z]+$/;
const COOKIE_OCTET_PATTERN = /^[\x21\x23-\x2B\x2D-\x3A\x3C-\x5B\x5D-\x7E]+$/;

function parseSetCookieHeaders(headers) {
  if (typeof headers.getSetCookie === 'function') {
    return headers.getSetCookie();
  }
  const raw = headers.get('set-cookie');
  if (!raw) {
    return [];
  }
  const parts = [];
  let start = 0;
  const pattern = /,\s*(?=[!#$%&'*+\-.^_`|~0-9A-Za-z]+=)/g;
  let match;
  while ((match = pattern.exec(raw)) !== null) {
    parts.push(raw.slice(start, match.index).trim());
    start = match.index + match[0].length;
  }
  if (start < raw.length) {
    parts.push(raw.slice(start).trim());
  }
  return parts.filter(Boolean);
}

function createEphemeralCookieJar() {
  const cookies = new Map();

  return {
    saveFromResponse(response, url) {
      const targetUrl = new URL(url);
      if (targetUrl.origin !== KEYCLOAK_ORIGIN) {
        return;
      }

      for (const headerLine of parseSetCookieHeaders(response.headers)) {
        if (/[\r\n]/.test(headerLine)) {
          continue;
        }
        const [nameValuePart, ...attributeParts] = headerLine.split(';');
        if (!nameValuePart) {
          continue;
        }
        const separatorIndex = nameValuePart.indexOf('=');
        if (separatorIndex <= 0) {
          continue;
        }
        const rawName = nameValuePart.slice(0, separatorIndex);
        const rawValue = nameValuePart.slice(separatorIndex + 1);
        if (rawName !== rawName.trim() || rawValue !== rawValue.trim()) {
          continue;
        }
        const name = rawName;
        const value = rawValue;
        if (!COOKIE_NAME_PATTERN.test(name)) {
          continue;
        }

        let isRemoved = value === '';
        for (const attr of attributeParts) {
          const trimmed = attr.trim();
          const attrEquals = trimmed.indexOf('=');
          const attrName = (attrEquals > 0 ? trimmed.slice(0, attrEquals) : trimmed).trim().toLowerCase();
          const attrVal = attrEquals > 0 ? trimmed.slice(attrEquals + 1).trim() : '';
          if (attrName === 'max-age') {
            const maxAge = parseInt(attrVal, 10);
            if (!Number.isNaN(maxAge) && maxAge <= 0) {
              isRemoved = true;
            }
          }
        }

        if (isRemoved) {
          cookies.delete(name);
        } else if (COOKIE_OCTET_PATTERN.test(value)) {
          cookies.set(name, value);
        }
      }
    },

    getCookieHeader(url) {
      const targetUrl = new URL(url);
      if (targetUrl.origin !== KEYCLOAK_ORIGIN || cookies.size === 0) {
        return null;
      }
      return [...cookies.entries()].map(([name, value]) => `${name}=${value}`).join('; ');
    },
  };
}

async function fetchWithSafeRedirects(input, init = {}, jar = null) {
  invariant(!init.method || init.method === 'GET', 'O smoke aceita somente requests GET');
  let currentUrl = new URL(input);
  invariant(currentUrl.origin === KEYCLOAK_ORIGIN, `Request inicial fora do Keycloak: ${currentUrl.origin}`);

  for (let redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount += 1) {
    const headers = new Headers(init.headers);
    if (jar && currentUrl.origin === KEYCLOAK_ORIGIN) {
      const cookieHeader = jar.getCookieHeader(currentUrl);
      if (cookieHeader) {
        headers.set('Cookie', cookieHeader);
      }
    }

    const response = await fetch(currentUrl, {
      ...init,
      headers,
      method: 'GET',
      redirect: 'manual',
      credentials: 'omit',
      referrerPolicy: 'no-referrer',
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });

    if (jar && currentUrl.origin === KEYCLOAK_ORIGIN) {
      jar.saveFromResponse(response, currentUrl);
    }

    if (![301, 302, 303, 307, 308].includes(response.status)) {
      return { response, finalUrl: currentUrl };
    }

    invariant(redirectCount < MAX_REDIRECTS, `Limite de ${MAX_REDIRECTS} redirects excedido`);
    const location = response.headers.get('location');
    invariant(location, `Redirect ${response.status} sem cabeçalho Location`);
    currentUrl = assertSafeKeycloakRedirect(location, currentUrl);
  }

  throw new Error('Fluxo de redirects inválido');
}

function parseAttributes(source) {
  const attributes = new Map();
  const pattern = /([^\s=/>]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?/g;

  for (const match of source.matchAll(pattern)) {
    attributes.set(match[1].toLowerCase(), match[2] ?? match[3] ?? match[4] ?? '');
  }

  return attributes;
}

function stripHtmlComments(html) {
  const withoutComments = html.replace(/<!--[\s\S]*?-->/g, '');
  invariant(
    !withoutComments.includes('<!--') && !withoutComments.includes('-->'),
    'Comentário HTML malformado',
  );
  return withoutComments;
}

function removeNonRenderedContainers(html) {
  return html.replace(
    /<(script|style|template|noscript)\b[^>]*>[\s\S]*?<\/\1\s*>/gi,
    '',
  );
}

function assertRenderedNotice(html) {
  const visibleHtml = removeNonRenderedContainers(stripHtmlComments(html));
  const paragraphs = [...visibleHtml.matchAll(/<p\b([^>]*)>([\s\S]*?)<\/p\s*>/gi)]
    .map((match) => ({ attributes: parseAttributes(match[1]), content: match[2].trim() }))
    .filter(({ attributes }) =>
      (attributes.get('class') ?? '').split(/\s+/).includes('betufu-independence-notice'),
    );

  invariant(paragraphs.length === 1, 'O footer renderizado deve conter um único parágrafo BETUFU');
  const paragraph = paragraphs[0];
  invariant(
    paragraph.attributes.size === 1 &&
      paragraph.attributes.get('class') === 'betufu-independence-notice',
    'O parágrafo institucional renderizado contém atributo não aprovado',
  );
  invariant(paragraph.content === NOTICE, 'O aviso institucional renderizado diverge do texto aprovado');
  invariant(!/[<>]/.test(paragraph.content), 'O aviso institucional renderizado deve ser texto comum');
  invariant(exactlyOnce(visibleHtml, NOTICE), 'O aviso institucional visível deve aparecer exatamente uma vez');

  return visibleHtml;
}

function extractHtmlResources(html, documentUrl) {
  const resources = [];
  const stylesheets = [];
  const withoutComments = stripHtmlComments(html);
  const withoutInactiveContainers = withoutComments.replace(
    /<(style|template|noscript)\b[^>]*>[\s\S]*?<\/\1\s*>/gi,
    '',
  );
  const resourceHtml = withoutInactiveContainers.replace(
    /(<script\b[^>]*>)[\s\S]*?<\/script\s*>/gi,
    '$1</script>',
  );

  const baseTags = [...resourceHtml.matchAll(/<base\b([^>]*)>/gi)];
  invariant(baseTags.length <= 1, 'HTML contém mais de um elemento base');
  let effectiveBaseUrl = new URL(documentUrl);
  if (baseTags.length === 1) {
    const baseAttributes = parseAttributes(baseTags[0][1]);
    invariant(baseAttributes.has('href'), 'Elemento base sem href');
    const approvedBaseUrl = assertSameOriginResource(
      baseAttributes.get('href'),
      documentUrl,
      'Elemento base',
    );
    invariant(approvedBaseUrl, 'Elemento base usa esquema não navegável');
    effectiveBaseUrl = approvedBaseUrl;
  }

  const tagPattern = /<(link|script|img|source)\b([^>]*)>/gi;

  for (const match of resourceHtml.matchAll(tagPattern)) {
    const tag = match[1].toLowerCase();
    const attributes = parseAttributes(match[2]);
    const candidates = [];

    if (attributes.has('href')) {
      candidates.push(attributes.get('href'));
    }
    if (attributes.has('src')) {
      candidates.push(attributes.get('src'));
    }
    if (attributes.has('srcset')) {
      candidates.push(
        ...attributes
          .get('srcset')
          .split(',')
          .map((entry) => entry.trim().split(/\s+/, 1)[0])
          .filter(Boolean),
      );
    }

    for (const candidate of candidates) {
      const url = assertSameOriginResource(candidate, effectiveBaseUrl, `Recurso HTML <${tag}>`);
      if (url) {
        resources.push(url);
        if (tag === 'link' && /(?:^|\s)stylesheet(?:\s|$)/i.test(attributes.get('rel') ?? '')) {
          invariant(!attributes.has('disabled'), 'Stylesheet desabilitado não comprova CSS aplicado');
          const media = (attributes.get('media') ?? '').trim().toLowerCase();
          invariant(
            media === '' || media === 'all' || media === 'screen',
            `Stylesheet usa mídia não comprovadamente aplicável: ${media}`,
          );
          stylesheets.push(url);
        }
      }
    }
  }

  const importMapPattern = /<script\b([^>]*)>([\s\S]*?)<\/script>/gi;
  for (const match of withoutInactiveContainers.matchAll(importMapPattern)) {
    const attributes = parseAttributes(match[1]);
    if ((attributes.get('type') ?? '').toLowerCase() !== 'importmap') {
      continue;
    }

    const importMap = JSON.parse(match[2]);
    const values = [
      ...Object.values(importMap.imports ?? {}),
      ...Object.values(importMap.scopes ?? {}).flatMap((scope) => Object.values(scope ?? {})),
    ];
    for (const value of values) {
      const url = assertSameOriginResource(value, effectiveBaseUrl, 'Entrada do import-map');
      if (url) {
        resources.push(url);
      }
    }
  }

  return { resources, stylesheets };
}

function extractCssReferences(css, stylesheetUrl) {
  const activeCss = stripCssComments(css);
  const importValues = [];
  const importPattern = /@import\s+(?:url\(\s*(?:(['"])(.*?)\1|([^\s'")]+))\s*\)|(['"])(.*?)\4)/gi;
  for (const match of activeCss.matchAll(importPattern)) {
    importValues.push(match[2] ?? match[3] ?? match[5]);
  }
  invariant(
    importValues.length === (activeCss.match(/@import\b/gi) ?? []).length,
    'Diretiva @import CSS não pôde ser validada',
  );

  const imports = importValues.map((value) => {
    const url = assertSameOriginResource(value, stylesheetUrl, 'Import CSS');
    invariant(url, 'Import CSS deve usar URL HTTP same-origin');
    return url;
  });
  const importHrefs = new Set(imports.map((url) => url.href));
  const resources = extractCssUrls(activeCss)
    .map((value) => assertSameOriginResource(value, stylesheetUrl, 'Recurso CSS'))
    .filter((url) => url && !importHrefs.has(url.href));

  return { imports, resources };
}

async function fetchRequiredBytes(url, label) {
  const { response, finalUrl } = await fetchWithSafeRedirects(url, {
    headers: { Accept: '*/*' },
  });
  invariant(response.status === 200, `${label} respondeu HTTP ${response.status}`);
  const bytes = Buffer.from(await response.arrayBuffer());
  invariant(bytes.length > 0, `${label} retornou corpo vazio`);
  return { bytes, finalUrl, contentType: response.headers.get('content-type') ?? '' };
}

async function validateHttpTheme() {
  const discoveryUrl = `${ISSUER}/.well-known/openid-configuration`;
  const discoveryResult = await fetchRequiredBytes(discoveryUrl, 'Discovery OIDC');
  invariant(
    /(?:application\/json|\+json)/i.test(discoveryResult.contentType),
    'Discovery OIDC não retornou JSON',
  );
  const discovery = JSON.parse(discoveryResult.bytes.toString('utf8'));
  invariant(discovery.issuer === ISSUER, 'Issuer publicado diverge do contrato aprovado');
  invariant(
    discovery.authorization_endpoint === `${ISSUER}/protocol/openid-connect/auth`,
    'authorization_endpoint publicado diverge do contrato aprovado',
  );
  assertSameOriginResource(discovery.authorization_endpoint, discoveryUrl, 'authorization_endpoint');

  const verifier = 'betufu-theme-validation-code-verifier-000000000000000000000000';
  const challenge = createHash('sha256').update(verifier).digest('base64url');
  const authorizationUrl = new URL(discovery.authorization_endpoint);
  authorizationUrl.search = new URLSearchParams({
    response_type: 'code',
    client_id: CLIENT_ID,
    redirect_uri: REDIRECT_URI,
    scope: 'openid',
    state: 'betufu-theme-validation-state',
    nonce: 'betufu-theme-validation-nonce',
    code_challenge: challenge,
    code_challenge_method: 'S256',
  }).toString();

  const cookieJar = createEphemeralCookieJar();
  const { response, finalUrl } = await fetchWithSafeRedirects(
    authorizationUrl,
    {
      headers: { Accept: 'text/html' },
    },
    cookieJar,
  );
  invariant(response.status === 200, `Página de login respondeu HTTP ${response.status}`);
  invariant(/text\/html/i.test(response.headers.get('content-type') ?? ''), 'Página de login não retornou HTML');
  const html = await response.text();
  invariant(html.trim().length > 0, 'Página de login retornou HTML vazio');
  const visibleHtml = assertRenderedNotice(html);
  invariant(
    /id=["']kc-page-title["']/i.test(visibleHtml),
    'Resposta não contém o marcador visível do login Keycloak',
  );

  const { resources: loginResources, stylesheets: loginStylesheets } = extractHtmlResources(html, finalUrl);
  invariant(loginStylesheets.length > 0, 'Página de login não referencia folhas de estilo');
  invariant(
    loginStylesheets.some((url) => url.pathname.endsWith('/login/betufu/css/login.css')),
    'A página de login não referencia diretamente css/login.css do tema betufu',
  );

  const registrationLinkMatch = html.match(/href=["']([^"']*\/login-actions\/registration[^"']*)["']/i);
  invariant(
    registrationLinkMatch,
    'KEYCLOAK_THEME_REGISTRATION_PAGE: Página de login não contém link de cadastro',
  );
  let registrationUrl;
  try {
    registrationUrl = new URL(registrationLinkMatch[1].replace(/&amp;/g, '&'), finalUrl);
  } catch {
    throw new Error('KEYCLOAK_THEME_REGISTRATION_PAGE: Link de cadastro contém URL malformada');
  }
  assertSameOriginResource(
    registrationUrl.href,
    finalUrl,
    'KEYCLOAK_THEME_REGISTRATION_PAGE: Link de cadastro',
  );

  const { response: regResponse, finalUrl: regFinalUrl } = await fetchWithSafeRedirects(
    registrationUrl,
    {
      headers: { Accept: 'text/html' },
    },
    cookieJar,
  );
  invariant(
    regResponse.status === 200,
    `KEYCLOAK_THEME_REGISTRATION_PAGE: Página de cadastro respondeu HTTP ${regResponse.status}`,
  );
  invariant(
    /text\/html/i.test(regResponse.headers.get('content-type') ?? ''),
    'KEYCLOAK_THEME_REGISTRATION_PAGE: Página de cadastro não retornou HTML',
  );
  const regHtml = await regResponse.text();
  invariant(
    regHtml.trim().length > 0,
    'KEYCLOAK_THEME_REGISTRATION_PAGE: Página de cadastro retornou HTML vazio',
  );
  const regVisibleHtml = assertRenderedNotice(regHtml);
  invariant(
    /id=["']kc-page-title["']/i.test(regVisibleHtml),
    'KEYCLOAK_THEME_REGISTRATION_PAGE: Resposta não contém o marcador visível da página de cadastro Keycloak',
  );
  invariant(
    !/name=["']password(?:-confirm)?["']/i.test(regHtml),
    'KEYCLOAK_THEME_NO_INITIAL_PASSWORD: Página inicial de cadastro não pode conter campos de senha',
  );

  const inputTags = [...regHtml.matchAll(/<input\b([^>]*)>/gi)];
  const editableInputNames = [];
  for (const match of inputTags) {
    const attributes = parseAttributes(match[1]);
    const type = (attributes.get('type') ?? 'text').toLowerCase();
    if (['hidden', 'submit', 'button'].includes(type)) {
      continue;
    }
    const name = attributes.get('name');
    if (name) {
      editableInputNames.push(name);
    }
  }
  invariant(
    editableInputNames.length === 1 && editableInputNames[0] === 'email',
    `KEYCLOAK_THEME_REGISTRATION_INPUTS: A página de cadastro deve conter exatamente o input editável [email], encontrados: [${editableInputNames.join(', ')}]`,
  );

  const { resources: regResources, stylesheets: regStylesheets } = extractHtmlResources(regHtml, regFinalUrl);
  invariant(
    regStylesheets.length > 0,
    'KEYCLOAK_THEME_REGISTRATION_PAGE: Página de cadastro não referencia folhas de estilo',
  );
  invariant(
    regStylesheets.some((url) => url.pathname.endsWith('/login/betufu/css/login.css')),
    'KEYCLOAK_THEME_REGISTRATION_PAGE: A página de cadastro não referencia diretamente css/login.css do tema betufu',
  );

  const combinedStylesheets = [...loginStylesheets, ...regStylesheets];
  const uniqueStylesheets = [...new Map(combinedStylesheets.map((url) => [url.href, url])).values()];

  const localCssBytes = await readFile(CSS_PATH);
  let customCssFound = false;
  let customCssUrl;
  const cssResourceUrls = [];
  const stylesheetQueue = [...uniqueStylesheets];
  const inspectedStylesheets = new Set();

  while (stylesheetQueue.length > 0) {
    invariant(inspectedStylesheets.size < 64, 'Limite de folhas CSS same-origin excedido');
    const stylesheetUrl = stylesheetQueue.shift();
    if (inspectedStylesheets.has(stylesheetUrl.href)) {
      continue;
    }
    inspectedStylesheets.add(stylesheetUrl.href);

    const result = await fetchRequiredBytes(stylesheetUrl, `Stylesheet ${stylesheetUrl.pathname}`);
    const css = result.bytes.toString('utf8');
    const references = extractCssReferences(css, result.finalUrl);
    stylesheetQueue.push(
      ...references.imports.filter((url) => !inspectedStylesheets.has(url.href)),
    );
    cssResourceUrls.push(...references.resources);

    if (result.finalUrl.pathname.endsWith('/login/betufu/css/login.css')) {
      customCssFound = true;
      customCssUrl = result.finalUrl;
      invariant(result.bytes.equals(localCssBytes), 'login.css servido diverge dos bytes locais aprovados');
    }
  }

  invariant(customCssFound, 'A página real não carregou o css/login.css do tema betufu');

  const directResources = [...loginResources, ...regResources, ...cssResourceUrls].filter(
    (url) => !inspectedStylesheets.has(url.href),
  );
  const uniqueResources = [...new Map(directResources.map((url) => [url.href, url])).values()];
  invariant(uniqueResources.length <= 256, 'Limite de recursos same-origin excedido');
  const localValidationScriptBytes = await readFile(VALIDATION_SCRIPT_PATH);
  let validationScriptFound = false;
  for (const resourceUrl of uniqueResources) {
    const result = await fetchRequiredBytes(resourceUrl, `Recurso same-origin ${resourceUrl.pathname}`);
    if (result.finalUrl.pathname.endsWith('/login/betufu/js/required-field-validation.js')) {
      validationScriptFound = true;
      invariant(
        result.bytes.equals(localValidationScriptBytes),
        'Script D-15 servido diverge dos bytes locais aprovados',
      );
    }
  }
  invariant(validationScriptFound, 'A página real não carregou o script D-15 do tema betufu');

  for (const font of EXPECTED_FONTS) {
    const fontUrl = new URL(font.url, customCssUrl);
    const result = await fetchRequiredBytes(fontUrl, `Fonte ${font.url}`);
    const asset = EXPECTED_ASSETS.get(`resources/fonts/${font.url.split('/').at(-1)}`);
    invariant(asset, `Fonte sem contrato local: ${font.url}`);
    invariant(result.bytes.length === asset.bytes, `Tamanho HTTP inválido para ${font.url}`);
    invariant(sha256(result.bytes) === asset.sha256, `SHA-256 HTTP inválido para ${font.url}`);
  }
}

function sanitizeError(error) {
  const message = error instanceof Error ? error.message : String(error);
  return message.replace(/[\r\n\t]+/g, ' ').slice(0, 500);
}

async function main() {
  const argumentsList = process.argv.slice(2);
  invariant(
    argumentsList.length === 0 ||
      (argumentsList.length === 1 && argumentsList[0] === '--local-only'),
    'Uso: node scripts/validate-keycloak-theme.mjs [--local-only]',
  );

  await validateLocalTheme();

  if (argumentsList[0] === '--local-only') {
    console.log('Tema Keycloak validado localmente: contrato, arquivos e hashes aprovados.');
    return;
  }

  await validateHttpTheme();
  console.log('Tema Keycloak validado localmente e por HTTP same-origin.');
}

main().catch((error) => {
  console.error(`Falha na validação do tema Keycloak: ${sanitizeError(error)}`);
  process.exitCode = 1;
});
