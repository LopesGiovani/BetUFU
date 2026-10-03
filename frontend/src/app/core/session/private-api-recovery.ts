import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { SessionStore } from './session.store';

/** Recover a session that expires after a guarded page has already loaded. */
@Injectable({ providedIn: 'root' })
export class PrivateApiRecovery {
  private readonly router = inject(Router);
  private readonly session = inject(SessionStore);

  async read<T>(request: Observable<T>): Promise<T> {
    try {
      return await firstValueFrom(request);
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        const returnTo = this.router.url;
        this.session.reset();
        void this.router.navigate(['/entrar'], { queryParams: { returnTo } });
      }
      throw error;
    }
  }
}
