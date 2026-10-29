import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { OddsMarketState } from '../../../api/generated/model/odds-market-state';
import { PublicOddsExplanation } from '../../../api/generated/model/public-odds-explanation';
import { PublicOddsView } from '../../../api/generated/model/public-odds-view';
import { SessionStore } from '../../../core/session/session.store';
import { AppClock } from '../../../core/time/app-clock';
import { EnrollmentStore } from '../../enrollment/enrollment.store';
import { PublicMarketApiAdapter, PublicMarketSnapshot } from './public-market-api.adapter';
import { PublicMarketBetTicketComponent } from './public-market-bet-ticket.component';
import { PublicMarketBettingApiAdapter } from './public-market-betting-api.adapter';
import { PublicMarketBettingStore } from './public-market-betting.store';

const MARKET_ID = '11111111-1111-4111-8111-111111111111';
const VERSION_1 = '22222222-2222-4222-8222-222222222222';
const PUBLICATION_1 = '33333333-3333-4333-8333-333333333333';

describe('PublicMarketBetTicketComponent', () => {
  it('disables the real form for pause, unknown and error while retaining an accepted receipt', async () => {
    const { fixture } = await render();
    const store = fixture.componentInstance.store;
    for (const value of ['paused', 'unknown', 'error'] as const) {
      store.setAvailability(value); fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('fieldset').disabled).toBe(true);
      expect(fixture.nativeElement.querySelector('#stake').disabled).toBe(true);
      expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    }
    store.setAvailability('available'); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('fieldset').disabled).toBe(false);
  });

  async function render(
    authenticated = true,
    currentUrl = `/odds/mercados/${MARKET_ID}`,
    options: {
      now?: string;
      marketState?: OddsMarketState;
      ownTicket?: object | null;
    } = {},
  ) {
    const publicSnapshot = signal<PublicMarketSnapshot | null>(null);
    const api = {
      getBalance: vi.fn().mockResolvedValue({
        balance: 1000,
        scale: 2,
        minimumStake: 10,
        rulesVersion: 'credits-v1',
      }),
      getOwnTicket: vi.fn().mockResolvedValue(options.ownTicket ?? null),
      submit: vi.fn().mockResolvedValue({
        predictionId: '44444444-4444-4444-8444-444444444444',
        marketId: MARKET_ID,
        marketVersionId: VERSION_1,
        publicationId: PUBLICATION_1,
        selection: 'HOME',
        stake: 10,
        acceptedOdds: 1.82,
        potentialReturn: 18.2,
        netProfit: 8.2,
        ruleVersion: 'elo-v1',
        closeDeadline: '2026-09-15T12:00:00Z',
        state: 'PENDING',
        acceptedAt: '2026-09-14T12:01:00Z',
      }),
    };
    const publicMarket = { fetchMarket: vi.fn(() => of(publicSnapshot()!)) };
    await TestBed.configureTestingModule({
      imports: [PublicMarketBetTicketComponent],
      providers: [
        PublicMarketBettingStore,
        { provide: PublicMarketBettingApiAdapter, useValue: api },
        {
          provide: SessionStore,
          useValue: {
            loadSession: vi.fn().mockResolvedValue(undefined),
            isAuthenticated: () => authenticated,
            revision: signal(0),
            isChecking: signal(false),
          },
        },
        {
          provide: EnrollmentStore,
          useValue: {
            loadEnrollment: vi.fn().mockResolvedValue(undefined),
            isRegistered: () => true,
            isError: () => false,
          },
        },
        { provide: PublicMarketApiAdapter, useValue: publicMarket },
        {
          provide: AppClock,
          useValue: {
            now: () => Date.parse(options.now ?? '2026-09-15T11:00:00Z'),
            scheduleAt: vi.fn(() => vi.fn()),
          },
        },
        provideRouter([]),
      ],
    }).compileComponents();
    vi.spyOn(TestBed.inject(Router), 'url', 'get').mockReturnValue(currentUrl);
    const fixture = TestBed.createComponent(PublicMarketBetTicketComponent);
    fixture.componentInstance.store.setAvailability('available');
    fixture.componentRef.setInput('marketId', MARKET_ID);
    fixture.componentRef.setInput('marketVersionId', VERSION_1);
    fixture.componentRef.setInput('publicationId', PUBLICATION_1);
    fixture.componentRef.setInput('ruleVersion', 'elo-v1');
    fixture.componentRef.setInput('closeDeadline', '2026-09-15T12:00:00Z');
    fixture.componentRef.setInput('marketState', options.marketState ?? OddsMarketState.Open);
    fixture.componentRef.setInput('selections', [
      { code: 'HOME', label: 'Mandante', odd: 1.82 },
      { code: 'AWAY', label: 'Visitante', odd: 2.05 },
    ]);
    fixture.detectChanges();
    const unavailable =
      options.marketState && options.marketState !== OddsMarketState.Open
        ? options.marketState === OddsMarketState.Suspended
          ? 'suspended'
          : 'closed'
        : Date.parse(options.now ?? '2026-09-15T11:00:00Z') >= Date.parse('2026-09-15T12:00:00Z')
          ? options.ownTicket
            ? 'success'
            : 'closed'
          : authenticated
            ? 'eligible'
            : 'visitor';
    await vi.waitFor(() => expect(fixture.componentInstance.store.status()).toBe(unavailable));
    fixture.detectChanges();
    return { fixture, api, publicMarket, publicSnapshot };
  }

  it('mostra mercado encerrado antes do login quando o deadline venceu', async () => {
    const { fixture, api } = await render(false, `/odds/mercados/${MARKET_ID}`, {
      now: '2026-09-15T12:00:00Z',
    });
    const root = fixture.nativeElement as HTMLElement;

    expect(root.textContent).toContain('Mercado encerrado');
    expect(root.textContent).not.toContain('Entrar para palpitar');
    expect(root.querySelector('form')).toBeNull();
    expect(api.getBalance).not.toHaveBeenCalled();
  });

  it('mostra suspensao antes do login para visitante', async () => {
    const { fixture } = await render(false, `/odds/mercados/${MARKET_ID}`, {
      marketState: OddsMarketState.Suspended,
    });
    const root = fixture.nativeElement as HTMLElement;

    expect(root.textContent).toContain('Mercado suspenso');
    expect(root.textContent).not.toContain('Entrar para palpitar');
    expect(root.querySelector('form')).toBeNull();
  });

  it('mantem comprovante no refresh depois do deadline', async () => {
    const ticket = {
      predictionId: '44444444-4444-4444-8444-444444444444',
      marketId: MARKET_ID,
      marketVersionId: VERSION_1,
      publicationId: PUBLICATION_1,
      selection: 'HOME',
      stake: 10,
      acceptedOdds: 1.82,
      potentialReturn: 18.2,
      netProfit: 8.2,
      ruleVersion: 'elo-v1',
      closeDeadline: '2026-09-15T12:00:00Z',
      state: 'PENDING',
      acceptedAt: '2026-09-14T12:01:00Z',
    };
    const { fixture } = await render(true, `/odds/mercados/${MARKET_ID}`, {
      now: '2026-09-15T12:00:00Z',
      ownTicket: ticket,
    });
    const root = fixture.nativeElement as HTMLElement;

    expect(root.textContent).toContain('Palpite registrado');
    expect(root.textContent).toContain('Seu comprovante permanece disponivel');
    expect(root.querySelector('form')).toBeNull();
  });

  it('inicia login preservando a rota atual do mercado', async () => {
    const currentUrl = `/odds/mercados/${MARKET_ID}?selection=AWAY#bilhete`;
    const { fixture } = await render(false, currentUrl);
    const root = fixture.nativeElement as HTMLElement;
    const link = root.querySelector<HTMLAnchorElement>('a.button');

    expect(link?.textContent).toContain('Entrar para palpitar');
    expect(link?.getAttribute('href')).toBe(
      '/login/start?returnTo=' + encodeURIComponent(currentUrl),
    );
  });

  it('renderiza codigos de transporte separados dos rotulos acessiveis', async () => {
    const { fixture } = await render();
    const root = fixture.nativeElement as HTMLElement;
    const radios = Array.from(root.querySelectorAll<HTMLInputElement>('input[type="radio"]'));

    expect(root.querySelector('legend')?.textContent).toContain('Escolha do resultado');
    expect(radios.map((radio) => radio.value)).toEqual(['HOME', 'AWAY']);
    expect(root.textContent).toContain('Mandante');
    expect(root.textContent).toContain('Visitante');
    expect(root.textContent).not.toContain('Idempotency-Key');
  });

  it('aplica all-in, calcula retorno informativo e bloqueia precisao acima de duas casas', async () => {
    const { fixture } = await render();
    const root = fixture.nativeElement as HTMLElement;
    root.querySelector<HTMLInputElement>('input[value="HOME"]')?.click();
    root.querySelector<HTMLButtonElement>('button.quiet')?.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.stake()).toBe(1000);
    expect(fixture.componentInstance.potentialReturn()).toBe(1820);

    const stake = root.querySelector<HTMLInputElement>('#stake')!;
    stake.value = '10.13';
    stake.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(stake.getAttribute('aria-invalid')).toBeNull();
    expect(root.textContent).not.toContain('Use no maximo duas casas decimais.');

    stake.value = '10.001';
    stake.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(stake.getAttribute('aria-invalid')).toBe('true');
    expect(root.textContent).toContain('Use no maximo duas casas decimais.');
  });

  it('envia sem confirmacao inicial e substitui o formulario pelo comprovante proprio', async () => {
    const { fixture, api } = await render();
    const root = fixture.nativeElement as HTMLElement;
    root.querySelector<HTMLInputElement>('input[value="HOME"]')?.click();
    const stake = root.querySelector<HTMLInputElement>('#stake')!;
    stake.value = '10';
    stake.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    root.querySelector<HTMLButtonElement>('button[type="submit"]')?.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.submit).toHaveBeenCalledTimes(1);
    expect(root.textContent).toContain('Palpite registrado');
    expect(root.textContent).toContain('Mandante');
    expect(root.querySelector('form')).toBeNull();
    expect(root.textContent).not.toMatch(/identity|token|idempotency/iu);
  });

  it('mostra a nova versao e mantem o segundo POST bloqueado ate reconfirmacao', async () => {
    const { fixture, api, publicSnapshot } = await render();
    const root = fixture.nativeElement as HTMLElement;
    api.submit.mockRejectedValueOnce(
      new HttpErrorResponse({
        status: 409,
        error: { code: 'BETUFU-409-STALE_VERSION' },
      }),
    );
    publicSnapshot.set({
      marketId: MARKET_ID,
      marketVersionId: '55555555-5555-4555-8555-555555555555',
      eventId: '77777777-7777-4777-8777-777777777777',
      marketType: PublicOddsView.MarketTypeEnum.HeadToHead,
      currentPublicationId: '66666666-6666-4666-8666-666666666666',
      ruleVersion: 'elo-v2',
      closeDeadline: '2026-09-15T12:00:00Z',
      closeDeadlineLabel: '15 de set. de 2026, 09:00',
      state: OddsMarketState.Open,
      stateLabel: 'Aberto',
      publishedAt: '2026-09-14T12:01:00Z',
      publishedAtLabel: '14 de set. de 2026, 09:01',
      selections: [
        { code: 'HOME', label: 'Mandante', odd: 1.9 },
        { code: 'AWAY', label: 'Visitante', odd: 1.98 },
      ],
      history: [],
      explanation: {
        algorithmVersion: 'elo-v2',
        configurationVersion: 'cfg-v2',
        fairProbability: 0.54,
        margin: 0.05,
        roundingMode: PublicOddsExplanation.RoundingModeEnum.HalfUp,
        confidence: 0.8,
        confidenceBand: PublicOddsExplanation.ConfidenceBandEnum.High,
        sampleSize: 18,
      },
      confidenceLabel: 'Confianca alta',
      isLowConfidence: false,
    });
    root.querySelector<HTMLInputElement>('input[value="HOME"]')?.click();
    const stake = root.querySelector<HTMLInputElement>('#stake')!;
    stake.value = '10';
    stake.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    root.querySelector<HTMLButtonElement>('button[type="submit"]')?.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(root.textContent).toContain('A odd mudou antes do envio');
    expect(root.textContent).toContain('elo-v2');
    expect(root.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(true);
    expect(api.submit).toHaveBeenCalledTimes(1);

    root.querySelector<HTMLInputElement>('input[type="checkbox"]')?.click();
    fixture.detectChanges();
    expect(root.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(false);
  });
});
