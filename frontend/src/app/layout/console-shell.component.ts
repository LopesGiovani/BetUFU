import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  ViewChild,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  ActivatedRoute,
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { filter, firstValueFrom } from 'rxjs';
import { SessionStore } from '../core/session/session.store';
import { OperationsApiAdapter } from '../features/operations/operations-api.adapter';
import {
  OperationalCapabilityName,
  OperationalNavigationItem,
} from '../features/operations/operations.models';

const SPORTS_OPERATION_DESTINATIONS: readonly OperationalNavigationItem[] = Object.freeze([
  Object.freeze({
    capability: 'AUTHORIZE_SETTLEMENT',
    label: 'Resultados para autorizar',
    path: '/operacao/liquidacoes',
  }),
  Object.freeze({
    capability: 'VIEW_OPERATIONAL_PANEL',
    label: 'Painel operacional',
    path: '/operacao/painel',
  }),
  Object.freeze({
    capability: 'MANAGE_ROLES',
    label: 'Papéis operacionais',
    path: '/operacao/papeis',
  }),
  Object.freeze({
    capability: 'CORRECT_ENROLLMENT',
    label: 'Corrigir matrícula',
    path: '/operacao/matriculas/corrigir',
  }),
  Object.freeze({
    capability: 'MANAGE_SPORTS_CATALOG',
    label: 'Visão geral',
    path: '/operacao/esportes',
  }),
  Object.freeze({
    capability: 'MANAGE_SPORTS_CATALOG',
    label: 'Catálogo',
    path: '/operacao/esportes/catalogo',
  }),
  Object.freeze({
    capability: 'MANAGE_SPORTS_CATALOG',
    label: 'Organizações e equipes',
    path: '/operacao/esportes/organizacoes',
  }),
  Object.freeze({
    capability: 'MANAGE_SPORTS_CATALOG',
    label: 'Atletas e elencos',
    path: '/operacao/esportes/atletas',
  }),
  Object.freeze({
    capability: 'MANAGE_SPORTS_CATALOG',
    label: 'Importar CSV/XLSX',
    path: '/operacao/esportes/importacao',
  }),
  Object.freeze({
    capability: 'MANAGE_SPORTS_CATALOG',
    label: 'Eventos e resultados',
    path: '/operacao/esportes/eventos',
  }),
  Object.freeze({
    capability: 'MANAGE_SPORTS_CATALOG',
    label: 'Chaveamento e classificação',
    path: '/operacao/esportes/chaveamentos',
  }),
  Object.freeze({
    capability: 'MANAGE_SPORTS_CATALOG',
    label: 'Revisões de vínculo',
    path: '/operacao/esportes/revisoes',
  }),
  Object.freeze({
    capability: 'VIEW_ODDS_MARKET',
    label: 'Mercados e odds',
    path: '/operacao/odds/mercados',
  }),
]);

const PERSONAL_DESTINATIONS = Object.freeze([
  Object.freeze({ label: 'Privacidade', path: '/conta/privacidade' }),
  Object.freeze({ label: 'Punicoes da conta', path: '/conta/punicoes' }),
  Object.freeze({ label: 'Moderacao conforme seu escopo', path: '/operacao/comunidade/moderacao' }),
  Object.freeze({ label: 'Historico de liquidacoes', path: '/conta/historico' }),
  Object.freeze({ label: 'Meus grupos', path: '/conta/grupos' }),
  Object.freeze({ label: 'Conquistas', path: '/conta/conquistas' }),
  Object.freeze({ label: 'Meu perfil', path: '/conta/perfil' }),
  Object.freeze({ label: 'Matrícula UFU', path: '/conta/matricula' }),
  Object.freeze({ label: 'Vínculo esportivo', path: '/conta/vinculo-atleta' }),
]);

export type ConsoleShellVariant = 'operational' | 'personal';

@Component({
  selector: 'app-console-shell',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './console-shell.component.html',
  styleUrl: './console-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConsoleShellComponent implements OnInit {
  @ViewChild('consoleMain') private consoleMain?: ElementRef<HTMLElement>;

  readonly sessionStore = inject(SessionStore);
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly operationsApiAdapter = inject(OperationsApiAdapter);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly capabilities = signal<readonly OperationalCapabilityName[]>([]);
  readonly menuOpen = signal(false);
  readonly variant: ConsoleShellVariant =
    this.activatedRoute.snapshot.data['consoleVariant'] === 'personal' ? 'personal' : 'operational';
  readonly isPersonal = this.variant === 'personal';
  readonly operationDestinations = computed(() =>
    this.isPersonal
      ? []
      : SPORTS_OPERATION_DESTINATIONS.filter((destination) =>
          this.capabilities().includes(destination.capability),
        ),
  );
  readonly personalDestinations = PERSONAL_DESTINATIONS;

  ngOnInit(): void {
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => {
        this.closeMenu();
        setTimeout(() => this.consoleMain?.nativeElement.focus());
      });
    void this.sessionStore.loadSession();
    if (!this.isPersonal) {
      void this.loadCapabilities();
    }
  }

  toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  closeMenu(): void {
    this.menuOpen.set(false);
  }

  onMenuTriggerKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      if (this.menuOpen()) {
        this.closeMenu();
        (event.currentTarget as HTMLElement | null)?.focus();
      }
      return;
    }

    if (event.key !== 'Enter' && event.key !== ' ') {
      return;
    }
    event.preventDefault();
    this.toggleMenu();
  }

  onMenuKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Escape') {
      return;
    }
    event.preventDefault();
    this.closeMenu();
    document.querySelector<HTMLElement>('.console-nav__account')?.focus();
  }

  async logout(): Promise<void> {
    this.closeMenu();
    const result = await this.sessionStore.logoutCurrentDevice();
    if (result?.endSessionUrl && /^(https?:\/\/|\/)/u.test(result.endSessionUrl)) {
      window.location.href = result.endSessionUrl;
    }
  }

  private async loadCapabilities(): Promise<void> {
    try {
      this.capabilities.set(
        await firstValueFrom(this.operationsApiAdapter.fetchOperationalCapabilities()),
      );
    } catch {
      this.capabilities.set([]);
    }
  }
}
