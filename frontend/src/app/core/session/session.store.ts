import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { SessionService } from '../../api/generated/api/session.service';
import type { SessionStateResponse } from '../../api/generated/model/session-state-response';
import type { CurrentDeviceLogoutResponse } from '../../api/generated/model/current-device-logout-response';

export type SessionStatus = 'checking' | 'authenticated' | 'expired' | 'logged_out';

export interface SessionState {
  status: SessionStatus;
  displayName: string | null;
  idleTimeout: string | null;
  lastValidActivityAt: string | null;
  endSessionUrl: string | null;
  error: string | null;
}

const INITIAL_STATE: SessionState = {
  status: 'checking',
  displayName: null,
  idleTimeout: null,
  lastValidActivityAt: null,
  endSessionUrl: null,
  error: null,
};

@Injectable({
  providedIn: 'root',
})
export class SessionStore {
  private readonly sessionService = inject(SessionService);

  // Estado interno encapsulado por Signal
  private readonly state = signal<SessionState>(INITIAL_STATE);

  // Seletores públicos computados (Signals)
  readonly status = computed(() => this.state().status);
  readonly displayName = computed(() => this.state().displayName);
  readonly idleTimeout = computed(() => this.state().idleTimeout);
  readonly endSessionUrl = computed(() => this.state().endSessionUrl);
  readonly error = computed(() => this.state().error);

  readonly isChecking = computed(() => this.state().status === 'checking');
  readonly isAuthenticated = computed(() => this.state().status === 'authenticated');
  readonly isExpired = computed(() => this.state().status === 'expired');
  readonly isLoggedOut = computed(() => this.state().status === 'logged_out');

  /**
   * Consulta o estado seguro da sessão atual no BFF (sem polling, chamada pontual).
   */
  async loadSession(): Promise<void> {
    this.state.update((s) => ({
      ...s,
      status: 'checking',
      error: null,
    }));

    try {
      const response: SessionStateResponse = await firstValueFrom(
        this.sessionService.getSession(),
      );

      if (response.authenticated) {
        this.state.set({
          status: 'authenticated',
          displayName: response.displayName ?? null,
          idleTimeout: response.idleTimeout ?? null,
          lastValidActivityAt: response.lastValidActivityAt ?? null,
          endSessionUrl: null,
          error: null,
        });
      } else {
        // Sessão anônima ou expirada: limpa dados em memória
        this.state.set({
          status: 'expired',
          displayName: null,
          idleTimeout: null,
          lastValidActivityAt: null,
          endSessionUrl: null,
          error: null,
        });
      }
    } catch (err: unknown) {
      // Qualquer erro de transporte ou rejeição trata como expirada (fail-closed)
      this.state.set({
        status: 'expired',
        displayName: null,
        idleTimeout: null,
        lastValidActivityAt: null,
        endSessionUrl: null,
        error: err instanceof Error ? err.message : 'Falha ao consultar estado da sessão',
      });
    }
  }

  /**
   * Encerra exclusivamente a sessão do dispositivo atual no BFF.
   */
  async logoutCurrentDevice(): Promise<CurrentDeviceLogoutResponse | null> {
    try {
      const response: CurrentDeviceLogoutResponse = await firstValueFrom(
        this.sessionService.logoutCurrentSession(),
      );

      this.state.set({
        status: 'logged_out',
        displayName: null,
        idleTimeout: null,
        lastValidActivityAt: null,
        endSessionUrl: response.endSessionUrl ?? null,
        error: null,
      });

      return response;
    } catch (err: unknown) {
      // Mesmo com erro de rede no logout remoto, limpa o estado local no cliente
      this.state.set({
        status: 'logged_out',
        displayName: null,
        idleTimeout: null,
        lastValidActivityAt: null,
        endSessionUrl: null,
        error: err instanceof Error ? err.message : 'Erro ao processar logout',
      });
      return null;
    }
  }

  /**
   * Redefine o store para o estado inicial limpo.
   */
  reset(): void {
    this.state.set(INITIAL_STATE);
  }
}
