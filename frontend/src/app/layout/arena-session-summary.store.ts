import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CreditLedgerService } from '../api/generated/api/credit-ledger.service';
import type { CreditBalanceResponse } from '../api/generated/model/credit-balance-response';
import type { CurrentDeviceLogoutResponse } from '../api/generated/model/current-device-logout-response';
import { SessionStore } from '../core/session/session.store';

type BalanceStatus = 'idle' | 'loading' | 'available' | 'unavailable';

interface BalanceState {
  status: BalanceStatus;
  value: number | null;
}

const EMPTY_BALANCE: BalanceState = { status: 'idle', value: null };

@Injectable({ providedIn: 'root' })
export class ArenaSessionSummaryStore {
  private readonly sessionStore = inject(SessionStore);
  private readonly creditLedgerService = inject(CreditLedgerService);
  private readonly balanceState = signal<BalanceState>(EMPTY_BALANCE);
  private requestGeneration = 0;

  readonly isChecking = this.sessionStore.isChecking;
  readonly isAuthenticated = this.sessionStore.isAuthenticated;
  readonly displayName = computed(() => this.sessionStore.displayName()?.trim() || 'Conta');
  readonly isVisitor = computed(
    () => this.sessionStore.isExpired() || this.sessionStore.isLoggedOut(),
  );
  readonly balanceStatus = computed(() => this.balanceState().status);
  readonly balance = computed(() => this.balanceState().value);
  readonly balanceLabel = computed(() => {
    const value = this.balanceState().value;
    return value === null
      ? null
      : `${new Intl.NumberFormat('pt-BR', {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        }).format(value)} créditos`;
  });

  async load(): Promise<void> {
    const generation = ++this.requestGeneration;
    this.balanceState.set(EMPTY_BALANCE);

    await this.sessionStore.loadSession();
    if (generation !== this.requestGeneration || !this.sessionStore.isAuthenticated()) {
      return;
    }

    this.balanceState.set({ status: 'loading', value: null });
    try {
      const response = await firstValueFrom(this.creditLedgerService.getMyCreditBalance());
      if (generation !== this.requestGeneration || !this.sessionStore.isAuthenticated()) {
        return;
      }
      this.balanceState.set({ status: 'available', value: this.readBalance(response) });
    } catch {
      if (generation === this.requestGeneration && this.sessionStore.isAuthenticated()) {
        this.balanceState.set({ status: 'unavailable', value: null });
      }
    }
  }

  async logout(): Promise<CurrentDeviceLogoutResponse | null> {
    this.requestGeneration += 1;
    this.balanceState.set(EMPTY_BALANCE);
    return this.sessionStore.logoutCurrentDevice();
  }

  private readBalance(response: CreditBalanceResponse): number {
    if (
      !response ||
      !Number.isFinite(response.balance) ||
      response.balance < 0 ||
      response.scale !== 2
    ) {
      throw new Error('CONTRATO_CREDITOS_INVALIDO');
    }
    return response.balance;
  }
}
