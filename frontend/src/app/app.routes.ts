import type { Routes } from '@angular/router';

import { profileCompletionGuard } from './core/profile/profile-completion.guard';
import { authenticationGuard } from './core/session/authentication.guard';
import { BUILD_SPECIFIC_ROUTES, PUBLIC_MARKET_ROUTES } from './app.routes.development';
import { ArenaShellComponent } from './layout/arena-shell.component';
import { ConsoleShellComponent } from './layout/console-shell.component';
import { sportsOperationsRoutes } from './features/sports/operations/sports-operations.routes';

// Build-specific routes include both the legacy personal console and the scoped sports console.

export const APP_ROUTES: Routes = [
  {
    path: '',
    component: ArenaShellComponent,
    children: [
      ...PUBLIC_MARKET_ROUTES,
      {
        path: 'comunidade',
        loadComponent: () =>
          import('./features/chat/chat-panel.component').then(
            ({ CommunityPageComponent }) => CommunityPageComponent,
          ),
      },
      {
        path: 'ranking',
        loadComponent: () =>
          import('./features/ranking/ranking-page.component').then(
            ({ RankingPageComponent }) => RankingPageComponent,
          ),
      },
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () =>
          import('./features/sports/public-agenda/public-agenda-page.component').then(
            ({ PublicAgendaPageComponent }) => PublicAgendaPageComponent,
          ),
      },
      {
        path: 'arena',
        loadComponent: () =>
          import('./features/sports/public-agenda/public-agenda-page.component').then(
            ({ PublicAgendaPageComponent }) => PublicAgendaPageComponent,
          ),
      },
      {
        path: 'competicoes/:competitionId',
        loadComponent: () =>
          import('./features/sports/public-competition/public-competition-page.component').then(
            ({ PublicCompetitionPageComponent }) => PublicCompetitionPageComponent,
          ),
      },
      {
        path: 'eventos/:eventId',
        loadComponent: () =>
          import('./features/sports/public-event/public-event-page.component').then(
            ({ PublicEventPageComponent }) => PublicEventPageComponent,
          ),
      },
      {
        path: 'organizacoes/:organizationId',
        loadComponent: () =>
          import('./features/sports/public-organization/public-organization-page.component').then(
            ({ PublicOrganizationPageComponent }) => PublicOrganizationPageComponent,
          ),
      },
      {
        path: 'equipes/:teamId',
        loadComponent: () =>
          import('./features/sports/public-team/public-team-page.component').then(
            ({ PublicTeamPageComponent }) => PublicTeamPageComponent,
          ),
      },
      {
        path: 'atletas/:athleteId',
        loadComponent: () =>
          import('./features/sports/public-athlete/public-athlete-page.component').then(
            ({ PublicAthletePageComponent }) => PublicAthletePageComponent,
          ),
      },
      {
        path: 'chaveamentos/:bracketId',
        loadComponent: () =>
          import('./features/sports/public-bracket/public-bracket-page.component').then(
            ({ PublicBracketPageComponent }) => PublicBracketPageComponent,
          ),
      },
      {
        path: 'classificacoes/:standingsId',
        loadComponent: () =>
          import('./features/sports/public-standings/public-standings-page.component').then(
            ({ PublicStandingsPageComponent }) => PublicStandingsPageComponent,
          ),
      },
    ],
  },
  {
    path: 'entrar',
    loadComponent: () =>
      import('./features/session/login-required-page.component').then(
        ({ LoginRequiredPageComponent }) => LoginRequiredPageComponent,
      ),
  },
  {
    path: '',
    canActivateChild: [authenticationGuard, profileCompletionGuard],
    children: [
      {
        path: 'conta/historico',
        component: ConsoleShellComponent,
        data: { consoleVariant: 'personal' },
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/settlement/settlement-history-page.component').then(
                ({ SettlementHistoryPageComponent }) => SettlementHistoryPageComponent,
              ),
          },
        ],
      },
      {
        path: 'conta/grupos',
        component: ConsoleShellComponent,
        data: { consoleVariant: 'personal' },
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/ranking/group-page.component').then(
                ({ GroupPageComponent }) => GroupPageComponent,
              ),
          },
        ],
      },
      {
        path: 'conta/conquistas',
        component: ConsoleShellComponent,
        data: { consoleVariant: 'personal' },
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/ranking/achievement-list.component').then(
                ({ AchievementListComponent }) => AchievementListComponent,
              ),
          },
        ],
      },
      {
        path: 'operacao/liquidacoes',
        component: ConsoleShellComponent,
        data: { consoleVariant: 'operational' },
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/settlement/settlement-operations-page.component').then(
                ({ SettlementOperationsPageComponent }) => SettlementOperationsPageComponent,
              ),
          },
        ],
      },
      ...sportsOperationsRoutes,
      ...BUILD_SPECIFIC_ROUTES,
      {
        path: 'operacao/odds/mercados',
        component: ConsoleShellComponent,
        data: { consoleVariant: 'operational' },
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/odds/operations/odds-operations-page.component').then(
                ({ OddsOperationsPageComponent }) => OddsOperationsPageComponent,
              ),
          },
          {
            path: ':marketId',
            loadComponent: () =>
              import('./features/odds/operations/odds-operations-page.component').then(
                ({ OddsOperationsPageComponent }) => OddsOperationsPageComponent,
              ),
          },
        ],
      },
    ],
  },
  {
    path: '**',
    loadComponent: () =>
      import('./features/errors/not-found-page.component').then(
        ({ NotFoundPageComponent }) => NotFoundPageComponent,
      ),
  },
];
