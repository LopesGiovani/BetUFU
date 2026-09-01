import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { OperationalCapabilitiesService } from '../api/generated/api/operational-capabilities.service';
import { OperationalCapability } from '../api/generated/model/operational-capability';
import { SessionStore } from '../core/session/session.store';

@Component({
  selector: 'app-console-shell',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './console-shell.component.html',
  styleUrl: './console-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConsoleShellComponent implements OnInit {
  readonly sessionStore = inject(SessionStore);
  private readonly capabilitiesService = inject(OperationalCapabilitiesService);
  readonly capabilities = signal<readonly string[]>([]);
  readonly menuOpen = signal(false);
  readonly hasSportsCatalog = computed(() =>
    this.capabilities().includes(OperationalCapability.ManageSportsCatalog),
  );
  readonly hasBootstrap = computed(() =>
    this.capabilities().includes(OperationalCapability.BootstrapSportsCompetition),
  );

  ngOnInit(): void {
    void this.sessionStore.loadSession();
    void this.loadCapabilities();
  }

  toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  closeMenu(): void {
    this.menuOpen.set(false);
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
      const response = await firstValueFrom(this.capabilitiesService.getOperationalCapabilities());
      const raw: unknown = response?.capabilities;
      const values: readonly unknown[] = Array.isArray(raw) ? raw : [];
      this.capabilities.set(values.filter((value): value is string => typeof value === 'string'));
    } catch {
      this.capabilities.set([]);
    }
  }
}
