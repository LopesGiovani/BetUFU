import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';

import { IdentityService } from '../../api/generated';

@Component({
  selector: 'app-tracer-page',
  standalone: true,
  templateUrl: './tracer-page.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TracerPageComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly identityService = inject(IdentityService);

  readonly isLoading = signal(false);
  readonly identityId = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  verifyIntegration(): void {
    if (this.isLoading()) {
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.identityService
      .putCurrentIdentity()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isLoading.set(false)),
      )
      .subscribe({
        next: ({ identityId }) => this.identityId.set(identityId),
        error: () =>
          this.errorMessage.set('Não foi possível verificar a integração. Tente novamente.'),
      });
  }
}
