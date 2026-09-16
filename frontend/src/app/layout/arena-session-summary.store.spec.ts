import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CreditLedgerService } from '../api/generated/api/credit-ledger.service';
import { SessionService } from '../api/generated/api/session.service';
import type { CreditBalanceResponse } from '../api/generated/model/credit-balance-response';
import { SessionStore } from '../core/session/session.store';
import { ArenaSessionSummaryStore } from './arena-session-summary.store';

describe('ArenaSessionSummaryStore', () => {
  let store: ArenaSessionSummaryStore;
  let sessionService: {
    getSession: ReturnType<typeof vi.fn>;
    logoutCurrentSession: ReturnType<typeof vi.fn>;
  };
  let creditLedgerService: { getMyCreditBalance: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    sessionService = {
      getSession: vi.fn().mockReturnValue(of({ authenticated: true, displayName: 'Aluno UFU' })),
      logoutCurrentSession: vi.fn().mockReturnValue(of({ endSessionUrl: null })),
    };
    creditLedgerService = {
      getMyCreditBalance: vi
        .fn()
        .mockReturnValue(
          of({ balance: 1000, scale: 2, minimumStake: 10, rulesVersion: 'credits-v1' }),
        ),
    };

    TestBed.configureTestingModule({
      providers: [
        ArenaSessionSummaryStore,
        SessionStore,
        { provide: SessionService, useValue: sessionService },
        { provide: CreditLedgerService, useValue: creditLedgerService },
      ],
    });
    store = TestBed.inject(ArenaSessionSummaryStore);
  });

  it('carrega saldo somente depois de confirmar a sessão autenticada', async () => {
    await store.load();

    expect(sessionService.getSession).toHaveBeenCalledTimes(1);
    expect(creditLedgerService.getMyCreditBalance).toHaveBeenCalledTimes(1);
    expect(store.isAuthenticated()).toBe(true);
    expect(store.displayName()).toBe('Aluno UFU');
    expect(store.balanceStatus()).toBe('available');
    expect(store.balanceLabel()).toBe('1.000,00 créditos');
  });

  it('não consulta créditos para visitante ou sessão expirada', async () => {
    sessionService.getSession.mockReturnValue(of({ authenticated: false }));

    await store.load();

    expect(store.isVisitor()).toBe(true);
    expect(store.balance()).toBeNull();
    expect(creditLedgerService.getMyCreditBalance).not.toHaveBeenCalled();
  });

  it('degrada falha de saldo sem derrubar a sessão autenticada', async () => {
    creditLedgerService.getMyCreditBalance.mockReturnValue(
      throwError(() => new Error('saldo indisponível')),
    );

    await store.load();

    expect(store.isAuthenticated()).toBe(true);
    expect(store.balanceStatus()).toBe('unavailable');
    expect(store.balance()).toBeNull();
  });

  it('ignora resposta tardia de saldo depois do logout', async () => {
    const delayedBalance = new Subject<CreditBalanceResponse>();
    creditLedgerService.getMyCreditBalance.mockReturnValue(delayedBalance);

    const loading = store.load();
    await vi.waitFor(() => expect(store.balanceStatus()).toBe('loading'));

    await store.logout();
    delayedBalance.next({
      balance: 1000,
      scale: 2,
      minimumStake: 10,
      rulesVersion: 'credits-v1',
    });
    delayedBalance.complete();
    await loading;

    expect(store.isVisitor()).toBe(true);
    expect(store.balanceStatus()).toBe('idle');
    expect(store.balance()).toBeNull();
  });
});
