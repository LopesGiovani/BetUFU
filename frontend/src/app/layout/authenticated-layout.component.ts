import {
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  ViewChild,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { Subscription, filter } from 'rxjs';
import { SessionStore } from '../core/session/session.store';
import { OperationsApiAdapter } from '../features/operations/operations-api.adapter';
import {
  OperationalCapabilityName,
  OperationalNavigationItem,
} from '../features/operations/operations.models';

const PROFILE_COMPLETION_PATH = '/conta/perfil/completar';
const OPERATION_DESTINATIONS: readonly OperationalNavigationItem[] = Object.freeze([
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
]);

@Component({
  selector: 'app-authenticated-layout',
  standalone: true,
  imports: [RouterLink, RouterOutlet],
  templateUrl: './authenticated-layout.component.html',
  styleUrl: './authenticated-layout.component.scss',
})
export class AuthenticatedLayoutComponent implements OnInit {
  @ViewChild('accountMenuTrigger')
  private accountMenuTrigger?: ElementRef<HTMLButtonElement>;

  readonly sessionStore = inject(SessionStore);
  private readonly operationsApiAdapter = inject(OperationsApiAdapter);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private readonly currentPath = signal(this.routePath(this.router.url));
  private readonly capabilities = signal<readonly OperationalCapabilityName[]>([]);
  private capabilityRequestStarted = false;
  private capabilitySubscription?: Subscription;

  readonly menuOpen = signal(false);
  readonly isProfileCompletionRoute = computed(
    () => this.currentPath() === PROFILE_COMPLETION_PATH,
  );
  readonly operationItems = computed<readonly OperationalNavigationItem[]>(() =>
    OPERATION_DESTINATIONS.filter((item) => this.capabilities().includes(item.capability)),
  );
  private readonly synchronizeCapabilities = effect(() => {
    if (this.isProfileCompletionRoute() || !this.sessionStore.isAuthenticated()) {
      this.clearCapabilities();
      return;
    }

    this.loadCapabilitiesIfAllowed();
  });

  ngOnInit(): void {
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((event) => {
        this.currentPath.set(this.routePath(event.urlAfterRedirects));
        this.menuOpen.set(false);
      });

    void this.sessionStore.loadSession();
  }

  toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  onMenuTriggerKeydown(event: KeyboardEvent): void {
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
    this.menuOpen.set(false);
    this.accountMenuTrigger?.nativeElement.focus();
  }

  closeMenu(): void {
    this.menuOpen.set(false);
  }

  async onLogout(): Promise<void> {
    this.menuOpen.set(false);
    const result = await this.sessionStore.logoutCurrentDevice();
    if (
      result?.endSessionUrl
      && (
        result.endSessionUrl.startsWith('http://')
        || result.endSessionUrl.startsWith('https://')
        || result.endSessionUrl.startsWith('/')
      )
    ) {
      window.location.href = result.endSessionUrl;
    }
  }

  onLogin(): void {
    window.location.href = '/oauth2/authorization/keycloak';
  }

  private loadCapabilitiesIfAllowed(): void {
    if (
      !this.sessionStore.isAuthenticated()
      || this.isProfileCompletionRoute()
      || this.capabilityRequestStarted
    ) {
      return;
    }

    this.capabilityRequestStarted = true;
    this.capabilitySubscription = this.operationsApiAdapter
      .fetchOperationalCapabilities()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (capabilities) => this.capabilities.set(capabilities),
        error: () => this.capabilities.set([]),
      });
  }

  private clearCapabilities(): void {
    this.capabilitySubscription?.unsubscribe();
    this.capabilitySubscription = undefined;
    this.capabilityRequestStarted = false;
    this.capabilities.set([]);
    this.menuOpen.set(false);
  }

  private routePath(url: string): string {
    const fragmentBoundary = url.indexOf('#');
    const withoutFragment = fragmentBoundary === -1 ? url : url.slice(0, fragmentBoundary);
    const queryBoundary = withoutFragment.indexOf('?');
    return queryBoundary === -1 ? withoutFragment : withoutFragment.slice(0, queryBoundary);
  }
}
