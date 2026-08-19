import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DiagnosticService } from '../../api/generated/api/diagnostic.service';
import { DiagnosticResponse } from '../../api/generated/model/diagnostic-response';
import { DiagnosticPageComponent } from './diagnostic-page.component';
import { DiagnosticStore } from './diagnostic.store';
import { DiagnosticApiAdapter } from './diagnostic-api.adapter';

const MOCK_POPULATED_RESPONSE: DiagnosticResponse = {
  status: 'UP',
  summary: 'Todos os sistemas operacionais',
  correlationId: 'corr-betufu-998877',
  timestamp: '2026-08-18T20:00:00.000Z',
  environment: 'dev',
  version: '0.1.0',
  components: [
    { name: 'API', status: 'UP', detail: 'Versão 0.1.0', durationMs: 4 },
    { name: 'Sessão BFF', status: 'UP', detail: 'Sessões ativas no Redis', durationMs: 2 },
    { name: 'Keycloak', status: 'UP', detail: 'Realm betufu ativo', durationMs: 15 },
    { name: 'PostgreSQL', status: 'UP', detail: 'Conexão estável', durationMs: 3 },
    { name: 'Redis', status: 'UP', detail: 'Ping OK', durationMs: 1 },
    { name: 'Armazenamento de objetos', status: 'UP', detail: 'Bucket local acessível', durationMs: 6 },
  ],
};

describe('DiagnosticPageComponent (8 Garantias de UI)', () => {
  let fixture: ComponentFixture<DiagnosticPageComponent>;
  let component: DiagnosticPageComponent;
  let store: DiagnosticStore;
  let mockDiagnosticService: { getDiagnostic: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    mockDiagnosticService = {
      getDiagnostic: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [DiagnosticPageComponent],
      providers: [
        DiagnosticApiAdapter,
        DiagnosticStore,
        { provide: DiagnosticService, useValue: mockDiagnosticService },
      ],
    }).compileComponents();

    store = TestBed.inject(DiagnosticStore);
  });

  function createComponent(): void {
    fixture = TestBed.createComponent(DiagnosticPageComponent);
    component = fixture.componentInstance;
  }

  it('Garantia [populated]: renderiza resumo e exatamente seis cartoes na ordem canonica', async () => {
    mockDiagnosticService.getDiagnostic.mockReturnValue(of(MOCK_POPULATED_RESPONSE));
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    // Resumo
    expect(compiled.querySelector('.summary-card')).not.toBeNull();
    expect(compiled.querySelector('.summary-text')?.textContent).toContain('Todos os sistemas operacionais');
    expect(compiled.querySelector('.code-inline')?.textContent).toContain('corr-betufu-998877');

    // Grade dos 6 cartões
    const cards = compiled.querySelectorAll('.component-card');
    expect(cards.length).toBe(6);

    const expectedNames = [
      'API',
      'Sessão BFF',
      'Keycloak',
      'PostgreSQL',
      'Redis',
      'Armazenamento de objetos',
    ];

    cards.forEach((card, index) => {
      expect(card.querySelector('.component-name')?.textContent?.trim()).toBe(expectedNames[index]);
      expect(card.querySelector('.component-badge')?.textContent?.trim()).toBe('Disponível');
    });
  });

  it('Garantia [empty]: exibe mensagem de estado vazio e botao Verificar novamente', async () => {
    mockDiagnosticService.getDiagnostic.mockReturnValue(of(null));
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.empty-state')).not.toBeNull();
    expect(compiled.querySelector('h2')?.textContent).toContain('Nenhum diagnóstico disponível');
    expect(compiled.querySelectorAll('.component-card').length).toBe(0);
  });

  it('Garantia [loading]: desabilita o botao e exibe Verificando durante carregamento', async () => {
    mockDiagnosticService.getDiagnostic.mockReturnValue(of(MOCK_POPULATED_RESPONSE));
    createComponent();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const refreshBtn = compiled.querySelector('.btn-refresh') as HTMLButtonElement;

    // Dispara novo refresh
    const refreshPromise = component.onRefresh();
    fixture.detectChanges();

    expect(refreshBtn.disabled).toBe(true);
    expect(refreshBtn.textContent).toContain('Verificando…');

    await refreshPromise;
    fixture.detectChanges();

    expect(refreshBtn.disabled).toBe(false);
    expect(refreshBtn.textContent).toContain('Verificar novamente');
  });

  it('Garantia [error]: exibe mensagem segura em portugues com codigo de referencia', async () => {
    mockDiagnosticService.getDiagnostic.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 500,
            error: { correlationId: 'corr-error-xyz-123' },
          }),
      ),
    );
    createComponent();
    fixture.detectChanges();

    // Aguarda o store esgotar os 2 retries e transitar para estado de erro
    await vi.waitFor(
      () => {
        expect(store.isError()).toBe(true);
      },
      { timeout: 3000 },
    );
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.error-state')).not.toBeNull();
    expect(compiled.querySelector('.error-text')?.textContent).toContain(
      'Algo não funcionou como esperado. Tente novamente.',
    );
    expect(compiled.querySelector('.code-value')?.textContent).toContain('corr-error-xyz-123');
    expect(compiled.querySelector('.btn-copy')).not.toBeNull();
  });

  it('Garantia [partial]: exibe cartoes degradados com badge adequado sem ocultar servicos saudaveis', async () => {
    const partialResponse: DiagnosticResponse = {
      ...MOCK_POPULATED_RESPONSE,
      status: 'DEGRADED',
      components: [
        MOCK_POPULATED_RESPONSE.components[0],
        { name: 'Sessão BFF', status: 'DEGRADED', detail: 'Tempo de resposta elevado', durationMs: 2500 },
        MOCK_POPULATED_RESPONSE.components[2],
        MOCK_POPULATED_RESPONSE.components[3],
        MOCK_POPULATED_RESPONSE.components[4],
        MOCK_POPULATED_RESPONSE.components[5],
      ],
    };

    mockDiagnosticService.getDiagnostic.mockReturnValue(of(partialResponse));
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const cards = compiled.querySelectorAll('.component-card');
    expect(cards.length).toBe(6);

    // Cartão 0 (API): Disponível
    expect(cards[0].querySelector('.component-badge')?.textContent?.trim()).toBe('Disponível');

    // Cartão 1 (Sessão BFF): Degradado
    expect(cards[1].querySelector('.component-badge')?.textContent?.trim()).toBe('Degradado');
    expect(cards[1].querySelector('.component-detail')?.textContent).toContain('Tempo de resposta elevado');
  });

  it('Garantia [clipboard & aria-live]: copia correlationId para a area de transferencia e anuncia na live region', async () => {
    mockDiagnosticService.getDiagnostic.mockReturnValue(of(MOCK_POPULATED_RESPONSE));
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    // Mock da Clipboard API
    const writeTextSpy = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, {
      clipboard: {
        writeText: writeTextSpy,
      },
    });

    await component.copyCorrelationId('corr-betufu-998877');
    fixture.detectChanges();

    expect(writeTextSpy).toHaveBeenCalledWith('corr-betufu-998877');
    expect(component.copyStatus()).toBe('copied');

    const liveRegion = (fixture.nativeElement as HTMLElement).querySelector('#diagnostic-live-region');
    expect(liveRegion?.textContent).toContain('Código de referência copiado com sucesso.');
  });
});
