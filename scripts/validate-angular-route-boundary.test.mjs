import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { APPROVED_GENERATED_FILES } from '../frontend/scripts/generate-api.mjs';

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(scriptsDirectory, '..');
const validatorPath = join(scriptsDirectory, 'validate-angular-route-boundary.mjs');
const developmentRoutesPath = 'frontend/src/app/app.routes.development.ts';
const tracerComponentPath = 'frontend/src/app/features/tracer/tracer-page.component.ts';
const appRoutesPath = 'frontend/src/app/app.routes.ts';
const profileGuardPath = 'frontend/src/app/core/profile/profile-completion.guard.ts';

const CANONICAL_GUARDED_APP_ROUTES = [
  "import type { Routes } from '@angular/router';",
  '',
  "import { profileCompletionGuard } from './core/profile/profile-completion.guard';",
  "import { BUILD_SPECIFIC_ROUTES, PUBLIC_MARKET_ROUTES } from './app.routes.development';",
  "import { ArenaShellComponent } from './layout/arena-shell.component';",
  "import { ConsoleShellComponent } from './layout/console-shell.component';",
  "import { sportsOperationsRoutes } from './features/sports/operations/sports-operations.routes';",
  '',
  'export const APP_ROUTES: Routes = [',
  '  {',
  "    path: '',",
  '    component: ArenaShellComponent,',
  '    children: [',
  '      ...PUBLIC_MARKET_ROUTES,',
  '      {',
  "        path: '',",
  "        pathMatch: 'full',",
  '        loadComponent: () =>',
  "          import('./features/sports/public-agenda/public-agenda-page.component').then(",
  '            ({ PublicAgendaPageComponent }) => PublicAgendaPageComponent,',
  '          ),',
  '      },',
  '      {',
  "        path: 'competicoes/:competitionId',",
  '        loadComponent: () =>',
  "          import('./features/sports/public-competition/public-competition-page.component').then(",
  '            ({ PublicCompetitionPageComponent }) => PublicCompetitionPageComponent,',
  '          ),',
  '      },',
  '      {',
  "        path: 'eventos/:eventId',",
  '        loadComponent: () =>',
  "          import('./features/sports/public-event/public-event-page.component').then(",
  '            ({ PublicEventPageComponent }) => PublicEventPageComponent,',
  '          ),',
  '      },',
  '      {',
  "        path: 'organizacoes/:organizationId',",
  '        loadComponent: () =>',
  "          import('./features/sports/public-organization/public-organization-page.component').then(",
  '            ({ PublicOrganizationPageComponent }) => PublicOrganizationPageComponent,',
  '          ),',
  '      },',
  '      {',
  "        path: 'equipes/:teamId',",
  '        loadComponent: () =>',
  "          import('./features/sports/public-team/public-team-page.component').then(",
  '            ({ PublicTeamPageComponent }) => PublicTeamPageComponent,',
  '          ),',
  '      },',
  '      {',
  "        path: 'atletas/:athleteId',",
  '        loadComponent: () =>',
  "          import('./features/sports/public-athlete/public-athlete-page.component').then(",
  '            ({ PublicAthletePageComponent }) => PublicAthletePageComponent,',
  '          ),',
  '      },',
  '      {',
  "        path: 'chaveamentos/:bracketId',",
  '        loadComponent: () =>',
  "          import('./features/sports/public-bracket/public-bracket-page.component').then(",
  '            ({ PublicBracketPageComponent }) => PublicBracketPageComponent,',
  '          ),',
  '      },',
  '      {',
  "        path: 'classificacoes/:standingsId',",
  '        loadComponent: () =>',
  "          import('./features/sports/public-standings/public-standings-page.component').then(",
  '            ({ PublicStandingsPageComponent }) => PublicStandingsPageComponent,',
  '          ),',
  '      },',
  '    ],',
  '  },',
  '  {',
  "    path: '',",
  '    canActivateChild: [profileCompletionGuard],',
  '    children: [',
  '      ...sportsOperationsRoutes,',
  '      ...BUILD_SPECIFIC_ROUTES,',
  '      {',
  "        path: 'operacao/odds/mercados',",
  '        component: ConsoleShellComponent,',
  "        data: { consoleVariant: 'operational' },",
  '        children: [',
  '          {',
  "            path: '',",
  '            loadComponent: () =>',
  "              import('./features/odds/operations/odds-operations-page.component').then(",
  '                ({ OddsOperationsPageComponent }) => OddsOperationsPageComponent,',
  '              ),',
  '          },',
  '          {',
  "            path: ':marketId',",
  '            loadComponent: () =>',
  "              import('./features/odds/operations/odds-operations-page.component').then(",
  '                ({ OddsOperationsPageComponent }) => OddsOperationsPageComponent,',
  '              ),',
  '          },',
  '        ],',
  '      },',
  '    ],',
  '  },',
  '];',
  '',
].join('\n');

const CANONICAL_PUBLIC_MARKET_ROUTE_ENTRY = [
  '  {',
  "    path: 'odds/mercados/:marketId',",
  '    loadComponent: () =>',
  "      import('./features/odds/public-market/public-market-page.component').then(",
  '        ({ PublicMarketPageComponent }) => PublicMarketPageComponent,',
  '      ),',
  '  },',
].join('\n');

const CANONICAL_PUBLIC_MARKET_ROUTES = [
  "import type { Routes } from '@angular/router';",
  '',
  'export const PUBLIC_MARKET_ROUTES: Routes = [',
  CANONICAL_PUBLIC_MARKET_ROUTE_ENTRY,
  '];',
  '',
].join('\n');

const CANONICAL_PROFILE_GUARD = [
  "import { inject } from '@angular/core';",
  "import { CanActivateChildFn, Router } from '@angular/router';",
  "import { ProfileStore } from '../../features/profile/profile.store';",
  '',
  "const PROFILE_COMPLETION_URL = '/conta/perfil/completar';",
  "const ERROR_URL = '/error';",
  'const AUTH_TECHNICAL_ROUTE_PREFIXES = Object.freeze([',
  "  '/oauth2/authorization/',",
  "  '/login/oauth2/code/',",
  ']);',
  '',
  'function routePath(url: string): string {',
  "  const fragmentBoundary = url.indexOf('#');",
  '  const withoutFragment = fragmentBoundary === -1 ? url : url.slice(0, fragmentBoundary);',
  "  const queryBoundary = withoutFragment.indexOf('?');",
  '  return queryBoundary === -1 ? withoutFragment : withoutFragment.slice(0, queryBoundary);',
  '}',
  '',
  'function isExemptRoute(url: string): boolean {',
  '  const path = routePath(url);',
  '  return path === PROFILE_COMPLETION_URL',
  '    || path === ERROR_URL',
  '    || AUTH_TECHNICAL_ROUTE_PREFIXES.some((prefix) => path.startsWith(prefix));',
  '}',
  '',
  'export const profileCompletionGuard: CanActivateChildFn = async (_route, state) => {',
  '  if (isExemptRoute(state.url)) {',
  '    return true;',
  '  }',
  '',
  '  const profileStore = inject(ProfileStore);',
  '  const router = inject(Router);',
  '',
  "  if (profileStore.status() === 'initial') {",
  '    await profileStore.loadProfile();',
  '  }',
  '',
  '  return profileStore.hasProfile()',
  '    ? true',
  '    : router.createUrlTree([PROFILE_COMPLETION_URL]);',
  '};',
  '',
].join('\n');

const CANONICAL_TRACER_ROUTE_ENTRY = [
  '  {',
  "    path: 'tracer',",
  '    loadComponent: () =>',
  "      import('./features/tracer/tracer-page.component').then(",
  '        ({ TracerPageComponent }) => TracerPageComponent,',
  '      ),',
  '  },',
].join('\n');

const CANONICAL_TRACER_ROUTES = [
  "import type { Routes } from '@angular/router';",
  '',
  'export const BUILD_SPECIFIC_ROUTES: Routes = [',
  CANONICAL_TRACER_ROUTE_ENTRY,
  '];',
  '',
].join('\n');

const CANONICAL_DIAGNOSTIC_ROUTE_ENTRY = [
  '  {',
  "    path: 'diagnostic',",
  '    loadComponent: () =>',
  "      import('./features/diagnostic/diagnostic-page.component').then(",
  '        ({ DiagnosticPageComponent }) => DiagnosticPageComponent,',
  '      ),',
  '  },',
].join('\n');

const CANONICAL_PROFILE_ROUTE_ENTRY = [
  '  {',
  "    path: 'conta/perfil/completar',",
  '    loadComponent: () =>',
  "      import('./features/profile/profile-gate-page.component').then(",
  '        ({ ProfileGatePageComponent }) => ProfileGatePageComponent,',
  '      ),',
  '  },',
].join('\n');

const CANONICAL_OPERATIONS_ROUTE_ENTRY = [
  '  {',
  "    path: 'operacao/painel',",
  '    loadComponent: () =>',
  "      import('./features/operations/operations-panel-page.component').then(",
  '        ({ OperationsPanelPageComponent }) => OperationsPanelPageComponent,',
  '      ),',
  '  },',
].join('\n');

const CANONICAL_CONSOLE_CATALOG_ROOT_ROUTE_ENTRY = [
  '      {',
  "        path: '',",
  "        pathMatch: 'full',",
  '        loadComponent: () =>',
  "          import('./features/sports/operations/catalog/sports-catalog-page.component').then(",
  '            ({ SportsCatalogPageComponent }) => SportsCatalogPageComponent,',
  '          ),',
  '      },',
].join('\n');

const CANONICAL_CONSOLE_CATALOG_ROUTE_ENTRY = [
  '      {',
  "        path: 'catalogo',",
  '        loadComponent: () =>',
  "          import('./features/sports/operations/catalog/sports-catalog-page.component').then(",
  '            ({ SportsCatalogPageComponent }) => SportsCatalogPageComponent,',
  '          ),',
  '      },',
].join('\n');

const CANONICAL_CONSOLE_ORGANIZATIONS_ROUTE_ENTRY = [
  '      {',
  "        path: 'organizacoes',",
  '        loadComponent: () =>',
  "          import('./features/sports/operations/organizations/sports-organizations-page.component').then(",
  '            ({ SportsOrganizationsPageComponent }) => SportsOrganizationsPageComponent,',
  '          ),',
  '      },',
].join('\n');

const CANONICAL_CONSOLE_TABULAR_IMPORT_ROUTE_ENTRY = [
  '      {',
  "        path: 'importacao',",
  '        loadComponent: () =>',
  "          import('./features/sports/operations/tabular-import/sports-tabular-import-page.component').then(",
  '            ({ SportsTabularImportPageComponent }) => SportsTabularImportPageComponent,',
  '          ),',
  '      },',
].join('\n');

function canonicalAuthenticatedRoutes(routeEntries) {
  return [
    "import type { Routes } from '@angular/router';",
    "import { AuthenticatedLayoutComponent } from './layout/authenticated-layout.component';",
    "import { ConsoleShellComponent } from './layout/console-shell.component';",
    '',
    'export const PUBLIC_MARKET_ROUTES: Routes = [',
    CANONICAL_PUBLIC_MARKET_ROUTE_ENTRY,
    '];',
    '',
    'export const BUILD_SPECIFIC_ROUTES: Routes = [',
    '  {',
    "    path: 'operacao/esportes',",
    '    component: ConsoleShellComponent,',
    '    children: [',
    CANONICAL_CONSOLE_CATALOG_ROOT_ROUTE_ENTRY,
    CANONICAL_CONSOLE_CATALOG_ROUTE_ENTRY,
    CANONICAL_CONSOLE_ORGANIZATIONS_ROUTE_ENTRY,
    CANONICAL_CONSOLE_TABULAR_IMPORT_ROUTE_ENTRY,
    '    ],',
    '  },',
    '  {',
    "    path: '',",
    '    component: AuthenticatedLayoutComponent,',
    '    children: [',
    ...routeEntries,
    '    ],',
    '  },',
    '];',
    '',
  ].join('\n');
}

const CANONICAL_PRODUCTION_ROUTES = canonicalAuthenticatedRoutes([
  CANONICAL_PROFILE_ROUTE_ENTRY,
  CANONICAL_OPERATIONS_ROUTE_ENTRY,
]);

const CANONICAL_DEVELOPMENT_ROUTES = canonicalAuthenticatedRoutes([
  CANONICAL_TRACER_ROUTE_ENTRY,
  CANONICAL_DIAGNOSTIC_ROUTE_ENTRY,
  CANONICAL_PROFILE_ROUTE_ENTRY,
  CANONICAL_OPERATIONS_ROUTE_ENTRY,
]);

const EAGER_TRACER_ROUTES = [
  "import type { Routes } from '@angular/router';",
  "import { TracerPageComponent } from './features/tracer/tracer-page.component';",
  '',
  'export const BUILD_SPECIFIC_ROUTES: Routes = [',
  "  { path: 'tracer', component: TracerPageComponent },",
  '];',
  '',
].join('\n');

const generatedFixturePaths = APPROVED_GENERATED_FILES.map(
  (relativePath) => `frontend/src/app/api/generated/${relativePath}`,
);

const FIXTURE_PATHS = Object.freeze([
  'frontend/angular.json',
  'frontend/src/main.ts',
  'frontend/src/app/app.config.ts',
  'frontend/src/app/app.routes.ts',
  profileGuardPath,
  'frontend/src/app/app.routes.development.ts',
  'frontend/src/app/app.routes.production.ts',
  'frontend/src/app/app.component.ts',
  'frontend/src/app/app.component.html',
  tracerComponentPath,
  'frontend/src/app/features/tracer/tracer-page.component.html',
  ...generatedFixturePaths,
]);

const canonicalFiles = new Map(
  await Promise.all(
    FIXTURE_PATHS.map(async (relativePath) => {
      if (relativePath === appRoutesPath) {
        return [relativePath, CANONICAL_GUARDED_APP_ROUTES];
      }
      if (relativePath === profileGuardPath) {
        return [relativePath, CANONICAL_PROFILE_GUARD];
      }
      return [
        relativePath,
        (await readFile(join(projectRoot, relativePath), 'utf8')).replaceAll('\r\n', '\n'),
      ];
    }),
  ),
);

function runValidator(fixtureRoot) {
  const argumentsList = [validatorPath];
  if (fixtureRoot !== undefined) {
    argumentsList.push('--root', fixtureRoot);
  }

  const result = spawnSync(process.execPath, argumentsList, {
    cwd: projectRoot,
    encoding: 'utf8',
    shell: false,
    timeout: 10_000,
    windowsHide: true,
  });

  assert.ifError(result.error);
  return result;
}

function runValidatorWithArguments(argumentsList) {
  const result = spawnSync(process.execPath, [validatorPath, ...argumentsList], {
    cwd: projectRoot,
    encoding: 'utf8',
    shell: false,
    timeout: 10_000,
    windowsHide: true,
  });

  assert.ifError(result.error);
  return result;
}

async function populateFixture(fixtureRoot) {
  for (const [relativePath, contents] of canonicalFiles) {
    const fixturePath = join(fixtureRoot, relativePath);
    await mkdir(dirname(fixturePath), { recursive: true });
    await writeFile(fixturePath, contents, 'utf8');
  }
}

async function withFixture(label, callback) {
  const safeLabel = label.replaceAll(/[^a-z0-9-]/giu, '-').toLowerCase();
  const fixtureRoot = await mkdtemp(
    join(tmpdir(), `betufu-angular-route-boundary-${safeLabel}-`),
  );

  try {
    await populateFixture(fixtureRoot);
    return await callback(fixtureRoot);
  } finally {
    await rm(fixtureRoot, { recursive: true, force: true });
  }
}

async function mutateText(fixtureRoot, relativePath, mutation) {
  const fixturePath = join(fixtureRoot, relativePath);
  const original = await readFile(fixturePath, 'utf8');
  const mutated = mutation(original);
  assert.notEqual(mutated, original, `A mutacao de ${relativePath} nao alterou a fixture.`);
  await writeFile(fixturePath, mutated, 'utf8');
}

async function overwriteText(fixtureRoot, relativePath, contents) {
  await writeFile(join(fixtureRoot, relativePath), contents, 'utf8');
}

async function mutateAngularJson(fixtureRoot, mutation) {
  const fixturePath = join(fixtureRoot, 'frontend/angular.json');
  const document = JSON.parse(await readFile(fixturePath, 'utf8'));
  mutation(document.projects.betufu.architect.build.configurations);
  await writeFile(fixturePath, `${JSON.stringify(document, null, 2)}\n`, 'utf8');
}

function expectValidationFailure(result, rule) {
  assert.equal(result.status, 1, result.stdout);
  assert.equal(result.stdout, '');
  assert.match(
    result.stderr,
    new RegExp(`^\\[validate-angular-route-boundary\\] ERRO: ${rule}:`, 'u'),
  );
}

test('aceita uma fixture canonica isolada', async () => {
  await withFixture('canonical', async (fixtureRoot) => {
    const result = runValidator(fixtureRoot);

    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /\[validate-angular-route-boundary\] OK:/u);
  });
});

test('recusa modulo publico divergente na Arena', async () => {
  await withFixture('public-arena-module-outside-allowlist', async (fixtureRoot) => {
    await mutateText(
      fixtureRoot,
      appRoutesPath,
      (source) => source.replace(
        './features/sports/public-agenda/public-agenda-page.component',
        './features/sports/public-agenda/other-page.component',
      ),
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'DYNAMIC_IMPORT_FORBIDDEN');
  });
});

test('recusa rota fora da allowlist no Console esportivo', async () => {
  await withFixture('console-route-outside-allowlist', async (fixtureRoot) => {
    await mutateText(
      fixtureRoot,
      developmentRoutesPath,
      (source) => source.replace("path: 'organizacoes'", "path: 'nao-aprovada'"),
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'DEVELOPMENT_CONSOLE_ROUTES');
  });
});

test('recusa rota operacional de odds fora da allowlist na seam', async () => {
  await withFixture('odds-operation-route-outside-allowlist', async (fixtureRoot) => {
    await mutateText(
      fixtureRoot,
      appRoutesPath,
      (source) => source.replace(
        "path: 'operacao/odds/mercados'",
        "path: 'operacao/odds/nao-aprovada'",
      ),
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'APP_ODDS_OPERATION_ROUTES');
  });
});

test('recusa import nao literal na rota operacional de odds', async () => {
  await withFixture('odds-operation-route-non-literal-import', async (fixtureRoot) => {
    await mutateText(
      fixtureRoot,
      appRoutesPath,
      (source) => source.replace(
        "import('./features/odds/operations/odds-operations-page.component')",
        "import(`./features/odds/operations/odds-operations-page.component`)",
      ),
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'DYNAMIC_IMPORT_FORBIDDEN');
  });
});

const globalGuardMutations = [
  {
    name: 'seam global sem a guarda de perfil',
    path: appRoutesPath,
    rule: 'PROFILE_GUARD_COMPOSITION',
    mutate: (source) => source.replace('    canActivateChild: [profileCompletionGuard],\n', ''),
  },
  {
    name: 'simbolo divergente da guarda global',
    path: appRoutesPath,
    rule: 'PROFILE_GUARD_IMPORT',
    mutate: (source) => source.replaceAll('profileCompletionGuard', 'otherGuard'),
  },
  {
    name: 'excecao de S1 removida criando loop',
    path: profileGuardPath,
    rule: 'PROFILE_GUARD_EXEMPTION',
    mutate: (source) => source.replace(
      '  return path === PROFILE_COMPLETION_URL\n',
      '  return false\n',
    ),
  },
  {
    name: 'header Authorization manual no arquivo da guarda',
    path: profileGuardPath,
    rule: 'AUTHORIZATION_HEADER',
    mutate: (source) => `${source}\nconst manualHeader = 'Authorization';\n`,
  },
  {
    name: 'propriedade extra na raiz guardada',
    path: appRoutesPath,
    rule: 'PROFILE_GUARD_COMPOSITION',
    mutate: (source) => source.replace(
      "    path: '',\n",
      "    path: '',\n    data: { leaked: true },\n",
    ),
  },
  {
    name: 'rota irma fora da composicao guardada',
    path: appRoutesPath,
    rule: 'PROFILE_GUARD_COMPOSITION',
    mutate: (source) => source.replace(
      '  },\n];',
      "  },\n  { path: 'unguarded', redirectTo: '' },\n];",
    ),
  },
];

for (const mutation of globalGuardMutations) {
  test(`recusa ${mutation.name}`, async () => {
    await withFixture(mutation.name, async (fixtureRoot) => {
      await mutateText(fixtureRoot, mutation.path, mutation.mutate);

      const result = runValidator(fixtureRoot);

      expectValidationFailure(result, mutation.rule);
    });
  });
}

test('aceita password somente como segmento de rota relativa gerada', async () => {
  await withFixture('password-reset-relative-path', async (fixtureRoot) => {
    const result = runValidator(fixtureRoot);

    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /\[validate-angular-route-boundary\] OK:/u);
  });
});

test('recusa development sem o subconjunto de producao', async () => {
  await withFixture('explicit-empty-development-routes', async (fixtureRoot) => {
    await overwriteText(
      fixtureRoot,
      'frontend/src/app/app.routes.production.ts',
      CANONICAL_PRODUCTION_ROUTES,
    );
    await overwriteText(
      fixtureRoot,
      developmentRoutesPath,
      canonicalAuthenticatedRoutes([]),
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'DEVELOPMENT_ROUTES_ALLOWLIST');
  });
});

test('aceita o subconjunto de producao mais as rotas tecnicas em development', async () => {
  await withFixture('canonical-tracer-route', async (fixtureRoot) => {
    await overwriteText(
      fixtureRoot,
      'frontend/src/app/app.routes.production.ts',
      CANONICAL_PRODUCTION_ROUTES,
    );
    await overwriteText(
      fixtureRoot,
      developmentRoutesPath,
      CANONICAL_DEVELOPMENT_ROUTES,
    );

    const result = runValidator(fixtureRoot);

    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /\[validate-angular-route-boundary\] OK:/u);
  });
});

test('recusa rota de producao fora do conjunto de produto aprovado', async () => {
  await withFixture('production-route-outside-allowlist', async (fixtureRoot) => {
    await overwriteText(
      fixtureRoot,
      'frontend/src/app/app.routes.production.ts',
      CANONICAL_PRODUCTION_ROUTES.replace(
        "path: 'conta/perfil/completar'",
        "path: 'produto/nao-aprovado'",
      ),
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'PRODUCTION_ROUTES_ALLOWLIST');
  });
});

test('recusa o simbolo de rotas de producao exportado duas vezes', async () => {
  await withFixture('duplicate-production-routes-export', async (fixtureRoot) => {
    await overwriteText(
      fixtureRoot,
      'frontend/src/app/app.routes.production.ts',
      `${CANONICAL_PRODUCTION_ROUTES}\nexport const BUILD_SPECIFIC_ROUTES: Routes = [];\n`,
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'PRODUCTION_ROUTES_UNIQUE');
  });
});

test('recusa rotas de producao sem o layout autenticado como pai', async () => {
  await withFixture('production-routes-without-authenticated-layout', async (fixtureRoot) => {
    await overwriteText(
      fixtureRoot,
      'frontend/src/app/app.routes.production.ts',
      [
        "import type { Routes } from '@angular/router';",
        '',
        'export const BUILD_SPECIFIC_ROUTES: Routes = [',
        CANONICAL_PROFILE_ROUTE_ENTRY,
        '];',
        '',
      ].join('\n'),
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'PRODUCTION_AUTHENTICATED_LAYOUT');
  });
});

test('recusa rota de development fora do conjunto permitido', async () => {
  await withFixture('development-route-outside-allowlist', async (fixtureRoot) => {
    await overwriteText(
      fixtureRoot,
      'frontend/src/app/app.routes.production.ts',
      CANONICAL_PRODUCTION_ROUTES,
    );
    await overwriteText(
      fixtureRoot,
      developmentRoutesPath,
      CANONICAL_DEVELOPMENT_ROUTES.replace(
        "path: 'diagnostic'",
        "path: 'tecnico/nao-aprovado'",
      ),
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'DEVELOPMENT_ROUTES_ALLOWLIST');
  });
});

test('aceita o componente tracer usando somente IdentityService gerado', async () => {
  await withFixture('generated-identity-service-only', async (fixtureRoot) => {
    const result = runValidator(fixtureRoot);

    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /\[validate-angular-route-boundary\] OK:/u);
  });
});

test('aceita file replacements nao relacionados em development e homolog', async () => {
  await withFixture('unrelated-replacements', async (fixtureRoot) => {
    await mutateAngularJson(fixtureRoot, (configurations) => {
      const unrelatedReplacement = {
        replace: 'src/environments/runtime.ts',
        with: 'src/environments/runtime.local.ts',
      };
      configurations.development.fileReplacements = [unrelatedReplacement];
      configurations.homolog.fileReplacements = [unrelatedReplacement];
    });

    const result = runValidator(fixtureRoot);

    assert.equal(result.status, 0, result.stderr);
  });
});

test('retorna codigo 2 para argumentos invalidos', () => {
  const result = runValidatorWithArguments(['--unexpected']);

  assert.equal(result.status, 2, result.stdout);
  assert.match(result.stderr, /^Uso: node scripts\/validate-angular-route-boundary\.mjs/u);
});

test('nao expoe conteudo de angular.json malformado', async () => {
  await withFixture('malformed-json', async (fixtureRoot) => {
    const fixturePath = join(fixtureRoot, 'frontend/angular.json');
    await writeFile(fixturePath, '{"secret":SENSITIVE_CANARY_VALUE}\n', 'utf8');

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'ANGULAR_JSON');
    assert.doesNotMatch(result.stderr, /SENSITIVE_CANARY_VALUE/u);
    assert.doesNotMatch(result.stderr, new RegExp(fixtureRoot.replaceAll('\\', '\\\\'), 'u'));
  });
});

const replacementMutations = [
  {
    name: 'replacement production ausente',
    rule: 'PRODUCTION_ROUTE_REPLACEMENT',
    mutate: (configurations) => {
      delete configurations.production.fileReplacements;
    },
  },
  {
    name: 'replacement production invertido',
    rule: 'PRODUCTION_ROUTE_REPLACEMENT',
    mutate: (configurations) => {
      configurations.production.fileReplacements = [
        {
          replace: 'src/app/app.routes.production.ts',
          with: 'src/app/app.routes.development.ts',
        },
      ];
    },
  },
  {
    name: 'replacement indevido em homolog',
    rule: 'HOMOLOG_ROUTE_REPLACEMENT',
    mutate: (configurations) => {
      configurations.homolog.fileReplacements = [
        {
          replace: 'src/app/app.routes.development.ts',
          with: 'src/app/app.routes.production.ts',
        },
      ];
    },
  },
  {
    name: 'replacement indevido em development',
    rule: 'DEVELOPMENT_ROUTE_REPLACEMENT',
    mutate: (configurations) => {
      configurations.development.fileReplacements = [
        {
          replace: 'src/app/app.routes.development.ts',
          with: 'src/app/app.routes.production.ts',
        },
      ];
    },
  },
  {
    name: 'replacement alias com ponto em homolog',
    rule: 'HOMOLOG_ROUTE_REPLACEMENT',
    mutate: (configurations) => {
      configurations.homolog.fileReplacements = [
        {
          replace: 'src/app/./app.routes.development.ts',
          with: 'src/app/app.routes.production.ts',
        },
      ];
    },
  },
  {
    name: 'replacement alias com barras Windows em development',
    rule: 'DEVELOPMENT_ROUTE_REPLACEMENT',
    mutate: (configurations) => {
      configurations.development.fileReplacements = [
        {
          replace: 'src\\app\\app.routes.development.ts',
          with: 'src\\app\\app.routes.production.ts',
        },
      ];
    },
  },
];

for (const replacementMutation of replacementMutations) {
  test(`recusa ${replacementMutation.name}`, async () => {
    await withFixture(replacementMutation.name, async (fixtureRoot) => {
      await mutateAngularJson(fixtureRoot, replacementMutation.mutate);

      const result = runValidator(fixtureRoot);

      expectValidationFailure(result, replacementMutation.rule);
    });
  });
}

const ROUTER_PROVIDER_ENTRY = '    provideRouter(APP_ROUTES),\n';
const HTTP_PROVIDER_ENTRY = `    provideHttpClient(
      withXsrfConfiguration({
        cookieName: 'XSRF-TOKEN',
        headerName: 'X-XSRF-TOKEN',
      }),
    ),
`;
const API_PROVIDER_ENTRY = "    provideApi(''),\n";

function replaceExactlyOnceLiteral(source, searchValue, replacement, label) {
  const firstIndex = source.indexOf(searchValue);
  assert.notEqual(firstIndex, -1, `${label} nao foi encontrado na fixture.`);
  assert.equal(
    source.indexOf(searchValue, firstIndex + searchValue.length),
    -1,
    `${label} apareceu mais de uma vez na fixture.`,
  );

  return `${source.slice(0, firstIndex)}${replacement}${source.slice(firstIndex + searchValue.length)}`;
}

const tracerRouteMutations = [
  {
    name: 'componente tracer por import eager',
    rule: 'DEVELOPMENT_ROUTES_SHAPE',
    mutate: () => EAGER_TRACER_ROUTES,
  },
  {
    name: 'path divergente na rota tracer',
    rule: 'DEVELOPMENT_ROUTES_ALLOWLIST',
    mutate: (source) => source.replace("path: 'tracer'", "path: 'other'"),
  },
  {
    name: 'modulo divergente na lazy route tracer',
    rule: 'DYNAMIC_IMPORT_FORBIDDEN',
    mutate: (source) => source.replace(
      './features/tracer/tracer-page.component',
      './features/tracer/other-page.component',
    ),
  },
  {
    name: 'export divergente na lazy route tracer',
    rule: 'DEVELOPMENT_ROUTES_ALLOWLIST',
    mutate: (source) => source.replaceAll(
      'TracerPageComponent',
      'OtherPageComponent',
    ),
  },
  {
    name: 'retorno divergente na lazy route tracer',
    rule: 'DEVELOPMENT_ROUTES_SHAPE',
    mutate: (source) => source.replace(
      '=> TracerPageComponent,',
      '=> OtherPageComponent,',
    ),
  },
  {
    name: 'lazy route tracer duplicada',
    rule: 'DYNAMIC_IMPORT_FORBIDDEN',
    mutate: (source) => source.replace(
      CANONICAL_TRACER_ROUTE_ENTRY,
      `${CANONICAL_TRACER_ROUTE_ENTRY}\n${CANONICAL_TRACER_ROUTE_ENTRY}`,
    ),
  },
  {
    name: 'segunda rota development',
    rule: 'DEVELOPMENT_ROUTES_SHAPE',
    mutate: (source) => source.replace(
      /\];\s*$/u,
      "  { path: 'other', redirectTo: '' },\n];\n",
    ),
  },
  {
    name: 'specifier template na lazy route tracer',
    rule: 'DYNAMIC_IMPORT_FORBIDDEN',
    mutate: (source) => source.replace(
      "import('./features/tracer/tracer-page.component')",
      'import(`./features/tracer/tracer-page.component`)',
    ),
  },
  {
    name: 'specifier concatenado na lazy route tracer',
    rule: 'DYNAMIC_IMPORT_FORBIDDEN',
    mutate: (source) => source.replace(
      "import('./features/tracer/tracer-page.component')",
      "import('./features/tracer/' + 'tracer-page.component')",
    ),
  },
  {
    name: 'import direto de production dentro de development',
    rule: 'DIRECT_PRODUCTION_IMPORT',
    mutate: (source) => [
      "import { BUILD_SPECIFIC_ROUTES as PRODUCTION_ROUTES } from './app.routes.production';",
      source,
    ].join('\n'),
  },
  {
    name: 'import dinamico de production dentro de development',
    rule: 'DYNAMIC_IMPORT_FORBIDDEN',
    mutate: (source) => source.replace(
      "import('./features/tracer/tracer-page.component')",
      "import('./app.routes.production')",
    ),
  },
];

const CANONICAL_LAYOUT_TRACER_ROUTES = canonicalAuthenticatedRoutes([
  CANONICAL_TRACER_ROUTE_ENTRY,
  CANONICAL_PROFILE_ROUTE_ENTRY,
]);

const layoutRouteMutations = [
  {
    name: 'componente de layout autenticado trocado',
    rule: 'DEVELOPMENT_ROUTES_SHAPE',
    mutate: (source) => source.replaceAll('AuthenticatedLayoutComponent', 'OtherLayoutComponent'),
  },
  {
    name: 'path pai autenticado diferente de vazio',
    rule: 'DEVELOPMENT_ROUTES_SHAPE',
    mutate: (source) => source.replace(
      "    path: '',\n    component: AuthenticatedLayoutComponent,",
      "    path: 'auth',\n    component: AuthenticatedLayoutComponent,",
    ),
  },
  {
    name: 'rota filha extra dentro de layout autenticado',
    rule: 'DEVELOPMENT_ROUTES_SHAPE',
    mutate: (source) => source.replace(
      '    component: AuthenticatedLayoutComponent,\n    children: [',
      "    component: AuthenticatedLayoutComponent,\n    children: [\n      { path: 'leak', loadComponent: () => null },",
    ),
  },
];

for (const tracerRouteMutation of tracerRouteMutations) {
  test(`recusa ${tracerRouteMutation.name}`, async () => {
    await withFixture(tracerRouteMutation.name, async (fixtureRoot) => {
      await overwriteText(
        fixtureRoot,
        developmentRoutesPath,
        tracerRouteMutation.mutate(CANONICAL_DEVELOPMENT_ROUTES),
      );

      const result = runValidator(fixtureRoot);

      expectValidationFailure(result, tracerRouteMutation.rule);
    });
  });
}

for (const layoutRouteMutation of layoutRouteMutations) {
  test(`recusa ${layoutRouteMutation.name}`, async () => {
    await withFixture(layoutRouteMutation.name, async (fixtureRoot) => {
      await overwriteText(
        fixtureRoot,
        developmentRoutesPath,
        layoutRouteMutation.mutate(CANONICAL_LAYOUT_TRACER_ROUTES),
      );

      const result = runValidator(fixtureRoot);

      expectValidationFailure(result, layoutRouteMutation.rule);
    });
  });
}

test('recusa import eager do tracer em production', async () => {
  await withFixture('production-tracer-import', async (fixtureRoot) => {
    await mutateText(
      fixtureRoot,
      'frontend/src/app/app.routes.production.ts',
      (source) => [
        "import { TracerPageComponent } from './features/tracer/tracer-page.component';",
        source,
        'void TracerPageComponent;',
        '',
      ].join('\n'),
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'TRACER_EAGER_REFERENCE');
  });
});

test('recusa import eager do tracer em app.config', async () => {
  await withFixture('app-config-tracer-import', async (fixtureRoot) => {
    await mutateText(
      fixtureRoot,
      'frontend/src/app/app.config.ts',
      (source) => [
        source,
        "import { TracerPageComponent } from './features/tracer/tracer-page.component';",
        'void TracerPageComponent;',
        '',
      ].join('\n'),
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'TRACER_EAGER_REFERENCE');
  });
});

test('recusa import eager do tracer em qualquer novo modulo runtime', async () => {
  await withFixture('additional-runtime-tracer-import', async (fixtureRoot) => {
    const extraRuntimePath = join(
      fixtureRoot,
      'frontend/src/app/eager-tracer-consumer.ts',
    );
    await writeFile(
      extraRuntimePath,
      "import { TracerPageComponent } from './features/tracer/tracer-page.component';\nvoid TracerPageComponent;\n",
      'utf8',
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'TRACER_EAGER_REFERENCE');
  });
});

test('recusa import eager ofuscado por escape em novo modulo runtime', async () => {
  await withFixture('escaped-runtime-tracer-import', async (fixtureRoot) => {
    const extraRuntimePath = join(
      fixtureRoot,
      'frontend/src/app/escaped-consumer.ts',
    );
    await writeFile(
      extraRuntimePath,
      String.raw`import { TracerPageComponent } from './features/tr\u0061cer/tracer-page.component';`
        + '\nvoid TracerPageComponent;\n',
      'utf8',
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'TRACER_EAGER_REFERENCE');
  });
});

test('recusa import dinamico concatenado em novo modulo runtime', async () => {
  await withFixture('concatenated-runtime-dynamic-import', async (fixtureRoot) => {
    const extraRuntimePath = join(
      fixtureRoot,
      'frontend/src/app/dynamic-consumer.ts',
    );
    await writeFile(
      extraRuntimePath,
      "void import('./features/tra' + 'cer/tracer-page.component');\n",
      'utf8',
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'DYNAMIC_IMPORT_FORBIDDEN');
  });
});

test('ignora referencia ao tracer presente somente em comentario runtime', async () => {
  await withFixture('runtime-tracer-comment', async (fixtureRoot) => {
    const extraRuntimePath = join(
      fixtureRoot,
      'frontend/src/app/runtime-comment.ts',
    );
    await writeFile(
      extraRuntimePath,
      "// import { TracerPageComponent } from './features/tracer/tracer-page.component';\n",
      'utf8',
    );

    const result = runValidator(fixtureRoot);

    assert.equal(result.status, 0, result.stderr);
  });
});

test('recusa qualquer import estatico adicional em production', async () => {
  await withFixture('production-extra-static-import', async (fixtureRoot) => {
    await mutateText(
      fixtureRoot,
      'frontend/src/app/app.routes.production.ts',
      (source) => [
        "import { OtherPageComponent } from './features/other-page.component';",
        source,
        'void OtherPageComponent;',
        '',
      ].join('\n'),
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'PRODUCTION_AUTHENTICATED_LAYOUT');
  });
});

test('recusa a lazy route canonica do tracer em production', async () => {
  await withFixture('production-lazy-tracer-route', async (fixtureRoot) => {
    await overwriteText(
      fixtureRoot,
      'frontend/src/app/app.routes.production.ts',
      CANONICAL_TRACER_ROUTES,
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'DYNAMIC_IMPORT_FORBIDDEN');
  });
});

test('recusa import dinamico canonico do tracer fora de development', async () => {
  await withFixture('tracer-import-outside-development', async (fixtureRoot) => {
    await mutateText(
      fixtureRoot,
      'frontend/src/app/app.component.ts',
      (source) => `${source}\nvoid import('./features/tracer/tracer-page.component');\n`,
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'DYNAMIC_IMPORT_FORBIDDEN');
  });
});

test('nao expoe specifier divergente da lazy route tracer', async () => {
  await withFixture('tracer-specifier-sanitized', async (fixtureRoot) => {
    const sensitiveSpecifier = 'SENSITIVE_TRACER_MODULE_CANARY';
    await overwriteText(
      fixtureRoot,
      developmentRoutesPath,
      CANONICAL_TRACER_ROUTES.replace(
        './features/tracer/tracer-page.component',
        `./features/tracer/${sensitiveSpecifier}`,
      ),
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'DYNAMIC_IMPORT_FORBIDDEN');
    assert.doesNotMatch(result.stderr, new RegExp(sensitiveSpecifier, 'u'));
    assert.doesNotMatch(
      result.stderr,
      new RegExp(fixtureRoot.replaceAll('\\', '\\\\'), 'u'),
    );
  });
});

test("aceita provideApi('') como quinto provider direto", async () => {
  await withFixture('api-provider', async (fixtureRoot) => {
    const result = runValidator(fixtureRoot);

    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /\[validate-angular-route-boundary\] OK:/u);
  });
});

const tracerComponentMutations = [
  {
    name: 'fetch direto no componente tracer',
    rule: 'TRACER_FETCH_FORBIDDEN',
    mutate: (source) => `${source}\nvoid fetch('/api/v1/tracer/identity/current');\n`,
  },
  {
    name: 'HttpClient direto no componente tracer',
    rule: 'TRACER_HTTP_CLIENT_FORBIDDEN',
    mutate: (source) => `${source}\nconst directClient = inject(HttpClient);\n`,
  },
  {
    name: 'import direto de angular common http no componente tracer',
    rule: 'TRACER_HTTP_CLIENT_FORBIDDEN',
    mutate: (source) => `${source}\nimport { HttpClient } from '@angular/common/http';\n`,
  },
  {
    name: 'Authorization manual no componente tracer',
    rule: 'AUTHORIZATION_HEADER',
    mutate: (source) => `${source}\nconst manualHeader = 'Authorization';\n`,
  },
  {
    name: 'Bearer manual no componente tracer',
    rule: 'BEARER_TOKEN',
    mutate: (source) => `${source}\nconst manualScheme = 'Bearer';\n`,
  },
  {
    name: 'localStorage no componente tracer',
    rule: 'LOCAL_STORAGE',
    mutate: (source) => `${source}\nlocalStorage.setItem('identity', 'value');\n`,
  },
  {
    name: 'sessionStorage no componente tracer',
    rule: 'SESSION_STORAGE',
    mutate: (source) => `${source}\nsessionStorage.setItem('identity', 'value');\n`,
  },
  {
    name: 'indexedDB no componente tracer',
    rule: 'INDEXED_DB',
    mutate: (source) => `${source}\nvoid indexedDB.open('identity');\n`,
  },
  {
    name: 'withCredentials no componente tracer',
    rule: 'WITH_CREDENTIALS',
    mutate: (source) => `${source}\nconst requestOptions = { withCredentials: true };\n`,
  },
  {
    name: 'Cookie manual no componente tracer',
    rule: 'MANUAL_COOKIE',
    mutate: (source) => `${source}\nconst manualCookie = document.cookie;\n`,
  },
  {
    name: 'accessToken manual no componente tracer',
    rule: 'ACCESS_TOKEN',
    mutate: (source) => `${source}\nconst accessToken = 'manual';\n`,
  },
  {
    name: 'XSRF manual no componente tracer',
    rule: 'TRACER_XSRF_MANUAL_FORBIDDEN',
    mutate: (source) => `${source}\nconst xsrfHeader = 'X-XSRF-TOKEN';\n`,
  },
  {
    name: 'retry automatico no componente tracer',
    rule: 'TRACER_RETRY_FORBIDDEN',
    mutate: (source) => `${source}\nconst retryPolicy = retry(1);\n`,
  },
  {
    name: 'endpoint relativo manual no componente tracer',
    rule: 'TRACER_ENDPOINT_MANUAL_FORBIDDEN',
    mutate: (source) => `${source}\nconst manualEndpoint = '/api/v1/tracer/identity/current';\n`,
  },
  {
    name: 'endpoint absoluto manual no componente tracer',
    rule: 'ABSOLUTE_HTTP_URL',
    mutate: (source) => `${source}\nconst manualEndpoint = 'https://api.example.invalid';\n`,
  },
  {
    name: 'endpoint manual no template tracer',
    path: 'frontend/src/app/features/tracer/tracer-page.component.html',
    rule: 'TRACER_ENDPOINT_MANUAL_FORBIDDEN',
    mutate: (source) => `${source}\n<a href="/api/v1/tracer/identity/current">manual</a>\n`,
  },
];

for (const tracerComponentMutation of tracerComponentMutations) {
  test(`recusa ${tracerComponentMutation.name}`, async () => {
    await withFixture(tracerComponentMutation.name, async (fixtureRoot) => {
      await mutateText(
        fixtureRoot,
        tracerComponentMutation.path ?? tracerComponentPath,
        tracerComponentMutation.mutate,
      );

      const result = runValidator(fixtureRoot);

      expectValidationFailure(result, tracerComponentMutation.rule);
    });
  });
}

const sourceMutations = [
  {
    name: 'import de provideApi divergente',
    path: 'frontend/src/app/app.config.ts',
    rule: 'API_IMPORT',
    mutate: (source) => source.replace(
      "from './api/generated/provide-api'",
      "from './api/other'",
    ),
  },
  {
    name: 'barrel gerado retendo servico no bundle production',
    path: 'frontend/src/app/app.config.ts',
    rule: 'API_IMPORT',
    mutate: (source) => source.replace(
      "from './api/generated/provide-api'",
      "from './api/generated'",
    ),
  },
  {
    name: 'provideApi ausente',
    path: 'frontend/src/app/app.config.ts',
    rule: 'API_PROVIDER',
    mutate: (source) => replaceExactlyOnceLiteral(
      source,
      API_PROVIDER_ENTRY,
      '',
      'provider API',
    ),
  },
  {
    name: 'provideApi fora de appConfig.providers',
    path: 'frontend/src/app/app.config.ts',
    rule: 'API_PROVIDER',
    mutate: (source) => `${replaceExactlyOnceLiteral(
      source,
      API_PROVIDER_ENTRY,
      '',
      'provider API',
    )}\nprovideApi('');\n`,
  },
  {
    name: 'provideApi aninhado em factory de provider',
    path: 'frontend/src/app/app.config.ts',
    rule: 'API_PROVIDER',
    mutate: (source) => replaceExactlyOnceLiteral(
      source,
      API_PROVIDER_ENTRY,
      "    { provide: 'api-decoy', useFactory: () => provideApi('') },\n",
      'provider API',
    ),
  },
  {
    name: 'provideApi com base nao vazia',
    path: 'frontend/src/app/app.config.ts',
    rule: 'API_PROVIDER',
    mutate: (source) => replaceExactlyOnceLiteral(
      source,
      API_PROVIDER_ENTRY,
      "    provideApi('/api'),\n",
      'provider API',
    ),
  },
  {
    name: 'provideApi com base em branco',
    path: 'frontend/src/app/app.config.ts',
    rule: 'API_PROVIDER',
    mutate: (source) => replaceExactlyOnceLiteral(
      source,
      API_PROVIDER_ENTRY,
      "    provideApi(' '),\n",
      'provider API',
    ),
  },
  {
    name: 'provideApi duplicado',
    path: 'frontend/src/app/app.config.ts',
    rule: 'API_PROVIDER',
    mutate: (source) => replaceExactlyOnceLiteral(
      source,
      API_PROVIDER_ENTRY,
      `${API_PROVIDER_ENTRY}${API_PROVIDER_ENTRY}`,
      'provider API',
    ),
  },
  {
    name: 'provideApi antes do provider HTTP',
    path: 'frontend/src/app/app.config.ts',
    rule: 'APP_CONFIG_PROVIDER_LIST',
    mutate: (source) => replaceExactlyOnceLiteral(
      replaceExactlyOnceLiteral(source, API_PROVIDER_ENTRY, '', 'provider API'),
      HTTP_PROVIDER_ENTRY,
      `${API_PROVIDER_ENTRY}${HTTP_PROVIDER_ENTRY}`,
      'provider HTTP',
    ),
  },
  {
    name: 'import direto de production',
    path: 'frontend/src/app/app.routes.ts',
    rule: 'DIRECT_PRODUCTION_IMPORT',
    mutate: (source) => source.replace(
      "from './app.routes.development'",
      "from './app.routes.production'",
    ),
  },
  {
    name: 'simbolo development divergente',
    path: 'frontend/src/app/app.routes.development.ts',
    rule: 'DEVELOPMENT_ROUTES_EXPORT',
    mutate: (source) => source.replace('BUILD_SPECIFIC_ROUTES', 'OTHER_ROUTES'),
  },
  {
    name: 'provideRouter ausente',
    path: 'frontend/src/app/app.config.ts',
    rule: 'ROUTER_PROVIDER',
    mutate: (source) => replaceExactlyOnceLiteral(
      source,
      ROUTER_PROVIDER_ENTRY,
      '',
      'provider Router',
    ),
  },
  {
    name: 'provideRouter fora de appConfig.providers',
    path: 'frontend/src/app/app.config.ts',
    rule: 'ROUTER_PROVIDER',
    mutate: (source) => `${replaceExactlyOnceLiteral(
      source,
      ROUTER_PROVIDER_ENTRY,
      '',
      'provider Router',
    )}\nprovideRouter(APP_ROUTES);\n`,
  },
  {
    name: 'provideRouter aninhado em factory de provider',
    path: 'frontend/src/app/app.config.ts',
    rule: 'ROUTER_PROVIDER',
    mutate: (source) => replaceExactlyOnceLiteral(
      source,
      ROUTER_PROVIDER_ENTRY,
      "    { provide: 'router-decoy', useFactory: () => provideRouter(APP_ROUTES) },\n",
      'provider Router',
    ),
  },
  {
    name: 'provideHttpClient ausente',
    path: 'frontend/src/app/app.config.ts',
    rule: 'HTTP_PROVIDER',
    mutate: (source) => replaceExactlyOnceLiteral(
      source,
      HTTP_PROVIDER_ENTRY,
      '',
      'provider HTTP',
    ),
  },
  {
    name: 'provideHttpClient fora de appConfig.providers',
    path: 'frontend/src/app/app.config.ts',
    rule: 'HTTP_PROVIDER',
    mutate: (source) => `${replaceExactlyOnceLiteral(
      source,
      HTTP_PROVIDER_ENTRY,
      '',
      'provider HTTP',
    )}
provideHttpClient(
  withXsrfConfiguration({
    cookieName: 'XSRF-TOKEN',
    headerName: 'X-XSRF-TOKEN',
  }),
);
`,
  },
  {
    name: 'provideHttpClient aninhado em factory de provider',
    path: 'frontend/src/app/app.config.ts',
    rule: 'HTTP_PROVIDER',
    mutate: (source) => replaceExactlyOnceLiteral(
      source,
      HTTP_PROVIDER_ENTRY,
      `    {
      provide: 'http-decoy',
      useFactory: () => provideHttpClient(
        withXsrfConfiguration({
          cookieName: 'XSRF-TOKEN',
          headerName: 'X-XSRF-TOKEN',
        }),
      ),
    },
`,
      'provider HTTP',
    ),
  },
  {
    name: 'cookie XSRF divergente',
    path: 'frontend/src/app/app.config.ts',
    rule: 'XSRF_COOKIE',
    mutate: (source) => source.replace('XSRF-TOKEN', 'WRONG-XSRF-TOKEN'),
  },
  {
    name: 'header XSRF divergente',
    path: 'frontend/src/app/app.config.ts',
    rule: 'XSRF_HEADER',
    mutate: (source) => source.replace('X-XSRF-TOKEN', 'WRONG-X-XSRF-TOKEN'),
  },
  {
    name: 'XSRF desabilitado',
    path: 'frontend/src/app/app.config.ts',
    rule: 'XSRF_DISABLED',
    mutate: (source) => `${source}\nwithNoXsrfProtection();\n`,
  },
  {
    name: 'URL absoluta',
    path: 'frontend/src/app/app.config.ts',
    rule: 'ABSOLUTE_HTTP_URL',
    mutate: (source) => `${source}\nconst backend = 'https://api.example.invalid';\n`,
  },
  {
    name: 'header Authorization',
    path: 'frontend/src/app/app.config.ts',
    rule: 'AUTHORIZATION_HEADER',
    mutate: (source) => `${source}\nconst headerName = 'Authorization';\n`,
  },
  {
    name: 'bearer token',
    path: 'frontend/src/app/app.config.ts',
    rule: 'BEARER_TOKEN',
    mutate: (source) => `${source}\nconst scheme = 'Bearer';\n`,
  },
  {
    name: 'localStorage',
    path: 'frontend/src/app/app.config.ts',
    rule: 'LOCAL_STORAGE',
    mutate: (source) => `${source}\nlocalStorage.setItem('token', 'value');\n`,
  },
  {
    name: 'sessionStorage',
    path: 'frontend/src/app/app.config.ts',
    rule: 'SESSION_STORAGE',
    mutate: (source) => `${source}\nsessionStorage.setItem('token', 'value');\n`,
  },
  {
    name: 'indexedDB',
    path: 'frontend/src/app/app.config.ts',
    rule: 'INDEXED_DB',
    mutate: (source) => `${source}\nindexedDB.open('tokens');\n`,
  },
  {
    name: 'HTTP_INTERCEPTORS',
    path: 'frontend/src/app/app.config.ts',
    rule: 'LEGACY_HTTP_INTERCEPTOR',
    mutate: (source) => `${source}\nconst legacy = HTTP_INTERCEPTORS;\n`,
  },
  {
    name: 'withInterceptors',
    path: 'frontend/src/app/app.config.ts',
    rule: 'HTTP_INTERCEPTOR',
    mutate: (source) => `${source}\nwithInterceptors([]);\n`,
  },
  {
    name: 'withInterceptorsFromDi',
    path: 'frontend/src/app/app.config.ts',
    rule: 'HTTP_INTERCEPTOR',
    mutate: (source) => `${source}\nwithInterceptorsFromDi();\n`,
  },
  {
    name: 'withCredentials no host',
    path: 'frontend/src/app/app.config.ts',
    rule: 'WITH_CREDENTIALS',
    mutate: (source) => `${source}\nconst credentialsMode = { withCredentials: true };\n`,
  },
  {
    name: 'Cookie manual no host',
    path: 'frontend/src/app/app.config.ts',
    rule: 'MANUAL_COOKIE',
    mutate: (source) => `${source}\nconst manualHeader = 'Cookie';\n`,
  },
  {
    name: 'accessToken no host',
    path: 'frontend/src/app/app.config.ts',
    rule: 'ACCESS_TOKEN',
    mutate: (source) => `${source}\nconst accessToken = 'manual';\n`,
  },
  {
    name: 'import dinamico de production',
    path: 'frontend/src/app/app.component.ts',
    rule: 'DYNAMIC_IMPORT_FORBIDDEN',
    mutate: (source) => `${source}\nvoid import('./app.routes.production');\n`,
  },
  {
    name: 'import dinamico de production com template literal',
    path: 'frontend/src/app/app.component.ts',
    rule: 'DYNAMIC_IMPORT_FORBIDDEN',
    mutate: (source) => `${source}\nvoid import(\`./app.routes.production\`);\n`,
  },
  {
    name: 'import dinamico de production em interpolacao executavel',
    path: 'frontend/src/app/app.component.ts',
    rule: 'DYNAMIC_IMPORT_FORBIDDEN',
    mutate: (source) => `${source}\nconst hiddenImport = \`\${import('./app.routes.production')}\`;\n`,
  },
  {
    name: 'import dinamico de production com specifier concatenado',
    path: 'frontend/src/app/app.component.ts',
    rule: 'DYNAMIC_IMPORT_FORBIDDEN',
    mutate: (source) => `${source}\nvoid import('./app.routes.' + 'production');\n`,
  },
  {
    name: 'specifier estatico com escape Unicode',
    path: 'frontend/src/app/app.component.ts',
    rule: 'UNSUPPORTED_SCRIPT_ESCAPE',
    mutate: (source) => [
      String.raw`import './app.routes.\u0070roduction';`,
      source,
    ].join('\n'),
  },
  {
    name: 'mutacao posterior da lista production',
    path: 'frontend/src/app/app.routes.production.ts',
    rule: 'PRODUCTION_ROUTES_UNIQUE',
    mutate: (source) => `${source}\nBUILD_SPECIFIC_ROUTES.push({ path: 'technical', redirectTo: '' });\n`,
  },
  {
    name: 'mutacao production em interpolacao executavel',
    path: 'frontend/src/app/app.routes.production.ts',
    rule: 'PRODUCTION_ROUTES_UNIQUE',
    mutate: (source) => `${source}\nconst hiddenMutation = \`\${BUILD_SPECIFIC_ROUTES.push({ path: 'technical', redirectTo: '' })}\`;\n`,
  },
  {
    name: 'mutacao da lista build-specific dentro da seam',
    path: 'frontend/src/app/app.routes.ts',
    rule: 'PROFILE_GUARD_COMPOSITION',
    mutate: (source) => source.replace(
      'export const APP_ROUTES: Routes = [',
      [
        "BUILD_SPECIFIC_ROUTES.push({ path: 'technical', redirectTo: '' });",
        '',
        'export const APP_ROUTES: Routes = [',
      ].join('\n'),
    ),
  },
  {
    name: 'mutacao posterior da lista final APP_ROUTES',
    path: 'frontend/src/app/app.routes.ts',
    rule: 'PROFILE_GUARD_COMPOSITION',
    mutate: (source) => `${source}\nAPP_ROUTES.push({ path: 'technical', redirectTo: '' });\n`,
  },
  {
    name: 'mutacao APP_ROUTES em interpolacao executavel',
    path: 'frontend/src/app/app.routes.ts',
    rule: 'PROFILE_GUARD_COMPOSITION',
    mutate: (source) => `${source}\nconst hiddenMutation = \`\${APP_ROUTES.push({ path: 'technical', redirectTo: '' })}\`;\n`,
  },
  {
    name: 'regex com barras fora do subconjunto lexico aprovado',
    path: 'frontend/src/app/app.config.ts',
    rule: 'UNSUPPORTED_SCRIPT_SLASH',
    mutate: (source) => [
      source,
      String.raw`const slashes = /\/\//; localStorage.setItem('token', 'value');`,
      '',
    ].join('\n'),
  },
  {
    name: 'regex depois de cabecalho de controle',
    path: 'frontend/src/app/app.config.ts',
    rule: 'UNSUPPORTED_SCRIPT_SLASH',
    mutate: (source) => [
      source,
      String.raw`if (true) /\/\//.test('//'); localStorage.setItem('token', 'value');`,
      '',
    ].join('\n'),
  },
  {
    name: 'regex depois de bloco de controle',
    path: 'frontend/src/app/app.config.ts',
    rule: 'UNSUPPORTED_SCRIPT_SLASH',
    mutate: (source) => [
      source,
      String.raw`if (true) {} /\/\//.test('//'); localStorage.setItem('token', 'value');`,
      '',
    ].join('\n'),
  },
  {
    name: 'divisao depois de propriedade chamada return',
    path: 'frontend/src/app/app.routes.ts',
    rule: 'UNSUPPORTED_SCRIPT_SLASH',
    mutate: (source) => [
      source,
      'const ratio = obj.return / APP_ROUTES.push({ path: \'technical\' }) / 2;',
      '',
    ].join('\n'),
  },
  {
    name: 'divisao depois de incremento pos-fixado',
    path: 'frontend/src/app/app.routes.ts',
    rule: 'UNSUPPORTED_SCRIPT_SLASH',
    mutate: (source) => [
      source,
      'let counter = 0;',
      "counter++ / APP_ROUTES.push({ path: 'technical' }) / 2;",
      '',
    ].join('\n'),
  },
  {
    name: 'regex depois de export default',
    path: 'frontend/src/app/app.routes.ts',
    rule: 'UNSUPPORTED_SCRIPT_SLASH',
    mutate: (source) => [
      source,
      String.raw`export default /\/\//; APP_ROUTES.push({ path: 'technical' });`,
      '',
    ].join('\n'),
  },
  {
    name: 'divisao depois de identificador contextual of',
    path: 'frontend/src/app/app.routes.ts',
    rule: 'UNSUPPORTED_SCRIPT_SLASH',
    mutate: (source) => [
      source,
      'const of = 1;',
      "const ratio = of / APP_ROUTES.push({ path: 'technical' }) / 2;",
      '',
    ].join('\n'),
  },
  {
    name: 'mutacao posterior de appConfig.providers',
    path: 'frontend/src/app/app.config.ts',
    rule: 'APP_CONFIG_IMMUTABLE',
    mutate: (source) => `${source}\nappConfig.providers.length = 2;\n`,
  },
  {
    name: 'sobrescrita posterior de providers via Object.assign',
    path: 'frontend/src/app/app.config.ts',
    rule: 'APP_CONFIG_IMMUTABLE',
    mutate: (source) => `${source}\nObject.assign(appConfig, { providers: [] });\n`,
  },
  {
    name: 'spread posterior sobrescrevendo providers no literal appConfig',
    path: 'frontend/src/app/app.config.ts',
    rule: 'APP_CONFIG_SHAPE',
    mutate: (source) => source.replace(
      '  ],\n};',
      '  ],\n  ...{ providers: [] },\n};',
    ),
  },
  {
    name: 'provider ROUTES adicional fora da seam aprovada',
    path: 'frontend/src/app/app.config.ts',
    rule: 'APP_CONFIG_PROVIDER_LIST',
    mutate: (source) => source
      .replace(
        "import { provideRouter } from '@angular/router';",
        "import { provideRouter } from '@angular/router';\nimport { ROUTES } from '@angular/router';",
      )
      .replace(
        '    provideRouter(APP_ROUTES),',
        `    provideRouter(APP_ROUTES),
    { provide: ROUTES, multi: true, useValue: [{ path: 'technical' }] },`,
      ),
  },
  {
    name: 'provider global de erros removido',
    path: 'frontend/src/app/app.config.ts',
    rule: 'APP_CONFIG_PROVIDER_LIST',
    mutate: (source) => source.replace(
      '    provideBrowserGlobalErrorListeners(),\n',
      '',
    ),
  },
  {
    name: 'provider zoneless removido',
    path: 'frontend/src/app/app.config.ts',
    rule: 'APP_CONFIG_PROVIDER_LIST',
    mutate: (source) => source.replace(
      '    provideZonelessChangeDetection(),\n',
      '',
    ),
  },
  {
    name: 'mutacao de appConfig a partir de main.ts',
    path: 'frontend/src/main.ts',
    rule: 'MAIN_BOOTSTRAP_SHAPE',
    mutate: (source) => `${source}\nappConfig.providers.length = 0;\n`,
  },
  {
    name: 'bootstrap canonico decoy seguido de bootstrap alternativo',
    path: 'frontend/src/main.ts',
    rule: 'MAIN_BOOTSTRAP_SHAPE',
    mutate: (source) => source.replace(
      'bootstrapApplication(AppComponent, appConfig).catch((error: unknown) => {',
      `function approvedButUnused() {
  return bootstrapApplication(AppComponent, appConfig);
}

bootstrapApplication(AppComponent, { providers: [] }).catch((error: unknown) => {`,
    ),
  },
  {
    name: 'Router.resetConfig no componente raiz',
    path: 'frontend/src/app/app.component.ts',
    rule: 'ROOT_COMPONENT_SHAPE',
    mutate: (source) => source
      .replace(
        "import { RouterOutlet } from '@angular/router';",
        "import { Router, RouterOutlet } from '@angular/router';",
      )
      .replace(
        'export class AppComponent {}',
        `export class AppComponent {
  constructor(router: Router) {
    router.resetConfig([{ path: 'technical', redirectTo: '' }]);
  }
}`,
      ),
  },
  {
    name: 'conteudo adicional no template raiz',
    path: 'frontend/src/app/app.component.html',
    rule: 'ROOT_TEMPLATE_SHAPE',
    mutate: (source) => `${source}<a href="technical">Technical</a>\n`,
  },
  {
    name: 'APP_ROUTES com escape Unicode no identificador',
    path: 'frontend/src/app/app.routes.ts',
    rule: 'UNSUPPORTED_SCRIPT_ESCAPE',
    mutate: (source) => [
      source,
      String.raw`APP\u005FROUTES.push({ path: 'technical' });`,
      '',
    ].join('\n'),
  },
  {
    name: 'appConfig com escape Unicode no identificador',
    path: 'frontend/src/app/app.config.ts',
    rule: 'UNSUPPORTED_SCRIPT_ESCAPE',
    mutate: (source) => [
      source,
      String.raw`app\u0043onfig.providers.length = 0;`,
      '',
    ].join('\n'),
  },
  {
    name: 'localStorage com escape Unicode no identificador',
    path: 'frontend/src/app/app.config.ts',
    rule: 'UNSUPPORTED_SCRIPT_ESCAPE',
    mutate: (source) => [
      source,
      String.raw`local\u0053torage.setItem('token', 'value');`,
      '',
    ].join('\n'),
  },
  {
    name: 'codigo ativo depois de comentario encerrado por CR',
    path: 'frontend/src/app/app.routes.ts',
    rule: 'PROFILE_GUARD_COMPOSITION',
    mutate: (source) => `${source}// harmless\rAPP_ROUTES.push({ path: 'technical' });\n`,
  },
  {
    name: 'codigo ativo depois de comentario encerrado por U+2028',
    path: 'frontend/src/app/app.routes.ts',
    rule: 'PROFILE_GUARD_COMPOSITION',
    mutate: (source) => `${source}// harmless\u2028APP_ROUTES.push({ path: 'technical' });\n`,
  },
  {
    name: 'codigo ativo depois de comentario encerrado por U+2029',
    path: 'frontend/src/app/app.routes.ts',
    rule: 'PROFILE_GUARD_COMPOSITION',
    mutate: (source) => `${source}// harmless\u2029APP_ROUTES.push({ path: 'technical' });\n`,
  },
  {
    name: 'spread posterior nas opcoes XSRF',
    path: 'frontend/src/app/app.config.ts',
    rule: 'XSRF_OPTIONS_SHAPE',
    mutate: (source) => source
      .replace(
        'export const appConfig',
        "const xsrfOverrides = { cookieName: 'OVERRIDDEN-XSRF-TOKEN' };\n\nexport const appConfig",
      )
      .replace(
        "headerName: 'X-XSRF-TOKEN',",
        "headerName: 'X-XSRF-TOKEN',\n        ...xsrfOverrides,",
      ),
  },
];

const generatedSourceMutations = [
  {
    name: 'fallback localhost no BaseService gerado',
    path: 'frontend/src/app/api/generated/api.base.service.ts',
    rule: 'GENERATED_BASE_PATH',
    mutate: (source) => source.replace(
      /protected basePath\s*=\s*(?:''|'http:\/\/localhost')\s*;/u,
      "protected basePath = 'http://localhost';",
    ),
  },
  {
    name: 'fallback vazio somente em comentario no BaseService gerado',
    path: 'frontend/src/app/api/generated/api.base.service.ts',
    rule: 'GENERATED_BASE_PATH',
    mutate: (source) => source.replace(
      "    protected basePath = '';",
      "    /* protected basePath = ''; */\n    protected fallbackPath = '';",
    ),
  },
  {
    name: 'fallback vazio somente em template string no BaseService gerado',
    path: 'frontend/src/app/api/generated/api.base.service.ts',
    rule: 'GENERATED_BASE_PATH',
    mutate: (source) => source.replace(
      "    protected basePath = '';",
      '    protected fallbackPath = ' + String.fromCodePoint(96)
        + "protected basePath = '';" + String.fromCodePoint(96) + ';',
    ),
  },
  {
    name: 'fallback relativo nao vazio no BaseService gerado',
    path: 'frontend/src/app/api/generated/api.base.service.ts',
    rule: 'GENERATED_BASE_PATH',
    mutate: (source) => source.replace(
      "    protected basePath = '';",
      "    protected basePath = '/api';",
    ),
  },
  {
    name: 'fallback vazio em outra classe gerada',
    path: 'frontend/src/app/api/generated/api.base.service.ts',
    rule: 'GENERATED_BASE_PATH',
    mutate: (source) => source.replace(
      "export class BaseService {\n    protected basePath = '';",
      "export class Decoy {\n    protected basePath = '';\n}\nexport class BaseService {",
    ),
  },
  {
    name: 'origem absoluta fora de um servico gerado',
    path: 'frontend/src/app/api/generated/model/current-identity-response.ts',
    rule: 'GENERATED_ABSOLUTE_URL',
    mutate: (source) => source + "\nconst absoluteEndpoint = 'https://api.example.invalid';\n",
  },
  {
    name: 'network-path fora de um servico gerado',
    path: 'frontend/src/app/api/generated/model/current-identity-response.ts',
    rule: 'GENERATED_ABSOLUTE_URL',
    mutate: (source) => source + "\nconst absoluteEndpoint = '//api.example.invalid';\n",
  },
  {
    name: 'origem absoluta com escape hexadecimal',
    path: 'frontend/src/app/api/generated/model/current-identity-response.ts',
    rule: 'GENERATED_ABSOLUTE_URL',
    mutate: (source) => source + "\nconst absoluteEndpoint = 'h\\x74tps://api.example.invalid';\n",
  },
  {
    name: 'origem absoluta com solidus escapado',
    path: 'frontend/src/app/api/generated/model/current-identity-response.ts',
    rule: 'GENERATED_ABSOLUTE_URL',
    mutate: (source) => source + "\nconst absoluteEndpoint = 'https:\\/\\/api.example.invalid';\n",
  },
  {
    name: 'origem absoluta construida por concatenacao',
    path: 'frontend/src/app/api/generated/model/current-identity-response.ts',
    rule: 'GENERATED_ABSOLUTE_URL',
    mutate: (source) => source + "\nconst absoluteEndpoint = 'https:' + '//api.example.invalid';\n",
  },
  {
    name: 'origem absoluta depois de regex com barras escapadas',
    path: 'frontend/src/app/api/generated/model/current-identity-response.ts',
    rule: 'GENERATED_ABSOLUTE_URL',
    mutate: (source) => source
      + '\n'
      + String.raw`const urlPattern = /https?:\/\//; const absoluteEndpoint = 'https://api.example.invalid';`
      + '\n',
  },
  {
    name: 'diagnostico ApiModule movido para fora do bloco canonico',
    path: 'frontend/src/app/api/generated/api.module.ts',
    rule: 'GENERATED_ABSOLUTE_URL',
    mutate: (source) => source.replace(
      "            'See also https://github.com/angular/angular/issues/20575');",
      "            'See documentation');\nconst moved = 'See also https://github.com/angular/angular/issues/20575';",
    ),
  },
  {
    name: 'request gerado sem composicao canonica de basePath',
    path: 'frontend/src/app/api/generated/api/identity.service.ts',
    rule: 'GENERATED_REQUEST_URL',
    mutate: (source) => source.replace(
      String.fromCodePoint(96) + '${basePath}${localVarPath}' + String.fromCodePoint(96),
      'localVarPath',
    ),
  },
  {
    name: 'segunda chamada HTTP no IdentityService gerado',
    path: 'frontend/src/app/api/generated/api/identity.service.ts',
    rule: 'GENERATED_REQUEST_URL',
    mutate: (source) => replaceExactlyOnceLiteral(
      source,
      "        return this.httpClient.request<CurrentIdentityResponse>('put',",
      "        this.httpClient.request<CurrentIdentityResponse>('put', '/decoy', {});\n        return this.httpClient.request<CurrentIdentityResponse>('put',",
      'chamada HTTP canonica',
    ),
  },
  {
    name: 'reatribuicao de localVarPath no IdentityService gerado',
    path: 'frontend/src/app/api/generated/api/identity.service.ts',
    rule: 'GENERATED_REQUEST_URL',
    mutate: (source) => replaceExactlyOnceLiteral(
      source,
      '        let localVarPath = `/api/v1/tracer/identity/current`;',
      "        let localVarPath = `/api/v1/tracer/identity/current`;\n        localVarPath = '/other';",
      'declaracao localVarPath',
    ),
  },
  {
    name: 'origem absoluta no IdentityService gerado',
    rule: 'GENERATED_ABSOLUTE_URL',
    mutate: (source) => `${source}\nconst absoluteEndpoint = 'https://api.example.invalid';\n`,
  },
  {
    name: 'Authorization ativo no servico gerado',
    rule: 'GENERATED_AUTHORIZATION',
    mutate: (source) => `${source}\nconst activeHeader = 'Authorization';\n`,
  },
  {
    name: 'Bearer ativo no servico gerado',
    rule: 'GENERATED_BEARER',
    mutate: (source) => `${source}\nconst activeScheme = 'Bearer';\n`,
  },
  {
    name: 'credencial ativa no servico gerado',
    rule: 'GENERATED_AUTH_CONFIGURATION',
    mutate: (source) => `${source}\nconst accessToken = 'manual';\n`,
  },
  {
    name: 'password ativo no servico gerado',
    rule: 'GENERATED_AUTH_CONFIGURATION',
    mutate: (source) => `${source}\nconst password = 'manual';\n`,
  },
  {
    name: 'apiKeys acessado por chave literal no servico gerado',
    rule: 'GENERATED_AUTH_CONFIGURATION',
    mutate: (source) => `${source}\nconst manual = configuration['apiKeys'];\n`,
  },
  {
    name: 'credentials acessado por chave literal no servico gerado',
    rule: 'GENERATED_AUTH_CONFIGURATION',
    mutate: (source) => `${source}\nconst manual = configuration['credentials'];\n`,
  },
  {
    name: 'password acessado por chave literal no servico gerado',
    rule: 'GENERATED_AUTH_CONFIGURATION',
    mutate: (source) => `${source}\nconst manual = configuration['password'];\n`,
  },
  {
    name: 'Cookie manual no servico gerado',
    rule: 'GENERATED_MANUAL_COOKIE',
    mutate: (source) => `${source}\nconst manualHeader = 'Cookie';\n`,
  },
  {
    name: 'withCredentials efetivo no servico gerado',
    rule: 'GENERATED_WITH_CREDENTIALS',
    mutate: (source) => replaceExactlyOnceLiteral(
      source,
      '...(withCredentials ? { withCredentials } : {}),',
      'withCredentials: true,',
      'encadeamento withCredentials',
    ),
  },
  {
    name: 'withCredentials ativo por padrao em configuration.ts',
    path: 'frontend/src/app/api/generated/configuration.ts',
    rule: 'GENERATED_CONFIGURATION_AUTH',
    mutate: (source) => replaceExactlyOnceLiteral(
      source,
      'this.withCredentials = withCredentials;',
      'this.withCredentials = true;',
      'atribuicao withCredentials',
    ),
  },
  {
    name: 'withCredentials ativo por initializer em configuration.ts',
    path: 'frontend/src/app/api/generated/configuration.ts',
    rule: 'GENERATED_CONFIGURATION_AUTH',
    mutate: (source) => source.replace(
      /(export class Configuration[\s\S]*?)withCredentials\?: boolean;/u,
      '$1withCredentials: boolean = true;',
    ),
  },
  {
    name: 'basePath absoluto por initializer em configuration.ts',
    path: 'frontend/src/app/api/generated/configuration.ts',
    rule: 'GENERATED_ABSOLUTE_URL',
    mutate: (source) => source.replace(
      /(export class Configuration[\s\S]*?)basePath\?: string;/u,
      "$1basePath: string = 'https://api.example.invalid';",
    ),
  },
  {
    name: 'basePath absoluto por atribuicao no construtor',
    path: 'frontend/src/app/api/generated/configuration.ts',
    rule: 'GENERATED_ABSOLUTE_URL',
    mutate: (source) => replaceExactlyOnceLiteral(
      source,
      'this.basePath = basePath;',
      "this.basePath = 'https://api.example.invalid';",
      'atribuicao basePath',
    ),
  },
  {
    name: 'credencial ativa por padrao em configuration.ts',
    path: 'frontend/src/app/api/generated/configuration.ts',
    rule: 'GENERATED_CONFIGURATION_AUTH',
    mutate: (source) => replaceExactlyOnceLiteral(
      source,
      'this.credentials = credentials ?? {};',
      "this.credentials = credentials ?? { bearerAuth: 'manual' };",
      'fallback credentials',
    ),
  },
  {
    name: 'withCredentials ativo por provideApi',
    path: 'frontend/src/app/api/generated/provide-api.ts',
    rule: 'GENERATED_PROVIDER_AUTH',
    mutate: (source) => replaceExactlyOnceLiteral(
      source,
      '? { provide: BASE_PATH, useValue: configOrBasePath }',
      '? { provide: Configuration, useValue: new Configuration({ withCredentials: true }) }',
      'branch string de provideApi',
    ),
  },
];

for (const sourceMutation of sourceMutations) {
  test(`recusa ${sourceMutation.name}`, async () => {
    await withFixture(sourceMutation.name, async (fixtureRoot) => {
      await mutateText(
        fixtureRoot,
        sourceMutation.path,
        sourceMutation.mutate,
      );

      const result = runValidator(fixtureRoot);

      expectValidationFailure(result, sourceMutation.rule);
    });
  });
}

for (const generatedSourceMutation of generatedSourceMutations) {
  test(`recusa ${generatedSourceMutation.name}`, async () => {
    await withFixture(generatedSourceMutation.name, async (fixtureRoot) => {
      await mutateText(
        fixtureRoot,
        generatedSourceMutation.path
          ?? 'frontend/src/app/api/generated/api/identity.service.ts',
        generatedSourceMutation.mutate,
      );

      const result = runValidator(fixtureRoot);

      expectValidationFailure(result, generatedSourceMutation.rule);
    });
  });
}

test('recusa mutacao semantica valida na arvore gerada por quebra de digest', async () => {
  await withFixture('generated-benign-digest-mismatch', async (fixtureRoot) => {
    await mutateText(
      fixtureRoot,
      'frontend/src/app/api/generated/model/current-identity-response.ts',
      (source) => `${source}\n// harmless comment that does not trigger semantic rules\n`,
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'GENERATED_MANIFEST_DIGEST');
  });
});

test('recusa arquivo TypeScript extra na arvore gerada', async () => {
  await withFixture('generated-extra-file', async (fixtureRoot) => {
    const extraPath = join(
      fixtureRoot,
      'frontend/src/app/api/generated/unapproved.ts',
    );
    await writeFile(extraPath, 'export const unapproved = true;\n', 'utf8');

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'GENERATED_MANIFEST_PATHS');
  });
});

test('recusa segundo servico na arvore gerada', async () => {
  await withFixture('generated-second-service', async (fixtureRoot) => {
    const extraServicePath = join(
      fixtureRoot,
      'frontend/src/app/api/generated/api/admin.service.ts',
    );
    await writeFile(extraServicePath, 'export class AdminService {}\n', 'utf8');

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'GENERATED_MANIFEST_PATHS');
  });
});

test('ignora marcadores proibidos presentes somente em comentarios', async () => {
  await withFixture('comments-ignored', async (fixtureRoot) => {
    await mutateText(
      fixtureRoot,
      'frontend/src/app/app.config.ts',
      (source) => `${source}\n/* https://example.invalid Authorization Bearer localStorage sessionStorage indexedDB HTTP_INTERCEPTORS withInterceptors withNoXsrfProtection */\n`,
    );

    const result = runValidator(fixtureRoot);

    assert.equal(result.status, 0, result.stderr);
  });
});

test('nao aceita provider obrigatorio presente somente em comentario', async () => {
  await withFixture('required-code-comment', async (fixtureRoot) => {
    await mutateText(
      fixtureRoot,
      'frontend/src/app/app.config.ts',
      (source) => `${source.replace(/\s*provideRouter\(APP_ROUTES\),/u, '')}\n// provideRouter(APP_ROUTES)\n`,
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'ROUTER_PROVIDER');
  });
});

test('nao aceita provider obrigatorio presente somente em string', async () => {
  await withFixture('required-code-string', async (fixtureRoot) => {
    await mutateText(
      fixtureRoot,
      'frontend/src/app/app.config.ts',
      (source) => `${source.replace(/\s*provideRouter\(APP_ROUTES\),/u, '')}\nconst fakeProvider = 'provideRouter(APP_ROUTES)';\n`,
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'ROUTER_PROVIDER');
  });
});

test('nao aceita export de rota presente somente em string', async () => {
  await withFixture('required-export-string', async (fixtureRoot) => {
    await mutateText(
      fixtureRoot,
      'frontend/src/app/app.routes.ts',
      (source) => `${source.replace(/export const APP_ROUTES:[^;]+;/u, '')}\nconst fakeExport = 'export const APP_ROUTES: Routes = [...BUILD_SPECIFIC_ROUTES];';\n`,
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'PROFILE_GUARD_COMPOSITION');
  });
});

test('aceita a rota operacional de producao operacao/painel', async () => {
  await withFixture('canonical-operations-route', async (fixtureRoot) => {
    await overwriteText(
      fixtureRoot,
      'frontend/src/app/app.routes.production.ts',
      CANONICAL_PRODUCTION_ROUTES,
    );
    await overwriteText(
      fixtureRoot,
      developmentRoutesPath,
      CANONICAL_DEVELOPMENT_ROUTES,
    );

    const result = runValidator(fixtureRoot);

    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /\[validate-angular-route-boundary\] OK:/u);
  });
});

test('recusa rota operacao/painel com componente divergente', async () => {
  await withFixture('invalid-operations-route-component', async (fixtureRoot) => {
    const invalidOperationsEntry = [
      '  {',
      "    path: 'operacao/painel',",
      '    loadComponent: () =>',
      "      import('./features/operations/operations-panel-page.component').then(",
      '        ({ WrongComponent }) => WrongComponent,',
      '      ),',
      '  },',
    ].join('\n');

    await overwriteText(
      fixtureRoot,
      'frontend/src/app/app.routes.production.ts',
      canonicalAuthenticatedRoutes([
        CANONICAL_PROFILE_ROUTE_ENTRY,
        invalidOperationsEntry,
      ]),
    );
    await overwriteText(
      fixtureRoot,
      developmentRoutesPath,
      canonicalAuthenticatedRoutes([
        CANONICAL_TRACER_ROUTE_ENTRY,
        CANONICAL_DIAGNOSTIC_ROUTE_ENTRY,
        CANONICAL_PROFILE_ROUTE_ENTRY,
        invalidOperationsEntry,
      ]),
    );

    const result = runValidator(fixtureRoot);

    expectValidationFailure(result, 'PRODUCTION_ROUTES_ALLOWLIST');
  });
});
