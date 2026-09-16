import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { contextualLoginUrl } from '../core/session/contextual-login-url';
import { ArenaSessionSummaryStore } from './arena-session-summary.store';

@Component({
  selector: 'app-arena-shell',
  standalone: true,
  imports: [RouterLink, RouterOutlet],
  templateUrl: './arena-shell.component.html',
  styleUrl: './arena-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ArenaShellComponent implements OnInit {
  readonly sessionSummary = inject(ArenaSessionSummaryStore);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly loginUrl = signal(contextualLoginUrl(this.router.url));

  ngOnInit(): void {
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((event) => this.loginUrl.set(contextualLoginUrl(event.urlAfterRedirects)));
    void this.sessionSummary.load();
  }

  async logout(): Promise<void> {
    const result = await this.sessionSummary.logout();
    if (result?.endSessionUrl && /^(https?:\/\/|\/(?!\/))/u.test(result.endSessionUrl)) {
      window.location.href = result.endSessionUrl;
    }
  }
}
