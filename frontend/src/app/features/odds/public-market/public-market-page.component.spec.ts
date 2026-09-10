import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { OddsPublicService } from '../../../api/generated/api/odds-public.service';
import { OddsMarketState } from '../../../api/generated/model/odds-market-state';
import { PublicOddsExplanation } from '../../../api/generated/model/public-odds-explanation';
import { PublicOddsView } from '../../../api/generated/model/public-odds-view';
import { PublicMarketApiAdapter } from './public-market-api.adapter';
import { PublicMarketPageComponent } from './public-market-page.component';
import { PublicMarketStore } from './public-market.store';

const MARKET_ID = 'm1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c';

function publicMarket(overrides: Partial<PublicOddsView> = {}): PublicOddsView {
  return {
    marketId: MARKET_ID,
    eventId: 'e1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c',
    marketType: PublicOddsView.MarketTypeEnum.HeadToHead,
    state: OddsMarketState.Open,
    current: {
      publicationId: 'pub-current',
      publishedOdds: [1.82, 2.05],
      publishedAt: '2026-09-08T12:00:00Z',
    },
    history: [
      { publicationId: 'pub-previous', publishedOdds: 1.9, publishedAt: '2026-09-07T12:00:00Z' },
    ],
    explanation: {
      algorithmVersion: 'ELO_V1',
      configurationVersion: 'ODDS_CFG_1',
      fairProbability: 0.55,
      margin: 0.05,
      roundingMode: PublicOddsExplanation.RoundingModeEnum.HalfUp,
      confidence: 0.8,
      confidenceBand: PublicOddsExplanation.ConfidenceBandEnum.High,
      sampleSize: 24,
    },
    ...overrides,
  };
}

describe('PublicMarketPageComponent', () => {
  let service: { getPublicOddsMarket: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    service = { getPublicOddsMarket: vi.fn() };
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  async function render() {
    await TestBed.configureTestingModule({
      imports: [PublicMarketPageComponent],
      providers: [
        PublicMarketApiAdapter,
        PublicMarketStore,
        { provide: OddsPublicService, useValue: service },
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => MARKET_ID } } } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(PublicMarketPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('renderiza odd atual, historico e explicacao sem campos privados', async () => {
    service.getPublicOddsMarket.mockReturnValue(of(publicMarket()));
    const fixture = await render();
    const root = fixture.nativeElement as HTMLElement;

    expect(service.getPublicOddsMarket).toHaveBeenCalledWith({ marketId: MARKET_ID });
    expect(root.querySelector('h1')?.textContent).toContain('Mercado pre-jogo');
    expect(root.querySelectorAll('.selection')).toHaveLength(2);
    expect(root.textContent).toContain('1.82');
    expect(root.textContent).toContain('Historico de versoes');
    expect(root.textContent).not.toMatch(/actor|evidence|PII|token|liquidez|aposta/i);
  });

  it('abre a explicacao em duas camadas e mostra baixa confianca sem acao de aposta', async () => {
    service.getPublicOddsMarket.mockReturnValue(
      of(
        publicMarket({
          state: OddsMarketState.Suspended,
          explanation: {
            ...publicMarket().explanation,
            confidence: 0.2,
            confidenceBand: PublicOddsExplanation.ConfidenceBandEnum.Low,
            sampleSize: 2,
          },
        }),
      ),
    );
    const fixture = await render();
    const root = fixture.nativeElement as HTMLElement;
    const button = root.querySelector<HTMLButtonElement>('.market-actions .action');

    expect(root.textContent).toContain('Suspenso');
    expect(root.textContent).toContain('Confianca baixa');
    expect(root.querySelector('#odds-explanation')).toBeNull();
    button?.click();
    fixture.detectChanges();

    expect(root.querySelector('#odds-explanation')).not.toBeNull();
    expect(root.textContent).toContain('A estimativa permanece neutra');
    expect(root.textContent).not.toMatch(/apostar|fazer aposta/i);
  });

  it('trata 404 como mercado ainda nao publicado e nao envia autenticacao', async () => {
    service.getPublicOddsMarket.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404, error: { actor: 'private' } })),
    );
    const fixture = await render();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.textContent).toContain('Mercado ainda nao publicado');
    expect(root.textContent).not.toContain('private');
    expect(root.querySelector('button')).toBeNull();
  });

  it('preserva o snapshot em falha posterior e permite retry somente por GET', async () => {
    const response = new Subject<PublicOddsView>();
    service.getPublicOddsMarket.mockReturnValueOnce(of(publicMarket())).mockReturnValueOnce(response);
    const fixture = await render();
    const store = fixture.componentInstance.store;

    const failedLoad = store.loadMarket(MARKET_ID);
    expect(store.snapshot()?.currentPublicationId).toBe('pub-current');
    response.error(new HttpErrorResponse({ status: 503 }));
    await failedLoad;
    await vi.waitFor(() => expect(store.isStale()).toBe(true));
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Dados podem estar desatualizados');
    expect(service.getPublicOddsMarket).toHaveBeenCalledTimes(2);
  });
});

describe('PublicMarketApiAdapter', () => {
  it('rejeita contrato incompleto e normaliza selecoes e historico', () => {
    const service = { getPublicOddsMarket: vi.fn() };
    TestBed.configureTestingModule({
      providers: [PublicMarketApiAdapter, { provide: OddsPublicService, useValue: service }],
    });
    const adapter = TestBed.inject(PublicMarketApiAdapter);
    const snapshot = adapter.adapt(publicMarket({ current: { ...publicMarket().current, publishedOdds: [1.8, 2.1, 3.2] } }));

    expect(snapshot.selections.map((selection) => selection.label)).toEqual([
      'Selecao 1',
      'Selecao 2',
      'Empate',
    ]);
    expect(snapshot.history[0].publicationId).toBe('pub-previous');
    expect(() => adapter.adapt(null)).toThrow('CONTRATO_ODDS_PUBLICAS_INVALIDO');
  });

  it('aceita uma unica odd publicada conforme o contrato OpenAPI', () => {
    const service = { getPublicOddsMarket: vi.fn() };
    TestBed.configureTestingModule({
      providers: [PublicMarketApiAdapter, { provide: OddsPublicService, useValue: service }],
    });
    const adapter = TestBed.inject(PublicMarketApiAdapter);

    const snapshot = adapter.adapt(
      publicMarket({
        current: { ...publicMarket().current, publishedOdds: [2.1] },
      }),
    );

    expect(snapshot.selections).toHaveLength(1);
    expect(snapshot.selections[0].odd).toBe(2.1);
  });
});
