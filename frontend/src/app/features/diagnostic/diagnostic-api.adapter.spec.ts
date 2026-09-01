import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DiagnosticService } from '../../api/generated/api/diagnostic.service';
import { DiagnosticResponse } from '../../api/generated/model/diagnostic-response';
import { DiagnosticApiAdapter } from './diagnostic-api.adapter';
import { DiagnosticStore } from './diagnostic.store';
import { CANONICAL_COMPONENT_NAMES } from './diagnostic.models';

const MOCK_VALID_RESPONSE: DiagnosticResponse = {
  status: 'UP',
  summary: 'Todos os sistemas operacionais',
  correlationId: 'corr-12345-abcde',
  timestamp: '2026-08-18T20:00:00.000Z',
  environment: 'dev',
  version: '0.1.0',
  components: [
    { name: 'API', status: 'UP', detail: 'Versão 0.1.0', durationMs: 5 },
    { name: 'Sessão BFF', status: 'UP', detail: 'Sessões ativas', durationMs: 2 },
    { name: 'Keycloak', status: 'UP', detail: 'Realm betufu ativo', durationMs: 12 },
    { name: 'PostgreSQL', status: 'UP', detail: 'Conexão estável', durationMs: 4 },
    { name: 'Redis', status: 'UP', detail: 'Ping OK', durationMs: 1 },
    { name: 'Armazenamento de objetos', status: 'UP', detail: 'Bucket acessível', durationMs: 8 },
  ],
};

describe('DiagnosticApiAdapter & DiagnosticStore', () => {
  let adapter: DiagnosticApiAdapter;
  let store: DiagnosticStore;
  let mockDiagnosticService: { getDiagnostic: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    mockDiagnosticService = {
      getDiagnostic: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        DiagnosticApiAdapter,
        DiagnosticStore,
        { provide: DiagnosticService, useValue: mockDiagnosticService },
      ],
    });

    adapter = TestBed.inject(DiagnosticApiAdapter);
    store = TestBed.inject(DiagnosticStore);
  });

  describe('DiagnosticApiAdapter (Contrato e Mapeamento)', () => {
    it('adapta com sucesso payload valido com exatamente 6 componentes na ordem canonica', () => {
      const adapted = adapter.adapt(MOCK_VALID_RESPONSE);

      expect(adapted).not.toBeNull();
      expect(adapted?.status).toBe('UP');
      expect(adapted?.correlationId).toBe('corr-12345-abcde');
      expect(adapted?.components.length).toBe(6);

      // Valida que os nomes e posições seguem CANONICAL_COMPONENT_NAMES
      for (let i = 0; i < CANONICAL_COMPONENT_NAMES.length; i++) {
        expect(adapted?.components[i].name).toBe(CANONICAL_COMPONENT_NAMES[i]);
        expect(adapted?.components[i].statusLabel).toBe('Disponível');
        expect(adapted?.components[i].statusDescription).toBe('Respondendo normalmente.');
        expect(adapted?.components[i].iconName).toBe('check_circle');
      }
    });

    it('retorna null para resposta vazia ou sem componentes (garantia empty)', () => {
      expect(adapter.adapt(null)).toBeNull();
      expect(adapter.adapt({} as DiagnosticResponse)).toBeNull();
      expect(
        adapter.adapt({
          status: 'UP',
          summary: '',
          correlationId: '1',
          timestamp: '',
          components: [],
        }),
      ).toBeNull();
    });

    it('recusa e lanca erro para payload com contagem menor que 6 (garantia zero-one-many: 5 componentes)', () => {
      const invalidResponse: DiagnosticResponse = {
        ...MOCK_VALID_RESPONSE,
        components: MOCK_VALID_RESPONSE.components.slice(0, 5), // apenas 5
      };

      expect(() => adapter.adapt(invalidResponse)).toThrow(
        /CONTRATO_COMPONENTES_INVALIDO: esperado exatamente 6 componentes, recebido 5/,
      );
    });

    it('recusa e lanca erro para payload com contagem maior que 6 (garantia zero-one-many: 7 componentes)', () => {
      const invalidResponse: DiagnosticResponse = {
        ...MOCK_VALID_RESPONSE,
        components: [
          ...MOCK_VALID_RESPONSE.components,
          { name: 'Serviço Extra', status: 'UP', detail: null, durationMs: 1 },
        ],
      };

      expect(() => adapter.adapt(invalidResponse)).toThrow(
        /CONTRATO_COMPONENTES_INVALIDO: esperado exatamente 6 componentes, recebido 7/,
      );
    });

    it('recusa e lanca erro para ordem de componentes divergente da ordem canonica', () => {
      const outOfOrderResponse: DiagnosticResponse = {
        ...MOCK_VALID_RESPONSE,
        components: [
          MOCK_VALID_RESPONSE.components[1], // Sessão BFF na posição 0
          MOCK_VALID_RESPONSE.components[0], // API na posição 1
          ...MOCK_VALID_RESPONSE.components.slice(2),
        ],
      };

      expect(() => adapter.adapt(outOfOrderResponse)).toThrow(/ORDEM_CANONICA_INVALIDA/);
    });

    it('mapeia corretamente estados DEGRADED e DOWN com textos e icones da especificacao', () => {
      const degradedResponse: DiagnosticResponse = {
        ...MOCK_VALID_RESPONSE,
        status: 'DEGRADED',
        components: [
          { name: 'API', status: 'UP', detail: null, durationMs: 5 },
          { name: 'Sessão BFF', status: 'DEGRADED', detail: 'Lento', durationMs: 1500 },
          { name: 'Keycloak', status: 'DOWN', detail: 'Inalcançável', durationMs: null },
          MOCK_VALID_RESPONSE.components[3],
          MOCK_VALID_RESPONSE.components[4],
          MOCK_VALID_RESPONSE.components[5],
        ],
      };

      const adapted = adapter.adapt(degradedResponse);
      expect(adapted?.status).toBe('DEGRADED');

      // API: UP
      expect(adapted?.components[0].statusLabel).toBe('Disponível');
      expect(adapted?.components[0].iconName).toBe('check_circle');

      // Sessão BFF: DEGRADED
      expect(adapted?.components[1].statusLabel).toBe('Degradado');
      expect(adapted?.components[1].statusDescription).toBe('Respondendo com limitações.');
      expect(adapted?.components[1].iconName).toBe('warning_amber');

      // Keycloak: DOWN
      expect(adapted?.components[2].statusLabel).toBe('Indisponível');
      expect(adapted?.components[2].statusDescription).toBe(
        'Não foi possível confirmar a disponibilidade.',
      );
      expect(adapted?.components[2].iconName).toBe('error_outline');
    });
  });

  describe('DiagnosticStore (Reatividade e Estados)', () => {
    it('inicia no estado initial com valores nulos', () => {
      expect(store.status()).toBe('initial');
      expect(store.snapshot()).toBeNull();
      expect(store.errorMessage()).toBeNull();
      expect(store.isRetrying()).toBe(false);
    });

    it('transita para populated em resposta 200 saudavel', async () => {
      mockDiagnosticService.getDiagnostic.mockReturnValue(of(MOCK_VALID_RESPONSE));

      await store.loadDiagnostic();

      expect(store.status()).toBe('populated');
      expect(store.isPopulated()).toBe(true);
      expect(store.snapshot()?.components.length).toBe(6);
      expect(store.errorMessage()).toBeNull();
    });

    it('transita para empty quando resposta nao contem dados (garantia empty)', async () => {
      mockDiagnosticService.getDiagnostic.mockReturnValue(of(null));

      await store.loadDiagnostic();

      expect(store.status()).toBe('empty');
      expect(store.isEmpty()).toBe(true);
      expect(store.snapshot()).toBeNull();
    });

    it('transita para partial quando resposta possui componentes degradados (garantia partial)', async () => {
      const degradedResponse: DiagnosticResponse = {
        ...MOCK_VALID_RESPONSE,
        status: 'DEGRADED',
        components: [
          ...MOCK_VALID_RESPONSE.components.slice(0, 4),
          { name: 'Redis', status: 'DEGRADED', detail: 'Conexões altas', durationMs: 200 },
          MOCK_VALID_RESPONSE.components[5],
        ],
      };
      mockDiagnosticService.getDiagnostic.mockReturnValue(of(degradedResponse));

      await store.loadDiagnostic();

      expect(store.status()).toBe('partial');
      expect(store.isPartial()).toBe(true);
      expect(store.snapshot()?.components[4].status).toBe('DEGRADED');
    });

    it('preserva snapshot anterior durante recarga e falha subsequente (D-04 e D-17)', async () => {
      // 1ª carga: sucesso
      mockDiagnosticService.getDiagnostic.mockReturnValue(of(MOCK_VALID_RESPONSE));
      await store.loadDiagnostic();

      expect(store.isPopulated()).toBe(true);
      const initialSnapshot = store.snapshot();
      expect(initialSnapshot).not.toBeNull();

      // 2ª carga: falha de rede
      mockDiagnosticService.getDiagnostic.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 500, statusText: 'Internal Error' })),
      );
      await store.loadDiagnostic();

      expect(store.isError()).toBe(true);
      expect(store.snapshot()).toBeNull();
      // O previousSnapshot continua em memória preservando a visualização resiliente
      expect(store.previousSnapshot()).toBe(initialSnapshot);
      expect(store.displaySnapshot()).toBe(initialSnapshot);
    });

    it('aplica retry limitado no maximo 2 vezes em falha de rede e transita para error (D-16)', async () => {
      // Mock lança erro continuamente
      mockDiagnosticService.getDiagnostic.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 503, statusText: 'Service Unavailable' })),
      );

      await store.loadDiagnostic();

      // Deve ter tentado exatamente 3 chamadas (1 chamada inicial + 2 retries)
      expect(mockDiagnosticService.getDiagnostic).toHaveBeenCalledTimes(3);
      expect(store.isError()).toBe(true);
      expect(store.errorMessage()).toBe('Algo não funcionou como esperado. Tente novamente.');
    });

    it('sanitiza mensagens de erro 401 e 403 sem vazar URLs internas (01-UI-SPEC:156 e D-14)', async () => {
      // 401: Sessão expirada
      mockDiagnosticService.getDiagnostic.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 401 })),
      );
      await store.loadDiagnostic();
      expect(store.errorMessage()).toBe('Sua sessão não permite continuar. Entre novamente.');

      // 403: Acesso negado
      mockDiagnosticService.getDiagnostic.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 403 })),
      );
      await store.loadDiagnostic();
      expect(store.errorMessage()).toBe('Você não tem acesso a este diagnóstico.');
    });
  });
});
