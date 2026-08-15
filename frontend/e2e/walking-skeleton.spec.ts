import {
  expect,
  test,
  type Page,
  type Request,
  type Response,
} from '@playwright/test';

const CLIENT_ORIGIN = 'http://localhost:4200';
const KEYCLOAK_ORIGIN = 'http://localhost:8081';
const IDENTITY_PATH = '/api/v1/tracer/identity/current';
const SAFE_TECHNICAL_USERNAME = 'technical';
const KEYCLOAK_REALM_PATH = '/realms/betufu-local/';
const TOKEN_MARKER_SOURCE =
  String.raw`(?:access[_-]?token|refresh[_-]?token|id[_-]?token|bearer\s+[a-z0-9._~-]+|eyj[a-z0-9_-]{10,}\.[a-z0-9_-]{10,}\.[a-z0-9_-]{10,})`;
const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

type Scenario = 'walking-skeleton' | 'production-absence';

interface NetworkProof {
  authorizationHeaderSeen: boolean;
  bffPathOutsideClientOriginSeen: boolean;
  bffRequestCount: number;
  directBackendRequestSeen: boolean;
  headerInspectionFailed: boolean;
  identityPutCount: number;
  pendingHeaderChecks: Promise<void>[];
  tokenEndpointSeen: boolean;
}

type ExposureSignal =
  | 'cookie-write'
  | 'dom-token'
  | 'indexeddb-entry'
  | 'observer-failed'
  | 'storage-entry'
  | 'url-token';

interface ExposureProof {
  cookieWriteSeen: boolean;
  cookieInspectionFailed: boolean;
  domTokenSeen: boolean;
  indexedDbEntrySeen: boolean;
  mainFrameUrlTokenSeen: boolean;
  observerFailed: boolean;
  pendingCookieChecks: Promise<void>[];
  requestUrlTokenSeen: boolean;
  setCookieViolations: Set<SetCookieViolation>;
  storageEntrySeen: boolean;
}

interface BrowserExposureSnapshot {
  domTokenSeen: boolean;
  indexedDbEntrySeen: boolean;
  observerFailed: boolean;
  storageEntrySeen: boolean;
  urlTokenSeen: boolean;
}

type CookieRuleId =
  | 'session'
  | 'xsrf'
  | 'auth-session'
  | 'auth-hash'
  | 'restart'
  | 'identity'
  | 'keycloak-session';

interface ExpectedCookieRule {
  httpOnly: boolean;
  id: CookieRuleId;
  name: string;
  path: string;
  responseOrigin: string;
  secure: boolean;
}

type SetCookieViolation =
  | 'format'
  | `http-only-${CookieRuleId}`
  | 'name-origin'
  | 'path'
  | 'readable-token'
  | 'secure-client'
  | 'secure-keycloak';

const EXPECTED_COOKIE_RULES: readonly ExpectedCookieRule[] = [
  {
    httpOnly: true,
    id: 'session',
    name: 'SESSION',
    path: '/',
    responseOrigin: CLIENT_ORIGIN,
    secure: false,
  },
  {
    httpOnly: false,
    id: 'xsrf',
    name: 'XSRF-TOKEN',
    path: '/',
    responseOrigin: CLIENT_ORIGIN,
    secure: false,
  },
  {
    httpOnly: true,
    id: 'auth-session',
    name: 'AUTH_SESSION_ID',
    path: KEYCLOAK_REALM_PATH,
    responseOrigin: KEYCLOAK_ORIGIN,
    secure: true,
  },
  {
    httpOnly: false,
    id: 'auth-hash',
    name: 'KC_AUTH_SESSION_HASH',
    path: KEYCLOAK_REALM_PATH,
    responseOrigin: KEYCLOAK_ORIGIN,
    secure: true,
  },
  {
    httpOnly: true,
    id: 'restart',
    name: 'KC_RESTART',
    path: KEYCLOAK_REALM_PATH,
    responseOrigin: KEYCLOAK_ORIGIN,
    secure: true,
  },
  {
    httpOnly: true,
    id: 'identity',
    name: 'KEYCLOAK_IDENTITY',
    path: KEYCLOAK_REALM_PATH,
    responseOrigin: KEYCLOAK_ORIGIN,
    secure: true,
  },
  {
    httpOnly: false,
    id: 'keycloak-session',
    name: 'KEYCLOAK_SESSION',
    path: KEYCLOAK_REALM_PATH,
    responseOrigin: KEYCLOAK_ORIGIN,
    secure: true,
  },
];

function containsTokenMarker(value: string): boolean {
  return new RegExp(TOKEN_MARKER_SOURCE, 'i').test(value);
}

function expectedCookieRule(
  name: string,
  responseOrigin?: string,
): ExpectedCookieRule | undefined {
  return EXPECTED_COOKIE_RULES.find(
    (rule) => rule.name === name &&
      (responseOrigin === undefined || rule.responseOrigin === responseOrigin),
  );
}

function inspectSetCookieHeader(
  header: string,
  responseOrigin: string,
): SetCookieViolation | null {
  const parts = header.split(';').map((part) => part.trim());
  const nameValue = parts[0] ?? '';
  const separator = nameValue.indexOf('=');
  if (separator <= 0) {
    return 'format';
  }

  const name = nameValue.slice(0, separator);
  const value = nameValue.slice(separator + 1);
  const rule = expectedCookieRule(name, responseOrigin);
  if (rule === undefined) {
    return 'name-origin';
  }

  const attributes = parts.slice(1);
  const pathAttribute = attributes.find((attribute) =>
    attribute.toLowerCase().startsWith('path='),
  );
  const httpOnly = attributes.some(
    (attribute) => attribute.toLowerCase() === 'httponly',
  );
  const secure = attributes.some(
    (attribute) => attribute.toLowerCase() === 'secure',
  );
  const hasNoValue = value.length === 0;

  if (pathAttribute?.slice('path='.length) !== rule.path) {
    return 'path';
  }
  // A transient empty Set-Cookie has no value to protect. Active cookies remain
  // exact and the final browser cookie jar is checked separately below.
  if (!hasNoValue && httpOnly !== rule.httpOnly) {
    return `http-only-${rule.id}`;
  }
  if (!hasNoValue && secure !== rule.secure) {
    return responseOrigin === CLIENT_ORIGIN ? 'secure-client' : 'secure-keycloak';
  }
  if (!rule.httpOnly && containsTokenMarker(`${name}\n${value}`)) {
    return 'readable-token';
  }
  return null;
}

async function observeExposure(page: Page): Promise<ExposureProof> {
  const proof: ExposureProof = {
    cookieWriteSeen: false,
    cookieInspectionFailed: false,
    domTokenSeen: false,
    indexedDbEntrySeen: false,
    mainFrameUrlTokenSeen: false,
    observerFailed: false,
    pendingCookieChecks: [],
    requestUrlTokenSeen: false,
    setCookieViolations: new Set(),
    storageEntrySeen: false,
  };

  await page.exposeBinding(
    '__betufuRecordExposure',
    (_source, signal: unknown) => {
      switch (signal) {
        case 'cookie-write':
          proof.cookieWriteSeen = true;
          break;
        case 'dom-token':
          proof.domTokenSeen = true;
          break;
        case 'indexeddb-entry':
          proof.indexedDbEntrySeen = true;
          break;
        case 'storage-entry':
          proof.storageEntrySeen = true;
          break;
        case 'url-token':
          proof.mainFrameUrlTokenSeen = true;
          break;
        default:
          proof.observerFailed = true;
      }
    },
  );

  await page.addInitScript(({ markerSource }) => {
    type ExposureReporter = (signal: ExposureSignal) => Promise<void>;
    const pageGlobal = globalThis as typeof globalThis & {
      __betufuRecordExposure: ExposureReporter;
    };
    const marker = new RegExp(markerSource, 'i');
    const report = (signal: ExposureSignal): void => {
      void pageGlobal.__betufuRecordExposure(signal).catch(() => undefined);
    };
    const inspectDocument = (): void => {
      try {
        const serializedDom = document.documentElement?.outerHTML ?? '';
        const controlTokenSeen = Array.from(
          document.querySelectorAll<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>(
            'input, textarea, select',
          ),
        ).some((control) => marker.test(control.value));
        if (marker.test(serializedDom) || controlTokenSeen) {
          report('dom-token');
        }
        if (marker.test(location.href)) {
          report('url-token');
        }
      } catch {
        report('observer-failed');
      }
    };

    try {
      const originalSetItem = Storage.prototype.setItem;
      Storage.prototype.setItem = function setItem(key: string, value: string): void {
        report('storage-entry');
        originalSetItem.call(this, key, value);
      };

      const originalOpen = IDBFactory.prototype.open;
      IDBFactory.prototype.open = function open(
        name: string,
        version?: number,
      ): IDBOpenDBRequest {
        report('indexeddb-entry');
        return version === undefined
          ? originalOpen.call(this, name)
          : originalOpen.call(this, name, version);
      };

      const cookieDescriptor = Object.getOwnPropertyDescriptor(Document.prototype, 'cookie');
      if (cookieDescriptor?.get !== undefined && cookieDescriptor.set !== undefined) {
        Object.defineProperty(Document.prototype, 'cookie', {
          configurable: cookieDescriptor.configurable,
          enumerable: cookieDescriptor.enumerable,
          get: cookieDescriptor.get,
          set(value: string) {
            report('cookie-write');
            cookieDescriptor.set?.call(this, value);
          },
        });
      } else {
        report('observer-failed');
      }

      const startObserver = (): void => {
        inspectDocument();
        if (document.documentElement === null) {
          report('observer-failed');
          return;
        }
        new MutationObserver(inspectDocument).observe(document.documentElement, {
          attributes: true,
          characterData: true,
          childList: true,
          subtree: true,
        });
        document.addEventListener('input', inspectDocument, true);
        addEventListener('hashchange', inspectDocument);
        addEventListener('popstate', inspectDocument);
      };

      if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', startObserver, { once: true });
      } else {
        startObserver();
      }
    } catch {
      report('observer-failed');
    }
  }, { markerSource: TOKEN_MARKER_SOURCE });

  page.on('framenavigated', (frame) => {
    if (frame === page.mainFrame() && containsTokenMarker(frame.url())) {
      proof.mainFrameUrlTokenSeen = true;
    }
  });
  page.on('request', (request) => {
    if (containsTokenMarker(request.url())) {
      proof.requestUrlTokenSeen = true;
    }
  });
  page.on('response', (response) => {
    proof.pendingCookieChecks.push(
      response.headersArray().then(
        (headers) => {
          let responseOrigin: string;
          try {
            responseOrigin = new URL(response.url()).origin;
          } catch {
            proof.cookieInspectionFailed = true;
            return;
          }
          const setCookieHeaders = headers.filter(
            ({ name }) => name.toLowerCase() === 'set-cookie',
          );
          for (const { value } of setCookieHeaders) {
            const violation = inspectSetCookieHeader(value, responseOrigin);
            if (violation !== null) {
              proof.setCookieViolations.add(violation);
            }
          }
        },
        () => {
          proof.cookieInspectionFailed = true;
        },
      ),
    );
  });

  return proof;
}

function readScenario(): Scenario {
  const value = process.env['BETUFU_E2E_SCENARIO'];
  if (value !== 'walking-skeleton' && value !== 'production-absence') {
    throw new Error('BETUFU_E2E_SCENARIO deve selecionar um cenário permitido.');
  }

  return value;
}

function readTechnicalPassword(): string {
  const value = process.env['BETUFU_TECHNICAL_PASSWORD'];
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error('A credencial técnica do cenário não foi fornecida.');
  }

  return value;
}

function hasBffPath(pathname: string): boolean {
  return /^\/(?:api|oauth2|login)(?:\/|$)/.test(pathname);
}

function isTokenEndpoint(pathname: string): boolean {
  const segments = pathname.split('/').filter((segment) => segment.length > 0);
  const marker = ['protocol', 'openid-connect', 'token'];

  return marker.every((segment, index) =>
    segments.some(
      (candidate, candidateIndex) =>
        candidate === segment &&
        (index === 0 || candidateIndex === segments.indexOf(marker[index - 1]!) + 1),
    ),
  );
}

function isIdentityRequest(request: Request): boolean {
  const url = new URL(request.url());
  return (
    request.method() === 'PUT' &&
    url.origin === CLIENT_ORIGIN &&
    url.pathname === IDENTITY_PATH &&
    url.search === '' &&
    url.hash === ''
  );
}

function observeNetwork(page: Page): NetworkProof {
  const proof: NetworkProof = {
    authorizationHeaderSeen: false,
    bffPathOutsideClientOriginSeen: false,
    bffRequestCount: 0,
    directBackendRequestSeen: false,
    headerInspectionFailed: false,
    identityPutCount: 0,
    pendingHeaderChecks: [],
    tokenEndpointSeen: false,
  };

  page.on('request', (request) => {
    const url = new URL(request.url());
    const bffPath = hasBffPath(url.pathname);

    if (url.origin === CLIENT_ORIGIN && bffPath) {
      proof.bffRequestCount += 1;
    } else if (bffPath) {
      proof.bffPathOutsideClientOriginSeen = true;
    }

    if (url.origin === 'http://localhost:8080' || url.origin === 'http://127.0.0.1:8080') {
      proof.directBackendRequestSeen = true;
    }

    if (isTokenEndpoint(url.pathname)) {
      proof.tokenEndpointSeen = true;
    }

    if (isIdentityRequest(request)) {
      proof.identityPutCount += 1;
    }

    proof.pendingHeaderChecks.push(
      request.allHeaders().then(
        (headers) => {
          const authorization = headers['authorization'];
          if (typeof authorization === 'string' && authorization.trim().length > 0) {
            proof.authorizationHeaderSeen = true;
          }
        },
        () => {
          proof.headerInspectionFailed = true;
        },
      ),
    );
  });

  return proof;
}

async function flushHeaderObservations(proof: NetworkProof): Promise<void> {
  const pending = proof.pendingHeaderChecks.splice(0);
  await Promise.all(pending);
}

async function flushCookieObservations(proof: ExposureProof): Promise<void> {
  while (proof.pendingCookieChecks.length > 0) {
    const pending = proof.pendingCookieChecks.splice(0);
    await Promise.all(pending);
  }
}

async function expectNoClientVisibleTokens(
  page: Page,
  exposure: ExposureProof,
): Promise<void> {
  const snapshot = await page.evaluate(async ({ markerSource }) => {
    const marker = new RegExp(markerSource, 'i');
    let indexedDbEntrySeen = true;
    try {
      indexedDbEntrySeen = (await indexedDB.databases()).length !== 0;
    } catch {
      indexedDbEntrySeen = true;
    }
    const controlTokenSeen = Array.from(
      document.querySelectorAll<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>(
        'input, textarea, select',
      ),
    ).some((control) => marker.test(control.value));

    return {
      domTokenSeen:
        marker.test(document.documentElement?.outerHTML ?? '') || controlTokenSeen,
      indexedDbEntrySeen,
      observerFailed: false,
      storageEntrySeen: localStorage.length !== 0 || sessionStorage.length !== 0,
      urlTokenSeen: marker.test(location.href),
    } satisfies BrowserExposureSnapshot;
  }, { markerSource: TOKEN_MARKER_SOURCE });
  await flushCookieObservations(exposure);

  const cookies = await page.context().cookies([CLIENT_ORIGIN, KEYCLOAK_ORIGIN]);
  const currentCookieViolation = cookies.some((cookie) => {
    const rule = expectedCookieRule(cookie.name);
    return rule === undefined ||
      cookie.domain !== 'localhost' ||
      cookie.path !== rule.path ||
      cookie.httpOnly !== rule.httpOnly ||
      cookie.secure !== rule.secure ||
      cookie.value.length === 0 ||
      (!rule.httpOnly && containsTokenMarker(`${cookie.name}\n${cookie.value}`));
  });

  const exposureChecks = [
    [
      'security: response cookie names',
      exposure.setCookieViolations.has('format') === false &&
        exposure.setCookieViolations.has('name-origin') === false,
    ],
    ['security: response cookie paths', exposure.setCookieViolations.has('path') === false],
    ['security: response SESSION HttpOnly', !exposure.setCookieViolations.has('http-only-session')],
    ['security: response XSRF HttpOnly', !exposure.setCookieViolations.has('http-only-xsrf')],
    [
      'security: response AUTH_SESSION_ID HttpOnly',
      !exposure.setCookieViolations.has('http-only-auth-session'),
    ],
    [
      'security: response KC_AUTH_SESSION_HASH HttpOnly',
      !exposure.setCookieViolations.has('http-only-auth-hash'),
    ],
    [
      'security: response KC_RESTART HttpOnly',
      !exposure.setCookieViolations.has('http-only-restart'),
    ],
    [
      'security: response KEYCLOAK_IDENTITY HttpOnly',
      !exposure.setCookieViolations.has('http-only-identity'),
    ],
    [
      'security: response KEYCLOAK_SESSION HttpOnly',
      !exposure.setCookieViolations.has('http-only-keycloak-session'),
    ],
    [
      'security: response client cookie Secure',
      exposure.setCookieViolations.has('secure-client') === false,
    ],
    [
      'security: response Keycloak cookie Secure',
      exposure.setCookieViolations.has('secure-keycloak') === false,
    ],
    [
      'security: response cookie tokens',
      exposure.setCookieViolations.has('readable-token') === false,
    ],
    ['security: current cookies allowlisted', currentCookieViolation === false],
    ['security: no script cookie writes', exposure.cookieWriteSeen === false],
    ['security: cookie inspection', exposure.cookieInspectionFailed === false],
    [
      'security: DOM without tokens',
      exposure.domTokenSeen === false && snapshot.domTokenSeen === false,
    ],
    [
      'security: IndexedDB empty',
      exposure.indexedDbEntrySeen === false && snapshot.indexedDbEntrySeen === false,
    ],
    [
      'security: main-frame URL without tokens',
      exposure.mainFrameUrlTokenSeen === false && snapshot.urlTokenSeen === false,
    ],
    [
      'security: observers active',
      exposure.observerFailed === false && snapshot.observerFailed === false,
    ],
    ['security: request URLs without tokens', exposure.requestUrlTokenSeen === false],
    [
      'security: storage empty',
      exposure.storageEntrySeen === false && snapshot.storageEntrySeen === false,
    ],
  ] as const;
  for (const [title, passed] of exposureChecks) {
    await test.step(title, async () => {
      expect(passed).toBe(true);
    });
  }
}

async function expectLoginBootstrap(page: Page, exposure: ExposureProof): Promise<void> {
  const response = await page.goto('/login', { waitUntil: 'domcontentloaded' });
  const cookies = await page.context().cookies(CLIENT_ORIGIN);
  const xsrfCookies = cookies.filter((cookie) => cookie.name === 'XSRF-TOKEN');
  const xsrfCookie = xsrfCookies[0];
  const locationIsClient = await page.evaluate(
    (clientOrigin) => location.origin === clientOrigin && location.pathname === '/login',
    CLIENT_ORIGIN,
  );

  const bootstrapChecks = [
    ['walking: bootstrap cookie unico', xsrfCookies.length === 1],
    ['walking: bootstrap cookie host-only', xsrfCookie?.domain === 'localhost'],
    ['walking: bootstrap cookie legivel', xsrfCookie?.httpOnly === false],
    ['walking: bootstrap cookie SameSite', xsrfCookie?.sameSite === 'Lax'],
    ['walking: bootstrap cookie sem Secure', xsrfCookie?.secure === false],
    ['walking: bootstrap cookie path', xsrfCookie?.path === '/'],
    [
      'walking: bootstrap cookie presente',
      typeof xsrfCookie?.value === 'string' && xsrfCookie.value.length > 0,
    ],
    ['walking: bootstrap origem cliente', locationIsClient],
    ['walking: bootstrap resposta valida', response?.status() === 200],
  ] as const;

  for (const [title, passed] of bootstrapChecks) {
    await test.step(title, async () => {
      expect(passed).toBe(true);
    });
  }
  await expectNoClientVisibleTokens(page, exposure);
}

async function proveHostedLoginAndD15(
  page: Page,
  exposure: ExposureProof,
): Promise<void> {
  await test.step('walking: pagina Keycloak hospedada', async () => {
    await page.goto('/oauth2/authorization/keycloak', {
      waitUntil: 'domcontentloaded',
    });
    await page.locator('#kc-form-login').waitFor({ state: 'visible' });

    const hostedFormProof = await page.evaluate((keycloakOrigin) => ({
      formCountIsOne: document.querySelectorAll('#kc-form-login').length === 1,
      independenceNoticeCountIsOne:
        document.querySelectorAll('.betufu-independence-notice').length === 1,
      keycloakOriginMatches: location.origin === keycloakOrigin,
    }), KEYCLOAK_ORIGIN);

    expect(hostedFormProof).toEqual({
      formCountIsOne: true,
      independenceNoticeCountIsOne: true,
      keycloakOriginMatches: true,
    });
  });

  const usernameField = page.locator('#username');
  const passwordField = page.locator('#password');
  const submitButton = page.locator('#kc-login');

  await test.step('walking: D-15 validacao obrigatoria', async () => {
    await usernameField.fill(SAFE_TECHNICAL_USERNAME);
    await passwordField.fill('');
    await submitButton.click();
    await page.locator('#kc-form-login').waitFor({ state: 'visible' });

    const validationProof = await page.evaluate((safeUsername) => {
      const username = document.querySelector<HTMLInputElement>('#username');
      const password = document.querySelector<HTMLInputElement>('#password');
      const associatedErrorIds = (field: HTMLInputElement): string[] => {
        const referencedIds = [
          field.getAttribute('aria-errormessage') ?? '',
          field.getAttribute('aria-describedby') ?? '',
        ]
          .join(' ')
          .split(/\s+/)
          .filter((id) => id.length > 0);

        return referencedIds.filter((id) => {
          const node = document.getElementById(id);
          const semanticMarker = `${id} ${node?.className ?? ''}`.toLowerCase();
          return node !== null &&
            node.hidden === false &&
            Boolean(node.textContent?.trim().length) &&
            (semanticMarker.includes('error') ||
              node.getAttribute('role') === 'alert' ||
              node.getAttribute('aria-live') !== null);
        });
      };
      const usernameErrorIds = username === null ? [] : associatedErrorIds(username);
      const passwordErrorIds = password === null ? [] : associatedErrorIds(password);

      return {
        fieldsExist: username !== null && password !== null,
        firstInvalidFieldFocused: password !== null && document.activeElement === password,
        hostedFormPreserved:
          location.origin === 'http://localhost:8081' &&
          document.querySelectorAll('#kc-form-login').length === 1,
        passwordHasAssociatedError:
          passwordErrorIds.length > 0 &&
          passwordErrorIds.some((id) => !usernameErrorIds.includes(id)),
        passwordIsInvalid: password?.getAttribute('aria-invalid') === 'true',
        passwordIsEmpty: password?.value === '',
        passwordIsRequired:
          password?.required === true && password.validity.valueMissing,
        safeUsernamePreserved: username?.value === safeUsername,
        usernameIsNotInvalid:
          username?.getAttribute('aria-invalid') !== 'true' && usernameErrorIds.length === 0,
      };
    }, SAFE_TECHNICAL_USERNAME);

    const d15Checks = [
      ['walking: D-15 campos presentes', validationProof.fieldsExist],
      ['walking: D-15 formulario hospedado', validationProof.hostedFormPreserved],
      ['walking: D-15 erro associado a senha', validationProof.passwordHasAssociatedError],
      ['walking: D-15 senha invalida', validationProof.passwordIsInvalid],
      ['walking: D-15 senha vazia', validationProof.passwordIsEmpty],
      ['walking: D-15 senha obrigatoria', validationProof.passwordIsRequired],
      ['walking: D-15 usuario preservado', validationProof.safeUsernamePreserved],
      ['walking: D-15 usuario sem erro', validationProof.usernameIsNotInvalid],
      ['walking: D-15 foco na senha', validationProof.firstInvalidFieldFocused],
    ] as const;
    for (const [title, passed] of d15Checks) {
      await test.step(title, async () => {
        expect(passed).toBe(true);
      });
    }

    await expectNoClientVisibleTokens(page, exposure);
  });

  await test.step('walking: autenticacao hospedada', async () => {
    let technicalPassword = readTechnicalPassword();
    await passwordField.fill(technicalPassword);
    technicalPassword = '';

    await Promise.all([
      page.waitForURL(
        (url) =>
          url.origin === CLIENT_ORIGIN &&
          url.pathname === '/' &&
          url.search === '' &&
          url.hash === '',
        { waitUntil: 'domcontentloaded' },
      ),
      submitButton.click(),
    ]);
  });

  await test.step('walking: retorno sem token', async () => {
    const returnLocationProof = await page.evaluate((clientOrigin) => ({
      hashIsEmpty: location.hash === '',
      originMatches: location.origin === clientOrigin,
      pathnameIsRoot: location.pathname === '/',
      searchIsEmpty: location.search === '',
    }), CLIENT_ORIGIN);

    expect(returnLocationProof).toEqual({
      hashIsEmpty: true,
      originMatches: true,
      pathnameIsRoot: true,
      searchIsEmpty: true,
    });

    await expectNoClientVisibleTokens(page, exposure);
  });
}

function readSafeIdentityPayload(payload: unknown): {
  identityId: string | null;
  shapeIsAllowed: boolean;
  statusIsPersisted: boolean;
} {
  if (typeof payload !== 'object' || payload === null || Array.isArray(payload)) {
    return {
      identityId: null,
      shapeIsAllowed: false,
      statusIsPersisted: false,
    };
  }

  const record = payload as Record<string, unknown>;
  const identityId =
    typeof record['identityId'] === 'string' && UUID_PATTERN.test(record['identityId'])
      ? record['identityId']
      : null;
  const keys = Object.keys(record).sort();

  return {
    identityId,
    shapeIsAllowed:
      keys.length === 4 &&
      keys[0] === 'createdAt' &&
      keys[1] === 'identityId' &&
      keys[2] === 'status' &&
      keys[3] === 'updatedAt',
    statusIsPersisted: record['status'] === 'PERSISTED',
  };
}

async function performIdentityPut(
  page: Page,
  network: NetworkProof,
  expectedPutCount: number,
): Promise<string> {
  const responsePromise = page.waitForResponse((candidate) =>
    isIdentityRequest(candidate.request()),
  );

  await page.getByRole('button', { name: 'Verificar integração', exact: true }).click();
  const response: Response = await responsePromise;
  const [headers, cookies, payload] = await Promise.all([
    response.request().allHeaders(),
    page.context().cookies(CLIENT_ORIGIN),
    response.json() as Promise<unknown>,
  ]);
  const payloadProof = readSafeIdentityPayload(payload);
  const xsrfCookies = cookies.filter((cookie) => cookie.name === 'XSRF-TOKEN');
  const xsrfCookie = xsrfCookies[0];
  const xsrfHeader = headers['x-xsrf-token'];
  const requestLocation = new URL(response.request().url());

  const putChecks = [
    ['walking: PUT sem Authorization', headers['authorization'] === undefined],
    ['walking: PUT XSRF unico', xsrfCookies.length === 1],
    ['walking: PUT XSRF legivel', xsrfCookie?.httpOnly === false],
    ['walking: PUT XSRF SameSite', xsrfCookie?.sameSite === 'Lax'],
    ['walking: PUT XSRF sem Secure', xsrfCookie?.secure === false],
    ['walking: PUT XSRF path', xsrfCookie?.path === '/'],
    ['walking: PUT identidade UUID', payloadProof.identityId !== null],
    [
      'walking: PUT XSRF automatico',
      typeof xsrfCookie?.value === 'string' &&
      xsrfCookie.value.length > 0 &&
      typeof xsrfHeader === 'string' &&
      xsrfHeader.length > 0 &&
      xsrfHeader === xsrfCookie.value,
    ],
    ['walking: PUT contagem correta', network.identityPutCount === expectedPutCount],
    [
      'walking: PUT mesma origem',
      requestLocation.origin === CLIENT_ORIGIN &&
      requestLocation.pathname === IDENTITY_PATH,
    ],
    ['walking: PUT resposta 200', response.status() === 200],
    ['walking: PUT shape permitido', payloadProof.shapeIsAllowed],
    ['walking: PUT status persistido', payloadProof.statusIsPersisted],
  ] as const;

  for (const [title, passed] of putChecks) {
    await test.step(title, async () => {
      expect(passed).toBe(true);
    });
  }

  if (payloadProof.identityId === null) {
    throw new Error('A resposta do vínculo não contém identidade opaca válida.');
  }

  const renderedIdentityMatches = await page.locator('main code').evaluate(
    (element, identityId) => element.textContent?.trim() === identityId,
    payloadProof.identityId,
  );
  await test.step('walking: PUT identidade renderizada', async () => {
    expect(renderedIdentityMatches).toBe(true);
  });

  return payloadProof.identityId;
}

async function runWalkingSkeleton(
  page: Page,
  network: NetworkProof,
  exposure: ExposureProof,
): Promise<void> {
  await test.step('walking: bootstrap XSRF', async () => {
    await expectLoginBootstrap(page, exposure);
  });
  await proveHostedLoginAndD15(page, exposure);

  let firstIdentityId = '';
  await test.step('walking: primeiro PUT', async () => {
    await page.goto('/tracer', { waitUntil: 'networkidle' });
    await test.step('walking: antes do PUT sem chamada', async () => {
      expect(network.identityPutCount).toBe(0);
    });

    firstIdentityId = await performIdentityPut(page, network, 1);
    await page.waitForLoadState('networkidle');
    await expectNoClientVisibleTokens(page, exposure);
  });

  await test.step('walking: reload', async () => {
    await page.reload({ waitUntil: 'networkidle' });
    const reloadProof = await page.evaluate((clientOrigin) => ({
      originMatches: location.origin === clientOrigin,
      pathnameIsTracer: location.pathname === '/tracer',
    }), CLIENT_ORIGIN);
    expect({
      originMatches: reloadProof.originMatches,
      pathnameIsTracer: reloadProof.pathnameIsTracer,
      putCountUnchanged: network.identityPutCount === 1,
    }).toEqual({
      originMatches: true,
      pathnameIsTracer: true,
      putCountUnchanged: true,
    });
    await expectNoClientVisibleTokens(page, exposure);
  });

  let secondIdentityId = '';
  await test.step('walking: segundo PUT', async () => {
    secondIdentityId = await performIdentityPut(page, network, 2);
    await page.waitForLoadState('networkidle');
    await expectNoClientVisibleTokens(page, exposure);
  });

  await test.step('walking: exposicao final', async () => {
    await flushHeaderObservations(network);
    expect({
      authorizationHeaderAbsent: network.authorizationHeaderSeen === false,
      bffCallsObserved: network.bffRequestCount > 0,
      bffPathOutsideClientOriginAbsent:
        network.bffPathOutsideClientOriginSeen === false,
      directBackendRequestAbsent: network.directBackendRequestSeen === false,
      exactlyTwoIdentityPuts: network.identityPutCount === 2,
      headerInspectionSucceeded: network.headerInspectionFailed === false,
      identityRemainedStable: firstIdentityId === secondIdentityId,
      tokenEndpointAbsent: network.tokenEndpointSeen === false,
    }).toEqual({
      authorizationHeaderAbsent: true,
      bffCallsObserved: true,
      bffPathOutsideClientOriginAbsent: true,
      directBackendRequestAbsent: true,
      exactlyTwoIdentityPuts: true,
      headerInspectionSucceeded: true,
      identityRemainedStable: true,
      tokenEndpointAbsent: true,
    });
  });
}

async function runProductionAbsence(
  page: Page,
  network: NetworkProof,
  exposure: ExposureProof,
): Promise<void> {
  let tracerSubresourceRequested = false;
  page.on('request', (request) => {
    const requestLocation = new URL(request.url());
    if (
      request.resourceType() !== 'document' &&
      `${requestLocation.pathname}${requestLocation.search}`
        .toLowerCase()
        .includes('tracer')
    ) {
      tracerSubresourceRequested = true;
    }
  });

  await test.step('production: bootstrap da aplicacao', async () => {
    const response = await page.goto('/tracer', { waitUntil: 'networkidle' });
    const bootstrapProof = await page.evaluate(() => ({
      angularRootPresent: document.querySelectorAll('app-root').length === 1,
      routerOutletPresent: document.querySelectorAll('router-outlet').length === 1,
      safeProbeLocation:
        location.origin === 'http://localhost:4200' &&
        ['/', '/tracer'].includes(location.pathname) &&
        location.search === '' &&
        location.hash === '',
    }));

    expect({
      ...bootstrapProof,
      documentWasServed: response?.status() === 200,
    }).toEqual({
      angularRootPresent: true,
      documentWasServed: true,
      routerOutletPresent: true,
      safeProbeLocation: true,
    });
  });

  await test.step('production: DOM sem harness', async () => {
    const absenceProof = await page.evaluate(() => {
      const forbiddenCopy = [
        'Verificação de integração',
        'Verificar integração',
        'Identidade persistida',
      ];
      const bodyText = document.body?.innerText ?? '';
      const tracerLinkPresent = Array.from(
        document.querySelectorAll<HTMLElement>('a[href], [routerlink]'),
      ).some((element) => {
        const destination =
          element.getAttribute('href') ?? element.getAttribute('routerlink') ?? '';
        return destination.toLowerCase().includes('tracer');
      });

      return {
        forbiddenCopyAbsent: forbiddenCopy.every((copy) => !bodyText.includes(copy)),
        harnessElementAbsent: document.querySelector('app-tracer-page') === null,
        identityOutputAbsent: document.querySelector('main code') === null,
        tracerLinkAbsent: tracerLinkPresent === false,
      };
    });

    expect(absenceProof).toEqual({
      forbiddenCopyAbsent: true,
      harnessElementAbsent: true,
      identityOutputAbsent: true,
      tracerLinkAbsent: true,
    });
  });

  await test.step('production: rede e storage sem tracer', async () => {
    await expectNoClientVisibleTokens(page, exposure);
    await flushHeaderObservations(network);

    expect({
      bffCallAbsent: network.bffRequestCount === 0,
      directBackendRequestAbsent: network.directBackendRequestSeen === false,
      headerInspectionSucceeded: network.headerInspectionFailed === false,
      tracerSubresourceAbsent: tracerSubresourceRequested === false,
    }).toEqual({
      bffCallAbsent: true,
      directBackendRequestAbsent: true,
      headerInspectionSucceeded: true,
      tracerSubresourceAbsent: true,
    });
  });
}

const scenario = readScenario();

test(`BETUFU: ${scenario}`, async ({ page }) => {
  const exposure = await observeExposure(page);
  const network = observeNetwork(page);

  if (scenario === 'walking-skeleton') {
    await runWalkingSkeleton(page, network, exposure);
    return;
  }

  await runProductionAbsence(page, network, exposure);
});
