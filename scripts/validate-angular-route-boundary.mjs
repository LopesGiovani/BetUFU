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
  profileGuard: 'frontend/src/app/core/profile/profile-completion.guard.ts',
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

function canonicalDynamicImportPattern(modulePath) {
  const escapedModulePath = modulePath.replace(/[.*+?^${}()|[\]\\]/gu, '\\$&');
  return new RegExp(String.raw`\bimport\s*\(\s*'${escapedModulePath}'\s*\)`, 'u');
}

function approvedRoute(path, modulePath, component, pathMatch = null) {
  return Object.freeze({
    path,
    modulePath,
    component,
    pathMatch,
    dynamicImportPattern: canonicalDynamicImportPattern(modulePath),
  });
}

const APPROVED_PUBLIC_ROUTES = Object.freeze([
  approvedRoute(
    '',
    './features/sports/public-agenda/public-agenda-page.component',
    'PublicAgendaPageComponent',
    'full',
  ),
  approvedRoute(
    'arena',
    './features/sports/public-agenda/public-agenda-page.component',
    'PublicAgendaPageComponent',
  ),
  approvedRoute(
    'competicoes/:competitionId',
    './features/sports/public-competition/public-competition-page.component',
    'PublicCompetitionPageComponent',
  ),
  approvedRoute(
    'eventos/:eventId',
    './features/sports/public-event/public-event-page.component',
    'PublicEventPageComponent',
  ),
  approvedRoute(
    'organizacoes/:organizationId',
    './features/sports/public-organization/public-organization-page.component',
    'PublicOrganizationPageComponent',
  ),
  approvedRoute(
    'equipes/:teamId',
    './features/sports/public-team/public-team-page.component',
    'PublicTeamPageComponent',
  ),
  approvedRoute(
    'atletas/:athleteId',
    './features/sports/public-athlete/public-athlete-page.component',
    'PublicAthletePageComponent',
  ),
  approvedRoute(
    'chaveamentos/:bracketId',
    './features/sports/public-bracket/public-bracket-page.component',
    'PublicBracketPageComponent',
  ),
  approvedRoute(
    'classificacoes/:standingsId',
    './features/sports/public-standings/public-standings-page.component',
    'PublicStandingsPageComponent',
  ),
]);

const APPROVED_PUBLIC_MARKET_ROUTES = Object.freeze([
  approvedRoute(
    'odds/mercados/:marketId',
    './features/odds/public-market/public-market-page.component',
    'PublicMarketPageComponent',
  ),
]);

const APPROVED_ODDS_OPERATION_ROUTES = Object.freeze([
  approvedRoute(
    'operacao/odds/mercados',
    './features/odds/operations/odds-operations-page.component',
    'OddsOperationsPageComponent',
  ),
  approvedRoute(
    'operacao/odds/mercados/:marketId',
    './features/odds/operations/odds-operations-page.component',
    'OddsOperationsPageComponent',
  ),
]);

const APPROVED_ROOT_STATE_ROUTES = Object.freeze([
  approvedRoute(
    'entrar',
    './features/session/login-required-page.component',
    'LoginRequiredPageComponent',
  ),
  approvedRoute(
    '**',
    './features/errors/not-found-page.component',
    'NotFoundPageComponent',
  ),
]);

const APPROVED_PHASE_SIX_ROUTES = Object.freeze([
  approvedRoute('ranking', './features/ranking/ranking-page.component', 'RankingPageComponent'),
  approvedRoute('conta/historico', './features/settlement/settlement-history-page.component', 'SettlementHistoryPageComponent'),
  approvedRoute('conta/grupos', './features/ranking/group-page.component', 'GroupPageComponent'),
  approvedRoute('conta/conquistas', './features/ranking/achievement-list.component', 'AchievementListComponent'),
  approvedRoute('operacao/liquidacoes', './features/settlement/settlement-operations-page.component', 'SettlementOperationsPageComponent'),
]);

const APPROVED_CONSOLE_ROUTES = Object.freeze([
  approvedRoute(
    '',
    './features/sports/operations/catalog/sports-catalog-page.component',
    'SportsCatalogPageComponent',
    'full',
  ),
  approvedRoute(
    'catalogo',
    './features/sports/operations/catalog/sports-catalog-page.component',
    'SportsCatalogPageComponent',
  ),
  approvedRoute(
    'organizacoes',
    './features/sports/operations/organizations/sports-organizations-page.component',
    'SportsOrganizationsPageComponent',
  ),
  approvedRoute(
    'importacao',
    './features/sports/operations/tabular-import/sports-tabular-import-page.component',
    'SportsTabularImportPageComponent',
  ),
]);

const APPROVED_PRODUCT_ROUTES = Object.freeze([
  approvedRoute(
    'conta/perfil/completar',
    './features/profile/profile-gate-page.component',
    'ProfileGatePageComponent',
  ),
  approvedRoute(
    'conta/perfil',
    './features/profile/profile-page.component',
    'ProfilePageComponent',
  ),
  approvedRoute(
    'conta/perfil/esportivo',
    './features/sports/athlete-profile/athlete-profile-page.component',
    'AthleteProfilePageComponent',
  ),
  approvedRoute(
    'conta/matricula',
    './features/enrollment/enrollment-page.component',
    'EnrollmentPageComponent',
  ),
  approvedRoute(
    'conta/vinculo-atleta',
    './features/sports/link-proposal/athlete-link-proposal-page.component',
    'AthleteLinkProposalPageComponent',
  ),
  approvedRoute(
    'operacao/painel',
    './features/operations/operations-panel-page.component',
    'OperationsPanelPageComponent',
  ),
  approvedRoute(
    'operacao/papeis',
    './features/operations/role-admin-page.component',
    'RoleAdminPageComponent',
  ),
  approvedRoute(
    'operacao/matriculas/corrigir',
    './features/operations/enrollment-admin-page.component',
    'EnrollmentAdminPageComponent',
  ),
]);

const APPROVED_SPORTS_OPERATION_IMPORTS = Object.freeze([
  './catalog/sports-catalog-page.component',
  './catalog/sports-catalog-page.component',
  './organizations/sports-organizations-page.component',
  './athletes/sports-athletes-page.component',
  './rosters/sports-rosters-page.component',
  './tabular-import/sports-tabular-import-page.component',
  './events-results/sports-event-result-page.component',
  './bracket/sports-bracket-page.component',
  './bracket/sports-bracket-page.component',
  './standings/sports-standings-page.component',
  './standings/sports-standings-page.component',
  './link-review/athlete-link-review-page.component',
].map((modulePath) => approvedRoute(modulePath, modulePath, 'LazyComponent')));

const APPROVED_PERSONAL_ROUTES = Object.freeze([
  approvedRoute(
    'perfil/completar',
    './features/profile/profile-gate-page.component',
    'ProfileGatePageComponent',
  ),
  approvedRoute(
    'perfil',
    './features/profile/profile-page.component',
    'ProfilePageComponent',
  ),
  approvedRoute(
    'matricula',
    './features/enrollment/enrollment-page.component',
    'EnrollmentPageComponent',
  ),
  approvedRoute(
    'vinculo-atleta',
    './features/sports/link-proposal/athlete-link-proposal-page.component',
    'AthleteLinkProposalPageComponent',
  ),
]);

const APPROVED_PERSONAL_EXTRA_ROUTES = Object.freeze([
  approvedRoute(
    '',
    './features/sports/athlete-profile/athlete-profile-page.component',
    'AthleteProfilePageComponent',
  ),
]);

const APPROVED_LEGACY_OPERATIONAL_ROUTES = Object.freeze([
  approvedRoute(
    'painel',
    './features/operations/operations-panel-page.component',
    'OperationsPanelPageComponent',
  ),
  approvedRoute(
    'papeis',
    './features/operations/role-admin-page.component',
    'RoleAdminPageComponent',
  ),
  approvedRoute(
    'matriculas/corrigir',
    './features/operations/enrollment-admin-page.component',
    'EnrollmentAdminPageComponent',
  ),
]);

const APPROVED_LEGACY_AUTHENTICATED_ROUTES = Object.freeze(
  APPROVED_PRODUCT_ROUTES.filter(
    (route) => !new Set(['conta/perfil/completar', 'conta/perfil', 'conta/matricula']).has(route.path),
  ),
);

const APPROVED_TECHNICAL_ROUTES = Object.freeze([
  approvedRoute(
    'tracer',
    './features/tracer/tracer-page.component',
    'TracerPageComponent',
  ),
  approvedRoute(
    'diagnostic',
    './features/diagnostic/diagnostic-page.component',
    'DiagnosticPageComponent',
  ),
]);

const APP_ROUTES_PATTERN = /^\s*import\s+type\s*\{\s*Routes\s*\}\s*from\s*'@angular\/router'\s*;\s*import\s*\{\s*profileCompletionGuard\s*\}\s*from\s*'\.\/core\/profile\/profile-completion\.guard'\s*;\s*import\s*\{\s*BUILD_SPECIFIC_ROUTES\s*,\s*PUBLIC_MARKET_ROUTES\s*\}\s*from\s*'\.\/app\.routes\.development'\s*;\s*import\s*\{\s*ArenaShellComponent\s*\}\s*from\s*'\.\/layout\/arena-shell\.component'\s*;\s*export\s+const\s+APP_ROUTES\s*:\s*Routes\s*=\s*\[\s*\{\s*path\s*:\s*''\s*,\s*component\s*:\s*ArenaShellComponent\s*,\s*children\s*:\s*\[(?<publicChildren>[\s\S]*?)\]\s*,?\s*\}\s*,\s*\{\s*path\s*:\s*''\s*,\s*canActivateChild\s*:\s*\[\s*profileCompletionGuard\s*\]\s*,\s*children\s*:\s*BUILD_SPECIFIC_ROUTES\s*,?\s*\}\s*,?\s*\]\s*;\s*$/u;
const APP_ROUTES_ODDS_HYBRID_PATTERN = /^\s*import\s+type\s*\{\s*Routes\s*\}\s*from\s*'@angular\/router'\s*;\s*import\s*\{\s*profileCompletionGuard\s*\}\s*from\s*'\.\/core\/profile\/profile-completion\.guard'\s*;\s*import\s*\{\s*BUILD_SPECIFIC_ROUTES\s*,\s*PUBLIC_MARKET_ROUTES\s*\}\s*from\s*'\.\/app\.routes\.development'\s*;\s*import\s*\{\s*ArenaShellComponent\s*\}\s*from\s*'\.\/layout\/arena-shell\.component'\s*;\s*import\s*\{\s*ConsoleShellComponent\s*\}\s*from\s*'\.\/layout\/console-shell\.component'\s*;\s*import\s*\{\s*sportsOperationsRoutes\s*\}\s*from\s*'\.\/features\/sports\/operations\/sports-operations\.routes'\s*;\s*export\s+const\s+APP_ROUTES\s*:\s*Routes\s*=\s*\[\s*\{\s*path\s*:\s*''\s*,\s*component\s*:\s*ArenaShellComponent\s*,\s*children\s*:\s*\[(?<publicChildren>[\s\S]*?)\]\s*,?\s*\}\s*,\s*\{\s*path\s*:\s*''\s*,\s*canActivateChild\s*:\s*\[\s*profileCompletionGuard\s*\]\s*,\s*children\s*:\s*\[\s*\.\.\.sportsOperationsRoutes\s*,\s*\.\.\.BUILD_SPECIFIC_ROUTES\s*,\s*\{\s*path\s*:\s*'(?<oddsOperationsPath>[^']+)'\s*,\s*component\s*:\s*ConsoleShellComponent\s*,\s*data\s*:\s*\{\s*consoleVariant\s*:\s*'operational'\s*\}\s*,\s*children\s*:\s*\[(?<oddsOperationsChildren>[\s\S]*?)\]\s*,?\s*\}\s*,?\s*\]\s*,?\s*\}\s*,?\s*\]\s*;\s*$/u;
const APP_ROUTES_SESSION_STATES_PATTERN = new RegExp(
  String.raw`^\s*import\s+type\s*\{\s*Routes\s*\}\s*from\s*'@angular/router'\s*;\s*import\s*\{\s*profileCompletionGuard\s*\}\s*from\s*'\./core/profile/profile-completion\.guard'\s*;\s*import\s*\{\s*authenticationGuard\s*\}\s*from\s*'\./core/session/authentication\.guard'\s*;\s*import\s*\{\s*BUILD_SPECIFIC_ROUTES\s*,\s*PUBLIC_MARKET_ROUTES\s*\}\s*from\s*'\./app\.routes\.development'\s*;\s*import\s*\{\s*ArenaShellComponent\s*\}\s*from\s*'\./layout/arena-shell\.component'\s*;\s*import\s*\{\s*ConsoleShellComponent\s*\}\s*from\s*'\./layout/console-shell\.component'\s*;\s*import\s*\{\s*sportsOperationsRoutes\s*\}\s*from\s*'\./features/sports/operations/sports-operations\.routes'\s*;\s*export\s+const\s+APP_ROUTES\s*:\s*Routes\s*=\s*\[\s*\{\s*path\s*:\s*''\s*,\s*component\s*:\s*ArenaShellComponent\s*,\s*children\s*:\s*\[(?<publicChildren>[\s\S]*?)\]\s*,?\s*\}\s*,\s*\{\s*path\s*:\s*'entrar'\s*,\s*loadComponent\s*:\s*\(\s*\)\s*=>\s*import\s*\(\s*'\./features/session/login-required-page\.component'\s*\)\s*\.then\s*\(\s*\(\s*\{\s*LoginRequiredPageComponent\s*\}\s*\)\s*=>\s*LoginRequiredPageComponent\s*,?\s*\)\s*,?\s*\}\s*,\s*\{\s*path\s*:\s*''\s*,\s*canActivateChild\s*:\s*\[\s*authenticationGuard\s*,\s*profileCompletionGuard\s*\]\s*,\s*children\s*:\s*\[\s*\.\.\.sportsOperationsRoutes\s*,\s*\.\.\.BUILD_SPECIFIC_ROUTES\s*,\s*\{\s*path\s*:\s*'(?<oddsOperationsPath>[^']+)'\s*,\s*component\s*:\s*ConsoleShellComponent\s*,\s*data\s*:\s*\{\s*consoleVariant\s*:\s*'operational'\s*\}\s*,\s*children\s*:\s*\[(?<oddsOperationsChildren>[\s\S]*?)\]\s*,?\s*\}\s*,?\s*\]\s*,?\s*\}\s*,\s*\{\s*path\s*:\s*'\*\*'\s*,\s*loadComponent\s*:\s*\(\s*\)\s*=>\s*import\s*\(\s*'\./features/errors/not-found-page\.component'\s*\)\s*\.then\s*\(\s*\(\s*\{\s*NotFoundPageComponent\s*\}\s*\)\s*=>\s*NotFoundPageComponent\s*,?\s*\)\s*,?\s*\}\s*,?\s*\]\s*;\s*$`,
  'u',
);
const APP_ROUTES_HYBRID_PATTERN = /^\s*import\s+type\s*\{\s*Routes\s*\}\s*from\s*'@angular\/router'\s*;\s*import\s*\{\s*profileCompletionGuard\s*\}\s*from\s*'\.\/core\/profile\/profile-completion\.guard'\s*;\s*import\s*\{\s*BUILD_SPECIFIC_ROUTES\s*,\s*PUBLIC_MARKET_ROUTES\s*\}\s*from\s*'\.\/app\.routes\.development'\s*;\s*import\s*\{\s*ArenaShellComponent\s*\}\s*from\s*'\.\/layout\/arena-shell\.component'\s*;\s*import\s*\{\s*sportsOperationsRoutes\s*\}\s*from\s*'\.\/features\/sports\/operations\/sports-operations\.routes'\s*;\s*export\s+const\s+APP_ROUTES\s*:\s*Routes\s*=\s*\[\s*\{\s*path\s*:\s*''\s*,\s*component\s*:\s*ArenaShellComponent\s*,\s*children\s*:\s*\[(?<publicChildren>[\s\S]*?)\]\s*,?\s*\}\s*,\s*\{\s*path\s*:\s*''\s*,\s*canActivateChild\s*:\s*\[\s*profileCompletionGuard\s*\]\s*,\s*children\s*:\s*\[\.\.\.sportsOperationsRoutes\s*,\s*\.\.\.BUILD_SPECIFIC_ROUTES\s*\]\s*,?\s*\}\s*,?\s*\]\s*;\s*$/u;
const BUILD_SPECIFIC_ROUTES_PATTERN = /^\s*import\s+type\s*\{\s*Routes\s*\}\s*from\s*'@angular\/router'\s*;\s*import\s*\{\s*AuthenticatedLayoutComponent\s*\}\s*from\s*'\.\/layout\/authenticated-layout\.component'\s*;\s*import\s*\{\s*ConsoleShellComponent\s*\}\s*from\s*'\.\/layout\/console-shell\.component'\s*;\s*(?:export\s+const\s+PUBLIC_MARKET_ROUTES\s*:\s*Routes\s*=\s*\[[\s\S]*?\]\s*;\s*)?export\s+const\s+BUILD_SPECIFIC_ROUTES\s*:\s*Routes\s*=\s*\[\s*\{\s*path\s*:\s*'operacao\/esportes'\s*,\s*component\s*:\s*ConsoleShellComponent\s*,\s*children\s*:\s*\[(?<consoleChildren>[\s\S]*?)\]\s*,?\s*\}\s*,\s*\{\s*path\s*:\s*''\s*,\s*component\s*:\s*AuthenticatedLayoutComponent\s*,\s*children\s*:\s*\[(?<authenticatedChildren>[\s\S]*?)\]\s*,?\s*\}\s*,?\s*\]\s*;\s*$/u;
const BUILD_SPECIFIC_ROUTES_HYBRID_PATTERN = /^\s*import\s+type\s*\{\s*Routes\s*\}\s*from\s*'@angular\/router'\s*;\s*import\s*\{\s*AuthenticatedLayoutComponent\s*\}\s*from\s*'\.\/layout\/authenticated-layout\.component'\s*;\s*import\s*\{\s*ConsoleShellComponent\s*\}\s*from\s*'\.\/layout\/console-shell\.component'\s*;\s*(?:export\s+const\s+PUBLIC_MARKET_ROUTES\s*:\s*Routes\s*=\s*\[[\s\S]*?\]\s*;\s*)?export\s+const\s+BUILD_SPECIFIC_ROUTES\s*:\s*Routes\s*=\s*\[\s*\{\s*path\s*:\s*'operacao\/esportes'\s*,\s*component\s*:\s*ConsoleShellComponent\s*,\s*data\s*:\s*\{\s*consoleVariant\s*:\s*'operational'\s*\}\s*,\s*children\s*:\s*\[(?<consoleChildren>[\s\S]*?)\]\s*,?\s*\}\s*,\s*\{\s*path\s*:\s*'conta'\s*,\s*component\s*:\s*ConsoleShellComponent\s*,\s*data\s*:\s*\{\s*consoleVariant\s*:\s*'personal'\s*\}\s*,\s*children\s*:\s*\[(?<personalChildren>[\s\S]*?)\]\s*,?\s*\}\s*,\s*\{\s*path\s*:\s*''\s*,\s*component\s*:\s*AuthenticatedLayoutComponent\s*,\s*children\s*:\s*\[(?<authenticatedChildren>[\s\S]*?)\]\s*,?\s*\}\s*,?\s*\]\s*;\s*$/u;
const LAZY_CHILD_ROUTE_PATTERN = /\{\s*path\s*:\s*'(?<path>[^']*)'\s*,\s*(?:pathMatch\s*:\s*'(?<pathMatch>full)'\s*,\s*)?loadComponent\s*:\s*\(\s*\)\s*=>\s*import\s*\(\s*'(?<modulePath>[^']+)'\s*\)\s*\.then\s*\(\s*\(\s*\{\s*(?<component>[A-Za-z_$][\w$]*)\s*\}\s*\)\s*=>\s*\k<component>\s*,?\s*\)\s*,?\s*\}\s*,?/gu;
const PUBLIC_MARKET_ROUTES_PATTERN = /export\s+const\s+PUBLIC_MARKET_ROUTES\s*:\s*Routes\s*=\s*\[([\s\S]*?)\]\s*;/u;

const ROUTE_SOURCE_PATHS = new Set([
  PATHS.seam,
  PATHS.development,
  PATHS.production,
  'frontend/src/app/features/sports/operations/sports-operations.routes.ts',
]);

const HOST_SOURCE_PATHS = Object.freeze([
  PATHS.main,
  PATHS.config,
  PATHS.seam,
  PATHS.profileGuard,
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

const REGEX_PRECEDING_EXPRESSION_KEYWORDS = new Set([
  'await',
  'case',
  'default',
  'delete',
  'do',
  'else',
  'in',
  'instanceof',
  'new',
  'return',
  'throw',
  'typeof',
  'void',
  'yield',
]);

function isRegexStart(result) {
  let cursor = result.length - 1;
  while (cursor >= 0 && /\s/u.test(result[cursor])) {
    cursor -= 1;
  }
  if (cursor < 0) {
    return true;
  }

  const char = result[cursor];

  if ('([{:;,!?=~&|^<>*%'.includes(char)) {
    return true;
  }

  if (char === '+') {
    return cursor === 0 || result[cursor - 1] !== '+';
  }
  if (char === '-') {
    return cursor === 0 || result[cursor - 1] !== '-';
  }

  if (char === '}') {
    return true;
  }

  if (char === ')') {
    let depth = 1;
    let openIndex = cursor - 1;
    while (openIndex >= 0 && depth > 0) {
      if (result[openIndex] === ')') depth += 1;
      else if (result[openIndex] === '(') depth -= 1;
      openIndex -= 1;
    }
    if (depth === 0) {
      while (openIndex >= 0 && /\s/u.test(result[openIndex])) {
        openIndex -= 1;
      }
      if (openIndex >= 0) {
        let wordEnd = openIndex;
        let wordStart = wordEnd;
        while (wordStart >= 0 && /[\p{ID_Continue}$]/u.test(result[wordStart])) {
          wordStart -= 1;
        }
        const headerWord = result.slice(wordStart + 1, wordEnd + 1);
        let beforeHeader = wordStart;
        while (beforeHeader >= 0 && /\s/u.test(result[beforeHeader])) {
          beforeHeader -= 1;
        }
        if (beforeHeader >= 0 && result[beforeHeader] === '.') {
          return false;
        }
        if (['if', 'while', 'for', 'with'].includes(headerWord)) {
          return true;
        }
      }
    }
    return false;
  }

  if (/[\p{ID_Continue}$]/u.test(char)) {
    let wordStart = cursor;
    while (wordStart >= 0 && /[\p{ID_Continue}$]/u.test(result[wordStart])) {
      wordStart -= 1;
    }
    const word = result.slice(wordStart + 1, cursor + 1);
    let beforeWord = wordStart;
    while (beforeWord >= 0 && /\s/u.test(result[beforeWord])) {
      beforeWord -= 1;
    }
    if (beforeWord >= 0 && result[beforeWord] === '.') {
      return false;
    }

    return REGEX_PRECEDING_EXPRESSION_KEYWORDS.has(word);
  }

  return false;
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

    if (context.type === 'regex') {
      if (current === '\\' && next !== undefined) {
        result += maskLiterals ? `${maskCharacter(current)}${maskCharacter(next)}` : `${current}${next}`;
        index += 1;
      } else if (current === '[' && !context.inClass) {
        context.inClass = true;
        result += maskLiterals ? maskCharacter(current) : current;
      } else if (current === ']' && context.inClass) {
        context.inClass = false;
        result += maskLiterals ? maskCharacter(current) : current;
      } else if (current === '/' && !context.inClass) {
        result += current;
        contexts.pop();
      } else if (current === '\n') {
        result += '\n';
        contexts.pop();
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

    if (current === '/' && isRegexStart(result)) {
      result += current;
      contexts.push({ type: 'regex', inClass: false });
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

function approvedDynamicImportsFor(relativePath) {
  if (relativePath === PATHS.seam) {
    return [
      ...APPROVED_PUBLIC_ROUTES,
      ...APPROVED_ODDS_OPERATION_ROUTES,
      ...APPROVED_ROOT_STATE_ROUTES,
      ...APPROVED_PHASE_SIX_ROUTES,
      approvedRoute('comunidade', './features/chat/chat-panel.component', 'CommunityPageComponent'),
    ];
  }
  if (relativePath === PATHS.development) {
    return [
      ...APPROVED_PUBLIC_MARKET_ROUTES,
      ...APPROVED_CONSOLE_ROUTES,
      ...APPROVED_PRODUCT_ROUTES,
      ...APPROVED_TECHNICAL_ROUTES,
    ];
  }
  if (relativePath === PATHS.production) {
    return [
      ...APPROVED_PUBLIC_MARKET_ROUTES,
      ...APPROVED_CONSOLE_ROUTES,
      ...APPROVED_PRODUCT_ROUTES,
    ];
  }
  if (relativePath === 'frontend/src/app/features/sports/operations/sports-operations.routes.ts') {
    return APPROVED_SPORTS_OPERATION_IMPORTS;
  }
  return null;
}

function validateApprovedDynamicImports(relativePath, code, executableCode) {
  const approvedRoutes = approvedDynamicImportsFor(relativePath);
  // TypeScript's `import()` type references are not runtime lazy imports.
  const dynamicImportCount = countMatches(executableCode, /\bimport\s*\(/u)
    - countMatches(code, /:\s*import\s*\([^)]*\)/u);

  if (approvedRoutes === null) {
    expect(
      dynamicImportCount === 0,
      'DYNAMIC_IMPORT_FORBIDDEN',
      `${relativePath} nao pode usar import dinamico fora das rotas lazy aprovadas.`,
    );
    return;
  }

  const limitsByModule = new Map();
  for (const route of approvedRoutes) {
    const current = limitsByModule.get(route.modulePath);
    limitsByModule.set(route.modulePath, {
      route,
      limit: (current?.limit ?? 0) + 1,
    });
  }

  let approvedImportCount = 0;
  let withinLimits = true;
  for (const { route, limit } of limitsByModule.values()) {
    const count = countActiveImports(code, route.dynamicImportPattern);
    approvedImportCount += count;
    withinLimits = withinLimits && count <= limit;
  }

  expect(
    withinLimits && dynamicImportCount === approvedImportCount,
    'DYNAMIC_IMPORT_FORBIDDEN',
    `${relativePath} aceita somente imports dinamicos literais das rotas lazy aprovadas.`,
  );
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
    validateApprovedDynamicImports(relativePath, code, executableCode);
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

  return {
    manifest,
    sources: new Map(manifest.entries.map(({ path, bytes }) => [
      `${PATHS.generatedRoot}/${path}`,
      normalizeText(bytes.toString('utf8')),
    ])),
  };
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
    // Domain authorization DTO imports are not authentication header activation.
    const headerInspectedSource = activeSource.replace(
      /from\s*'\.\.\/model\/settlement-authorization-(?:candidate-page|request|response)'/gu,
      "from 'approved-settlement-model'",
    );
    const executableSource = maskScriptStrings(activeSource);
    expect(
      !/\[\s*(['"])(?:accessToken|apiKeys|credentials|username|password)\1\s*\]/u.test(activeSource),
      'GENERATED_AUTH_CONFIGURATION',
      `${relativePath} nao pode acessar credencial manual por chave literal computada.`,
    );
    for (const forbiddenPattern of GENERATED_ACTIVE_AUTH_PATTERNS) {
      // Nomes de credencial sao proibidos como identificadores executaveis, mas podem fazer
      // parte de um path relativo legitimo, como /password-reset-events. Authorization,
      // Bearer e Cookie continuam sendo procurados tambem em strings, pois sao nomes/valores
      // de header cuja ativacao manual precisa ser recusada.
      const inspectedSource = forbiddenPattern.rule === 'GENERATED_AUTH_CONFIGURATION'
        ? executableSource
        : headerInspectedSource;
      expect(
        !forbiddenPattern.pattern.test(inspectedSource),
        forbiddenPattern.rule,
        `${forbiddenPattern.label} nao pode ficar ativa no cliente gerado (${relativePath}).`,
      );
    }

    if (/\bwithCredentials\b/u.test(activeSource)) {
      // A propriedade protegida e o encadeamento DORMENTE, nao a identidade do arquivo.
      // Ate o Plano 01-26 existia um unico servico gerado, e este guard prendia
      // identity.service.ts com exatamente 3 ocorrencias. A regeneracao que documentou
      // sessao, logout e diagnostico criou mais servicos com o mesmo padrao dormente e
      // derrubou o guard sem que nenhuma propriedade tivesse sido violada.
      //
      // Continua exigido, agora em QUALQUER servico gerado: destructuring canonico, toda
      // ocorrencia contida no spread dormente (1 do destructuring mais 2 por spread) e a
      // proibicao absoluta da forma forcada.
      const dormantSpreads = countMatches(activeSource, /\.\.\.\(withCredentials\s*\?\s*\{\s*withCredentials\s*\}\s*:\s*\{\s*\}\s*\)/u);
      expect(
        dormantSpreads >= 1
          && countMatches(activeSource, /\bwithCredentials\b/u) === 3 * dormantSpreads
          && countMatches(activeSource, /const\s*\{\s*basePath\s*,\s*withCredentials\s*\}\s*=\s*this\.configuration\s*;/u) === dormantSpreads
          && !/withCredentials\s*:\s*true\b/u.test(activeSource),
        'GENERATED_WITH_CREDENTIALS',
        'withCredentials gerado deve permanecer apenas no encadeamento dormente aprovado.',
      );
    }
  }
}

function validateGeneratedManifest(manifest) {
  expect(
    isDeepStrictEqual(manifest.files, APPROVED_GENERATED_FILES),
    'GENERATED_MANIFEST_PATHS',
    `a arvore gerada deve conter exatamente os ${APPROVED_GENERATED_FILES.length} caminhos aprovados, na ordem canonica.`,
  );
  expect(
    manifest.digest === APPROVED_GENERATED_DIGEST,
    'GENERATED_MANIFEST_DIGEST',
    `a arvore gerada deve possuir o digest canonico ${APPROVED_GENERATED_DIGEST}.`,
  );
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

function parseLazyRoutes(children, rule, environmentLabel) {
  const routeMatches = [...children.matchAll(LAZY_CHILD_ROUTE_PATTERN)];
  const unparsedChildren = children.replace(LAZY_CHILD_ROUTE_PATTERN, '').trim();
  expect(
    unparsedChildren === '',
    rule,
    `${environmentLabel} aceita somente filhos canonicos carregados sob demanda.`,
  );

  return routeMatches.map((routeMatch) => Object.freeze({
    path: routeMatch.groups.path,
    modulePath: routeMatch.groups.modulePath,
    component: routeMatch.groups.component,
    pathMatch: routeMatch.groups.pathMatch ?? null,
  }));
}

function parseLooseLazyRoutes(source) {
  return [...source.matchAll(LAZY_CHILD_ROUTE_PATTERN)].map((routeMatch) => Object.freeze({
    path: routeMatch.groups.path,
    modulePath: routeMatch.groups.modulePath,
    component: routeMatch.groups.component,
    pathMatch: routeMatch.groups.pathMatch ?? null,
  }));
}

function parsePublicMarketRoutes(source, rule, environmentLabel) {
  const match = PUBLIC_MARKET_ROUTES_PATTERN.exec(source);
  expect(
    match !== null,
    rule,
    `${environmentLabel} deve exportar PUBLIC_MARKET_ROUTES como lista tipada.`,
  );

  return parseLazyRoutes(
    match[1],
    rule,
    `${environmentLabel}/rotas publicas de odds`,
  );
}

function parseBuildSpecificRoutes(source, rule, environmentLabel) {
  if (!/AuthenticatedLayoutComponent/u.test(source)) {
    const staticImports = [...source.matchAll(/^\s*import\s+(?!\()/gmu)].map((match) => match[0]);
    expect(
      staticImports.length === 2
        && /^\s*import\s+type\s*\{\s*Routes\s*\}\s*from\s*['"]@angular\/router['"]\s*;/mu.test(source)
        && /^\s*import\s*\{\s*ConsoleShellComponent\s*\}\s*from\s*['"]\.\/layout\/console-shell\.component['"]\s*;/mu.test(source),
      environmentLabel === 'production' ? 'PRODUCTION_AUTHENTICATED_LAYOUT' : 'DEVELOPMENT_ROUTES_SHAPE',
      `${environmentLabel} deve importar somente Routes e ConsoleShellComponent estaticamente.`,
    );
    const consoleMatch = source.match(
      /path\s*:\s*'operacao\/esportes'[\s\S]*?children\s*:\s*\[(?<consoleChildren>[\s\S]*?)\]\s*,?\s*\}/u,
    );
    const personalMatch = source.match(
      /path\s*:\s*'conta'[\s\S]*?data\s*:\s*\{\s*consoleVariant\s*:\s*'personal'\s*\}[\s\S]*?children\s*:\s*\[(?<personalChildren>[\s\S]*?)\]\s*,?\s*\}/u,
    );
    const operationalMatch = source.match(
      /path\s*:\s*'operacao'[\s\S]*?data\s*:\s*\{\s*consoleVariant\s*:\s*'operational'\s*\}[\s\S]*?children\s*:\s*\[(?<operationalChildren>[\s\S]*?)\]\s*,?\s*\}/u,
    );
    const athleteMatch = source.match(
      /path\s*:\s*'conta\/perfil\/esportivo'[\s\S]*?data\s*:\s*\{\s*consoleVariant\s*:\s*'personal'\s*\}[\s\S]*?children\s*:\s*\[(?<athleteChildren>[\s\S]*?)\]\s*,?\s*\}/u,
    );
    expect(
      consoleMatch && personalMatch && operationalMatch && athleteMatch,
      rule,
      `${environmentLabel} deve declarar os consoles pessoal e operacional sem o shell legado.`,
    );
    const technicalChildren = source
      .replace(consoleMatch[0], '')
      .replace(personalMatch[0], '')
      .replace(operationalMatch[0], '')
      .replace(athleteMatch[0], '');
    const technicalBodyMatch = technicalChildren.match(
      /export\s+const\s+BUILD_SPECIFIC_ROUTES\s*:\s*Routes\s*=\s*\[([\s\S]*?)\]\s*;/u,
    );
    expect(
      technicalBodyMatch !== null,
      rule,
      `${environmentLabel} deve manter as rotas técnicas na mesma lista de build.`,
    );
    return Object.freeze({
      final: true,
      hybrid: false,
      consoleRoutes: parseLazyRoutes(
        consoleMatch.groups.consoleChildren,
        rule,
        `${environmentLabel}/Console operacional esportivo`,
      ),
      personalRoutes: parseLazyRoutes(
        personalMatch.groups.personalChildren,
        rule,
        `${environmentLabel}/Console pessoal`,
      ),
      personalExtraRoutes: parseLazyRoutes(
        athleteMatch.groups.athleteChildren,
        rule,
        `${environmentLabel}/Console pessoal esportivo`,
      ),
      operationalRoutes: parseLazyRoutes(
        operationalMatch.groups.operationalChildren,
        rule,
        `${environmentLabel}/Console operacional legado`,
      ),
      technicalRoutes: parseLooseLazyRoutes(technicalBodyMatch[1]),
      authenticatedRoutes: [],
    });
  }

  const hybridMatch = BUILD_SPECIFIC_ROUTES_HYBRID_PATTERN.exec(source);
  if (hybridMatch !== null) {
    return Object.freeze({
      hybrid: true,
      final: false,
      personalExtraRoutes: [],
      operationalRoutes: [],
      technicalRoutes: [],
      consoleRoutes: parseLazyRoutes(
        hybridMatch.groups.consoleChildren,
        rule,
        `${environmentLabel}/Console operacional`,
      ),
      personalRoutes: parseLazyRoutes(
        hybridMatch.groups.personalChildren,
        rule,
        `${environmentLabel}/Console pessoal`,
      ),
      authenticatedRoutes: parseLazyRoutes(
        hybridMatch.groups.authenticatedChildren,
        rule,
        `${environmentLabel}/conta legada`,
      ),
    });
  }

  const match = BUILD_SPECIFIC_ROUTES_PATTERN.exec(source);
  expect(
    match !== null,
    rule,
    `${environmentLabel} deve declarar ConsoleShell e AuthenticatedLayout como raizes canonicas.`,
  );

  return Object.freeze({
    hybrid: false,
    final: false,
    personalExtraRoutes: [],
    operationalRoutes: [],
    technicalRoutes: [],
    consoleRoutes: parseLazyRoutes(
      match.groups.consoleChildren,
      rule,
      `${environmentLabel}/Console`,
    ),
    personalRoutes: [],
    authenticatedRoutes: parseLazyRoutes(
      match.groups.authenticatedChildren,
      rule,
      `${environmentLabel}/conta`,
    ),
  });
}

function routeMatchesDescriptor(route, descriptor) {
  return route.path === descriptor.path
    && route.modulePath === descriptor.modulePath
    && route.component === descriptor.component
    && route.pathMatch === descriptor.pathMatch;
}

function expectApprovedRoutes(routes, approvedRoutes, rule, environmentLabel) {
  const uniquePaths = new Set(routes.map((route) => route.path));
  expect(
    uniquePaths.size === routes.length,
    rule,
    `${environmentLabel} nao pode repetir caminhos de rota.`,
  );

  expect(
    routes.every((route) => approvedRoutes.some(
      (descriptor) => routeMatchesDescriptor(route, descriptor),
    )),
    rule,
    `${environmentLabel} contem uma rota fora do conjunto fechado aprovado.`,
  );
}

function expectExactApprovedRoutes(routes, approvedRoutes, rule, environmentLabel) {
  expectApprovedRoutes(routes, approvedRoutes, rule, environmentLabel);
  expect(
    routes.length === approvedRoutes.length
      && approvedRoutes.every((descriptor) => routes.some(
        (route) => routeMatchesDescriptor(route, descriptor),
      )),
    rule,
    `${environmentLabel} deve conter exatamente o conjunto fechado aprovado.`,
  );
}

function normalizePhaseSixRoutes(source) {
  if (!APPROVED_PHASE_SIX_ROUTES.some(route => source.includes(route.modulePath))) return source;
  const guard = /canActivateChild\s*:\s*\[\s*authenticationGuard\s*,\s*profileCompletionGuard\s*\]\s*,\s*children\s*:\s*\[/u.exec(source);
  expect(guard !== null, 'PHASE_SIX_GUARDS', 'Fase 6 privada exige os dois guards existentes.');
  let start = guard.index + guard[0].length;
  let tail = source.slice(start);
  for (const route of APPROVED_PHASE_SIX_ROUTES.slice(1)) {
    const variant = route.path.startsWith('conta/') ? 'personal' : 'operational';
    const modulePath = route.modulePath.replace(/[.*+?^${}()|[\]\\]/gu, '\\$&');
    const pattern = new RegExp(String.raw`^\s*\{\s*path\s*:\s*'${route.path}'\s*,\s*component\s*:\s*ConsoleShellComponent\s*,\s*data\s*:\s*\{\s*consoleVariant\s*:\s*'${variant}'\s*\}\s*,\s*children\s*:\s*\[\s*\{\s*path\s*:\s*''\s*,\s*loadComponent\s*:\s*\(\s*\)\s*=>\s*import\s*\(\s*'${modulePath}'\s*\)\s*\.then\s*\(\s*\(\s*\{\s*${route.component}\s*\}\s*\)\s*=>\s*${route.component}\s*,?\s*\)\s*,?\s*\}\s*,?\s*\]\s*,?\s*\}\s*,`, 'u');
    const match = pattern.exec(tail);
    expect(match !== null, 'PHASE_SIX_ROUTE_SHAPE', 'Cada rota privada da Fase 6 deve usar o shell e o componente aprovado dentro dos guards.');
    tail = tail.slice(match[0].length);
  }
  source = source.slice(0, start) + tail;
  const ranking = /\{\s*path\s*:\s*'ranking'\s*,\s*loadComponent\s*:\s*\(\s*\)\s*=>\s*import\s*\(\s*'\.\/features\/ranking\/ranking-page\.component'\s*\)\s*\.then\s*\(\s*\(\s*\{\s*RankingPageComponent\s*\}\s*\)\s*=>\s*RankingPageComponent\s*,?\s*\)\s*,?\s*\}\s*,/u;
  expect(countMatches(source, ranking) === 1, 'PHASE_SIX_PUBLIC_RANKING', 'Ranking publico deve ser declarado uma unica vez.');
  return source.replace(ranking, '');
}

export function normalizePhaseSevenRoutes(source) {
  const route = /\{\s*path\s*:\s*'comunidade'\s*,\s*loadComponent\s*:\s*\(\s*\)\s*=>\s*import\s*\(\s*'\.\/features\/chat\/chat-panel\.component'\s*\)\s*\.then\s*\(\s*\(\s*\{\s*CommunityPageComponent\s*\}\s*\)\s*=>\s*CommunityPageComponent\s*,?\s*\)\s*,?\s*\}\s*,/u;
  if (!/path\s*:\s*'comunidade'/u.test(source)) return source;
  const matches = [...source.matchAll(new RegExp(route.source, 'gu'))];
  const publicStart = source.search(/component\s*:\s*ArenaShellComponent/u);
  const publicEnd = source.search(/path\s*:\s*'entrar'/u);
  expect(matches.length === 1 && matches[0].index > publicStart && publicStart >= 0
    && publicEnd > matches[0].index, 'PHASE_SEVEN_PUBLIC_COMMUNITY',
    'Comunidade deve usar exatamente a rota e o componente aprovados dentro da Arena publica.');
  return source.replace(route, '');
}

function validateRouteSources(sources) {
  const seam = normalizePhaseSixRoutes(normalizePhaseSevenRoutes(stripScriptComments(sources.get(PATHS.seam))));
  const profileGuard = stripScriptComments(sources.get(PATHS.profileGuard));
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
    /^\s*import\s*\{\s*profileCompletionGuard\s*\}\s*from\s*['"]\.\/core\/profile\/profile-completion\.guard['"]\s*;/mu,
    'PROFILE_GUARD_IMPORT',
    'import da guarda global de completude do perfil',
  );
  expectExactlyOnceActiveImport(
    seam,
    /^\s*import\s*\{\s*authenticationGuard\s*\}\s*from\s*['"]\.\/core\/session\/authentication\.guard['"]\s*;/mu,
    'AUTHENTICATION_GUARD_IMPORT',
    'import da guarda global de autenticacao',
  );
  expectExactlyOnceActiveImport(
    seam,
    /^\s*import\s*\{\s*BUILD_SPECIFIC_ROUTES\s*,\s*PUBLIC_MARKET_ROUTES\s*\}\s*from\s*['"]\.\/app\.routes\.development['"]\s*;/mu,
    'DEVELOPMENT_ROUTE_IMPORT',
    'import das listas development pela seam',
  );
  expectExactlyOnceActiveImport(
    seam,
    /^\s*import\s*\{\s*ArenaShellComponent\s*\}\s*from\s*['"]\.\/layout\/arena-shell\.component['"]\s*;/mu,
    'ARENA_SHELL_IMPORT',
    'import do shell publico da Arena',
  );
  const oddsAppRoutesMatch =
    APP_ROUTES_SESSION_STATES_PATTERN.exec(seam) ?? APP_ROUTES_ODDS_HYBRID_PATTERN.exec(seam);
  const hybridAppRoutesMatch = oddsAppRoutesMatch ?? APP_ROUTES_HYBRID_PATTERN.exec(seam);
  const appRoutesMatch = hybridAppRoutesMatch ?? APP_ROUTES_PATTERN.exec(seam);
  expect(
    appRoutesMatch !== null,
    'PROFILE_GUARD_COMPOSITION',
    'APP_ROUTES deve separar a Arena publica da raiz protegida que envolve BUILD_SPECIFIC_ROUTES.',
  );
  const publicRoutes = parseLazyRoutes(
    appRoutesMatch.groups.publicChildren.replace(
      /^\s*\.\.\.PUBLIC_MARKET_ROUTES\s*,/u,
      '',
    ),
    'PUBLIC_ARENA_ROUTES',
    'Arena publica',
  );
  expect(
    /^\s*\.\.\.PUBLIC_MARKET_ROUTES\s*,/u.test(appRoutesMatch.groups.publicChildren),
    'PUBLIC_ARENA_ROUTES',
    'Arena publica deve espalhar PUBLIC_MARKET_ROUTES antes das demais rotas.',
  );
  expect(
    countMatches(appRoutesMatch.groups.publicChildren, /\.\.\.PUBLIC_MARKET_ROUTES\b/gu) === 1,
    'PUBLIC_ARENA_ROUTES',
    'PUBLIC_MARKET_ROUTES deve ser espalhada uma unica vez na Arena publica.',
  );
  if (oddsAppRoutesMatch !== null) {
    expect(
      oddsAppRoutesMatch.groups.oddsOperationsPath === 'operacao/odds/mercados',
      'APP_ODDS_OPERATION_ROUTES',
      'a seam deve usar o caminho operacional canonico de odds.',
    );
    const oddsOperationRoutes = parseLazyRoutes(
      oddsAppRoutesMatch.groups.oddsOperationsChildren,
      'APP_ODDS_OPERATION_ROUTES',
      'rotas operacionais de odds na seam',
    ).map((route) => ({
      ...route,
      path: route.path === ''
        ? 'operacao/odds/mercados'
        : `operacao/odds/mercados/${route.path}`,
    }));
    expectExactApprovedRoutes(
      oddsOperationRoutes,
      APPROVED_ODDS_OPERATION_ROUTES,
      'APP_ODDS_OPERATION_ROUTES',
      'rotas operacionais de odds na seam',
    );
  }
  expect(
    /const\s+PROFILE_COMPLETION_URL\s*=\s*'\/conta\/perfil\/completar'\s*;/u.test(profileGuard)
      && /const\s+ERROR_URL\s*=\s*'\/error'\s*;/u.test(profileGuard)
      && /const\s+AUTH_TECHNICAL_ROUTE_PREFIXES\s*=\s*Object\.freeze\s*\(\s*\[\s*'\/oauth2\/authorization\/'\s*,\s*'\/login\/oauth2\/code\/'\s*,?\s*\]\s*\)\s*;/u.test(profileGuard),
    'PROFILE_GUARD_EXEMPTION',
    'a guarda deve declarar somente S1 e as rotas tecnicas de autenticacao e erro como excecoes.',
  );
  expect(
    /return\s+\(?\s*path\s*===\s*PROFILE_COMPLETION_URL\s*\|\|\s*path\s*===\s*ERROR_URL\s*\|\|\s*AUTH_TECHNICAL_ROUTE_PREFIXES\.some\s*\(\s*\(\s*prefix\s*\)\s*=>\s*path\.startsWith\s*\(\s*prefix\s*\)\s*\)\s*\)?\s*;/u.test(profileGuard)
      && /if\s*\(\s*isExemptRoute\s*\(\s*state\.url\s*\)\s*\)\s*\{\s*return\s+true\s*;\s*\}/u.test(profileGuard),
    'PROFILE_GUARD_EXEMPTION',
    'a guarda deve liberar S1 e rotas tecnicas antes de consultar o perfil, evitando loop.',
  );
  expect(
    /if\s*\(\s*profileStore\.status\s*\(\s*\)\s*===\s*'initial'\s*\)\s*\{\s*await\s+profileStore\.loadProfile\s*\(\s*\)\s*;\s*\}/u.test(profileGuard)
      && /return\s+profileStore\.hasProfile\s*\(\s*\)\s*\?\s*true\s*:\s*router\.createUrlTree\s*\(\s*\[\s*PROFILE_COMPLETION_URL\s*\]\s*\)\s*;/u.test(profileGuard),
    'PROFILE_GUARD_DECISION',
    'a guarda deve carregar o perfil inicial e falhar fechado por UrlTree quando ele nao estiver completo.',
  );
  expectExactlyOnce(
    maskScriptStrings(development),
    /\bexport\s+const\s+BUILD_SPECIFIC_ROUTES\s*:\s*Routes\s*=\s*\[/u,
    'DEVELOPMENT_ROUTES_EXPORT',
    'export BUILD_SPECIFIC_ROUTES: Routes em development',
  );
  const productionExecutable = maskScriptStrings(production);
  expect(
    countMatches(
      productionExecutable,
      /\bexport\s+const\s+BUILD_SPECIFIC_ROUTES\s*:\s*Routes\s*=\s*\[/u,
    ) === 1
      && countMatches(productionExecutable, /\bBUILD_SPECIFIC_ROUTES\b/u) === 1,
    'PRODUCTION_ROUTES_UNIQUE',
    'production deve exportar o simbolo BUILD_SPECIFIC_ROUTES tipado exatamente uma vez.',
  );

  const productionRouteGroups = parseBuildSpecificRoutes(
    production,
    'PRODUCTION_AUTHENTICATED_LAYOUT',
    'production',
  );
  const productionRoutes = productionRouteGroups.hybrid
    ? [
      ...productionRouteGroups.personalRoutes.map((route) => ({
        ...route,
        path: `conta/${route.path}`,
      })),
      ...productionRouteGroups.authenticatedRoutes,
    ]
    : productionRouteGroups.final
      ? [
        ...productionRouteGroups.personalRoutes.map((route) => ({
          ...route,
          path: `conta/${route.path}`,
        })),
        ...productionRouteGroups.personalExtraRoutes.map((route) => ({
          ...route,
          path: 'conta/perfil/esportivo',
        })),
        ...productionRouteGroups.operationalRoutes.map((route) => ({
          ...route,
          path: `operacao/${route.path}`,
        })),
      ]
    : productionRouteGroups.authenticatedRoutes;
  expect(productionRoutes.length > 0, 'PRODUCTION_ROUTES_ALLOWLIST', 'production deve publicar ao menos uma rota de produto aprovada.');
  if (productionRouteGroups.final) {
    expectExactApprovedRoutes(
      productionRouteGroups.personalRoutes,
      APPROVED_PERSONAL_ROUTES,
      'PRODUCTION_PERSONAL_ROUTES',
      'Console pessoal production',
    );
    expectExactApprovedRoutes(
      productionRouteGroups.personalExtraRoutes,
      APPROVED_PERSONAL_EXTRA_ROUTES,
      'PRODUCTION_PERSONAL_EXTRA_ROUTES',
      'Console pessoal esportivo production',
    );
    expectExactApprovedRoutes(
      productionRouteGroups.operationalRoutes,
      APPROVED_LEGACY_OPERATIONAL_ROUTES,
      'PRODUCTION_OPERATIONAL_ROUTES',
      'Console operacional legado production',
    );
    expectExactApprovedRoutes(
      productionRoutes,
      APPROVED_PRODUCT_ROUTES,
      'PRODUCTION_ROUTES_ALLOWLIST',
      'production',
    );
  } else if (productionRouteGroups.hybrid) {
    expectExactApprovedRoutes(
      productionRouteGroups.personalRoutes,
      APPROVED_PERSONAL_ROUTES,
      'PRODUCTION_PERSONAL_ROUTES',
      'Console pessoal production',
    );
    expectApprovedRoutes(
      productionRouteGroups.authenticatedRoutes,
      APPROVED_LEGACY_AUTHENTICATED_ROUTES,
      'PRODUCTION_ROUTES_ALLOWLIST',
      'production/conta legada',
    );
  } else {
    expectApprovedRoutes(
      productionRoutes,
      APPROVED_PRODUCT_ROUTES,
      'PRODUCTION_ROUTES_ALLOWLIST',
      'production',
    );
  }
  expectExactApprovedRoutes(
    productionRouteGroups.consoleRoutes,
    APPROVED_CONSOLE_ROUTES,
    'PRODUCTION_CONSOLE_ROUTES',
    'Console production',
  );

  const developmentRouteGroups = parseBuildSpecificRoutes(
    development,
    'DEVELOPMENT_ROUTES_SHAPE',
    'development',
  );

  expectExactApprovedRoutes(
    [...APPROVED_PUBLIC_MARKET_ROUTES, ...publicRoutes],
    hybridAppRoutesMatch === null
      ? [...APPROVED_PUBLIC_MARKET_ROUTES, APPROVED_PUBLIC_ROUTES[0]]
      : [...APPROVED_PUBLIC_MARKET_ROUTES, ...APPROVED_PUBLIC_ROUTES],
    'PUBLIC_ARENA_ROUTES',
    'Arena publica',
  );
  expectExactApprovedRoutes(
    parsePublicMarketRoutes(development, 'DEVELOPMENT_PUBLIC_MARKET_ROUTES', 'development'),
    APPROVED_PUBLIC_MARKET_ROUTES,
    'DEVELOPMENT_PUBLIC_MARKET_ROUTES',
    'development',
  );
  expectExactApprovedRoutes(
    parsePublicMarketRoutes(production, 'PRODUCTION_PUBLIC_MARKET_ROUTES', 'production'),
    APPROVED_PUBLIC_MARKET_ROUTES,
    'PRODUCTION_PUBLIC_MARKET_ROUTES',
    'production',
  );
  const developmentRoutes = developmentRouteGroups.hybrid
    ? [
      ...developmentRouteGroups.personalRoutes.map((route) => ({
        ...route,
        path: `conta/${route.path}`,
      })),
      ...developmentRouteGroups.authenticatedRoutes,
    ]
    : developmentRouteGroups.final
      ? [
        ...developmentRouteGroups.personalRoutes.map((route) => ({
          ...route,
          path: `conta/${route.path}`,
        })),
        ...developmentRouteGroups.personalExtraRoutes.map((route) => ({
          ...route,
          path: 'conta/perfil/esportivo',
        })),
        ...developmentRouteGroups.operationalRoutes.map((route) => ({
          ...route,
          path: `operacao/${route.path}`,
        })),
      ]
    : developmentRouteGroups.authenticatedRoutes;
  if (developmentRouteGroups.final) {
    expectExactApprovedRoutes(
      developmentRouteGroups.personalRoutes,
      APPROVED_PERSONAL_ROUTES,
      'DEVELOPMENT_PERSONAL_ROUTES',
      'Console pessoal development',
    );
    expectExactApprovedRoutes(
      developmentRouteGroups.personalExtraRoutes,
      APPROVED_PERSONAL_EXTRA_ROUTES,
      'DEVELOPMENT_PERSONAL_EXTRA_ROUTES',
      'Console pessoal esportivo development',
    );
    expectExactApprovedRoutes(
      developmentRouteGroups.operationalRoutes,
      APPROVED_LEGACY_OPERATIONAL_ROUTES,
      'DEVELOPMENT_OPERATIONAL_ROUTES',
      'Console operacional legado development',
    );
    expectApprovedRoutes(
      developmentRouteGroups.technicalRoutes,
      APPROVED_TECHNICAL_ROUTES,
      'DEVELOPMENT_TECHNICAL_ROUTES',
      'rotas técnicas development',
    );
    expectExactApprovedRoutes(
      developmentRoutes,
      APPROVED_PRODUCT_ROUTES,
      'DEVELOPMENT_ROUTES_ALLOWLIST',
      'development',
    );
  } else if (developmentRouteGroups.hybrid) {
    expectExactApprovedRoutes(
      developmentRouteGroups.personalRoutes,
      APPROVED_PERSONAL_ROUTES,
      'DEVELOPMENT_PERSONAL_ROUTES',
      'Console pessoal development',
    );
    expectApprovedRoutes(
      developmentRouteGroups.authenticatedRoutes,
      [...APPROVED_LEGACY_AUTHENTICATED_ROUTES, ...APPROVED_TECHNICAL_ROUTES],
      'DEVELOPMENT_ROUTES_ALLOWLIST',
      'development/conta legada',
    );
  } else {
    expectApprovedRoutes(
      developmentRoutes,
      [...APPROVED_PRODUCT_ROUTES, ...APPROVED_TECHNICAL_ROUTES],
      'DEVELOPMENT_ROUTES_ALLOWLIST',
      'development',
    );
  }
  expectExactApprovedRoutes(
    developmentRouteGroups.consoleRoutes,
    APPROVED_CONSOLE_ROUTES,
    'DEVELOPMENT_CONSOLE_ROUTES',
    'Console development',
  );
  expect(
    productionRoutes.every((productionRoute) => developmentRoutes.some(
      (developmentRoute) => routeMatchesDescriptor(developmentRoute, productionRoute),
    )),
    'DEVELOPMENT_ROUTES_ALLOWLIST',
    'development deve conter todas as rotas publicadas em production.',
  );
  expect(
    !/(?:\btracer\b|\bTracerPageComponent\b|\bdiagnostic\b|\bDiagnosticPageComponent\b)/iu.test(production),
    'PRODUCTION_TRACER_FORBIDDEN',
    'production nao pode importar, mencionar nem expor o tracer ou o diagnostico.',
  );
  if (productionRouteGroups.final && developmentRouteGroups.final) {
    expect(
      !/AuthenticatedLayoutComponent|authenticated-layout\.component/u.test(production)
        && !/AuthenticatedLayoutComponent|authenticated-layout\.component/u.test(development),
      'AUTHENTICATED_LAYOUT_RETIRED',
      'development e production nao podem referenciar o AuthenticatedLayoutComponent apos o corte.',
    );
  }
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
    const expectedPublicMarketRoutesCount = relativePath === PATHS.seam
      ? 2
      : Number(relativePath === PATHS.development || relativePath === PATHS.production);
    expect(
      countMatches(executableSource, /\bPUBLIC_MARKET_ROUTES\b/u)
        === expectedPublicMarketRoutesCount,
      'PUBLIC_MARKET_ROUTES_USAGE',
      'PUBLIC_MARKET_ROUTES deve aparecer somente nas declaracoes de ambiente, no import e no spread da seam.',
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
      const inspectedSource = (
        relativePath === PATHS.profileGuard
        && forbiddenPattern.rule === 'AUTHORIZATION_HEADER'
      )
        ? activeSource.replaceAll('/oauth2/authorization/', '')
        : activeSource;
      expect(
        !forbiddenPattern.pattern.test(inspectedSource),
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

    if (relativePath.endsWith('.ts') && !ROUTE_SOURCE_PATHS.has(relativePath)) {
      const typeOnlyImportsRemoved = activeSource.replace(/:\s*import\s*\([^)]*\)/gu, '');
      expect(
        countMatches(maskScriptStrings(typeOnlyImportsRemoved), /\bimport\s*\(/u) === 0,
        'DYNAMIC_IMPORT_FORBIDDEN',
        `${relativePath} nao pode usar import dinamico fora das rotas lazy canonicas.`,
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
  const { manifest: generatedManifest, sources: generatedSources } =
    await readGeneratedTypeScriptSources(resolvedProjectRoot);
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
  validateGeneratedManifest(generatedManifest);

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
