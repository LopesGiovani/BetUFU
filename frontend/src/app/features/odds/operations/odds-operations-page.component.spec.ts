import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { signal } from '@angular/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { OddsMarketState } from '../../../api/generated/model/odds-market-state';
import { OddsOperationsPageComponent } from './odds-operations-page.component';
import { OddsOperationsStore } from './odds-operations.store';
import type { OddsOperationsSnapshot } from './odds-operations-api.adapter';
import { OperationsApiAdapter } from '../../operations/operations-api.adapter';
import { of } from 'rxjs';

describe('OddsOperationsPageComponent', () => {
  let fixture: ComponentFixture<OddsOperationsPageComponent>;
  const snapshot = signal<OddsOperationsSnapshot>({
    marketId: 'market-01',
    eventId: 'event-01',
    state: OddsMarketState.Suspended,
    stateLabel: 'Suspenso',
    currentVersionId: 'version-02',
    versionNo: 2,
    lockVersion: 4,
    closeDeadline: null,
    updatedAt: '2026-09-08T12:00:00Z',
    isReadOnly: false,
  });
  const proposal = signal(null);
  const store = {
    snapshot: () => snapshot(),
    proposal: () => proposal(),
    marketId: () => 'market-01',
    isLoading: () => false,
    isEmpty: () => false,
    isError: () => false,
    isStale: () => false,
    isMutating: () => false,
    errorMessage: () => null,
    feedbackMessage: () => null,
    loadMarket: vi.fn(),
    transition: vi.fn(),
    propose: vi.fn(),
    approve: vi.fn(),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OddsOperationsPageComponent],
      providers: [
        { provide: OddsOperationsStore, useValue: store },
        {
          provide: OperationsApiAdapter,
          useValue: {
            fetchOperationalCapabilities: () =>
              of([
                'VIEW_ODDS_MARKET',
                'MANAGE_ODDS_MARKET',
                'PROPOSE_ODDS_OVERRIDE',
                'APPROVE_ODDS_OVERRIDE',
              ]),
          },
        },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: new Map([['marketId', 'market-01']]) } },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(OddsOperationsPageComponent);
    fixture.detectChanges();
  });

  it('renderiza estado suspenso e ordem corrigir antes de suspender', async () => {
    await fixture.whenStable();
    fixture.componentInstance.startCorrection();
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Suspenso');
    expect(text).toContain('Corrigir fonte e recalcular');
    expect(text).toContain('Solicitar override excepcional');
    expect(text).not.toContain('Apostar');
  });

  it('nao envia proposta sem referencia de correcao, evidencia e motivo', () => {
    fixture.componentInstance.startCorrection();
    fixture.componentInstance.submitProposal();
    expect(store.propose).not.toHaveBeenCalled();
  });

  it('mantem CLOSED somente leitura e sem CTA mutavel', () => {
    snapshot.set({
      ...snapshot(),
      state: OddsMarketState.Closed,
      stateLabel: 'CLOSED',
      isReadOnly: true,
    });
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('CLOSED/LOCKED');
    expect(text).not.toContain('Suspender mercado');
    expect(text).not.toContain('Solicitar override excepcional');
  });
});
