import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, it, expect, vi } from 'vitest';
import { SettlementHistoryPageComponent } from './settlement-history-page.component';
import { SettlementHistoryApiAdapter } from './settlement-history-api.adapter';
describe('Historico de liquidacoes', () => {
  async function render(read: ReturnType<typeof vi.fn>) {
    TestBed.configureTestingModule({
      imports: [SettlementHistoryPageComponent],
      providers: [provideRouter([]), { provide: SettlementHistoryApiAdapter, useValue: { read } }],
    });
    const f = TestBed.createComponent(SettlementHistoryPageComponent);
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    return f;
  }
  it('mostra vazio com retorno para agenda', async () => {
    const f = await render(vi.fn().mockResolvedValue({ items: [], corrections: [] }));
    expect(f.nativeElement.textContent).toContain('Seu historico de palpites ainda esta vazio.');
    expect(f.nativeElement.querySelector('a').textContent).toContain('Ver agenda');
  });
  it('distingue pendencia de fato antigo e explica recuperacao', async () => {
    const base = {
      predictionId: 'p',
      eventId: 'e',
      marketId: 'm',
      acceptedAt: '2026-10-01T00:00:00Z',
      selection: 'HOME',
      stake: 50,
      acceptedOdds: 2.12,
      potentialReturn: 106,
    };
    const f = await render(
      vi.fn().mockResolvedValue({
        items: [
          { ...base, current: true, state: 'PENDING', processingStatus: 'RUNNING' },
          {
            ...base,
            current: false,
            state: 'WON',
            processingStatus: 'COMPLETE',
            amount: 106,
            homeScore: 2,
            awayScore: 1,
            ruleVersion: 'v1',
            ledgerEntryId: 'l',
          },
        ],
        corrections: [
          {
            caseId: 'c',
            referenceCode: 'REF-1',
            reason: 'SETTLEMENT_CORRECTION',
            amount: 106,
            recovered: 56,
            pending: 50,
            status: 'PENDING',
          },
        ],
      }),
    );
    expect(f.nativeElement.textContent).toContain('Liquidacao parcial em processamento.');
    expect(f.nativeElement.textContent).toContain('Fato historico preservado');
    expect(f.nativeElement.textContent).toContain('Valor recuperado');
    expect(f.nativeElement.textContent).toContain('REF-1');
    expect(f.nativeElement.querySelector('main')).toBeNull();
  });
  it('erro nao reproduz detalhes internos', async () => {
    const f = await render(vi.fn().mockRejectedValue(new Error('secret source')));
    expect(f.nativeElement.textContent).toContain('Nao foi possivel carregar seu historico.');
    expect(f.nativeElement.textContent).not.toContain('secret source');
  });
});
