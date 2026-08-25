import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { EnrollmentService } from '../../api/generated/api/enrollment.service';
import { EnrollmentStatusResponse } from '../../api/generated/model/enrollment-status-response';
import { EnrollmentApiAdapter } from './enrollment-api.adapter';
import { EnrollmentStore } from './enrollment.store';

const MOCK_REGISTERED_RESPONSE: EnrollmentStatusResponse = {
  status: 'ATIVO',
  createdAt: '2026-08-22T10:00:00.000Z',
};

const MOCK_UNREGISTERED_RESPONSE: EnrollmentStatusResponse = {
  status: 'NAO_REGISTRADA',
  createdAt: null,
};

describe('EnrollmentApiAdapter & EnrollmentStore', () => {
  let adapter: EnrollmentApiAdapter;
  let store: EnrollmentStore;
  let mockEnrollmentService: {
    getEnrollment: ReturnType<typeof vi.fn>;
    putEnrollment: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    mockEnrollmentService = {
      getEnrollment: vi.fn(),
      putEnrollment: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        EnrollmentApiAdapter,
        EnrollmentStore,
        { provide: EnrollmentService, useValue: mockEnrollmentService },
      ],
    });

    adapter = TestBed.inject(EnrollmentApiAdapter);
    store = TestBed.inject(EnrollmentStore);
  });

  describe('EnrollmentApiAdapter (Contrato e Mapeamento)', () => {
    it('adapta com sucesso resposta valida de matricula registrada', () => {
      const adapted = adapter.adapt(MOCK_REGISTERED_RESPONSE);

      expect(adapted).not.toBeNull();
      expect(adapted?.status).toBe('ATIVO');
      expect(adapted?.createdAt).toBe('2026-08-22T10:00:00.000Z');
    });

    it('adapta com sucesso resposta valida de matricula nao registrada', () => {
      const adapted = adapter.adapt(MOCK_UNREGISTERED_RESPONSE);

      expect(adapted).not.toBeNull();
      expect(adapted?.status).toBe('NAO_REGISTRADA');
      expect(adapted?.createdAt).toBeNull();
    });

    it('retorna null para resposta vazia ou indefinida', () => {
      expect(adapter.adapt(null)).toBeNull();
      expect(adapter.adapt(undefined)).toBeNull();
    });

    it('lanca erro CONTRATO_MATRICULA_INVALIDO se status estiver ausente ou em branco', () => {
      expect(() =>
        adapter.adapt({
          status: '',
          createdAt: null,
        }),
      ).toThrow(/CONTRATO_MATRICULA_INVALIDO/);

      expect(() =>
        adapter.adapt({
          status: '   ',
          createdAt: null,
        }),
      ).toThrow(/CONTRATO_MATRICULA_INVALIDO/);
    });

    it('chama getEnrollment no EnrollmentService e adapta o resultado', async () => {
      mockEnrollmentService.getEnrollment.mockReturnValue(of(MOCK_REGISTERED_RESPONSE));

      const result = await new Promise((resolve) => {
        adapter.fetchEnrollment().subscribe((val) => resolve(val));
      });

      expect(mockEnrollmentService.getEnrollment).toHaveBeenCalledTimes(1);
      expect(result).toEqual({
        status: 'ATIVO',
        createdAt: '2026-08-22T10:00:00.000Z',
      });
    });

    it('chama putEnrollment com matricula formatada e adapta o resultado', async () => {
      mockEnrollmentService.putEnrollment.mockReturnValue(of(MOCK_REGISTERED_RESPONSE));

      const result = await new Promise((resolve) => {
        adapter.registerEnrollment('  12345678X  ').subscribe((val) => resolve(val));
      });

      expect(mockEnrollmentService.putEnrollment).toHaveBeenCalledWith({
        enrollmentRegistrationRequest: { matricula: '12345678X' },
      });
      expect(result).toEqual({
        status: 'ATIVO',
        createdAt: '2026-08-22T10:00:00.000Z',
      });
    });
  });

  describe('EnrollmentStore (Sinais e Gerenciamento de Estado)', () => {
    it('inicia no estado initial com dados nulos', () => {
      expect(store.status()).toBe('initial');
      expect(store.enrollment()).toBeNull();
      expect(store.isRegistered()).toBe(false);
      expect(store.isSaving()).toBe(false);
      expect(store.isRetrying()).toBe(false);
      expect(store.errorMessage()).toBeNull();
    });

    it('transita para populated quando a matricula registrada e carregada com sucesso', async () => {
      mockEnrollmentService.getEnrollment.mockReturnValue(of(MOCK_REGISTERED_RESPONSE));

      await store.loadEnrollment();

      expect(store.status()).toBe('populated');
      expect(store.isPopulated()).toBe(true);
      expect(store.isRegistered()).toBe(true);
      expect(store.enrollment()?.status).toBe('ATIVO');
      expect(store.errorMessage()).toBeNull();
    });

    it('transita para empty quando o endpoint retorna status NAO_REGISTRADA', async () => {
      mockEnrollmentService.getEnrollment.mockReturnValue(of(MOCK_UNREGISTERED_RESPONSE));

      await store.loadEnrollment();

      expect(store.status()).toBe('empty');
      expect(store.isEmpty()).toBe(true);
      expect(store.isRegistered()).toBe(false);
      expect(store.enrollment()?.status).toBe('NAO_REGISTRADA');
      expect(store.errorMessage()).toBeNull();
    });

    it('transita para empty quando o endpoint retorna 404', async () => {
      mockEnrollmentService.getEnrollment.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 404 })),
      );

      await store.loadEnrollment();

      expect(store.status()).toBe('empty');
      expect(store.isEmpty()).toBe(true);
      expect(store.isRegistered()).toBe(false);
      expect(store.errorMessage()).toBeNull();
    });

    it('aplica retry limitado a no maximo 2 vezes na leitura e transita para error em falha de rede', async () => {
      mockEnrollmentService.getEnrollment.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 503,
              error: { correlationId: 'corr-net-fail-enroll' },
            }),
        ),
      );

      await store.loadEnrollment();

      expect(mockEnrollmentService.getEnrollment).toHaveBeenCalledTimes(3); // 1 chamada inicial + 2 retries
      expect(store.status()).toBe('error');
      expect(store.isError()).toBe(true);
      expect(store.errorMessage()).toContain('Algo não funcionou como esperado.');
      expect(store.errorCorrelationId()).toBe('corr-net-fail-enroll');
    });

    it('registra matricula com sucesso sem auto-retry e atualiza o estado para populated', async () => {
      mockEnrollmentService.putEnrollment.mockReturnValue(of(MOCK_REGISTERED_RESPONSE));

      const success = await store.registerMatricula('12345678X');

      expect(success).toBe(true);
      expect(mockEnrollmentService.putEnrollment).toHaveBeenCalledTimes(1); // Exatamente 1 tentativa (zero retry automático em mutação)
      expect(store.isPopulated()).toBe(true);
      expect(store.isRegistered()).toBe(true);
      expect(store.isSaving()).toBe(false);
      expect(store.errorMessage()).toBeNull();
    });

    it('trata recusa de matricula duplicada (BETUFU-409-MATRICULA_DUPLICADA) com mensagem contratada e sem retry', async () => {
      mockEnrollmentService.putEnrollment.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 409,
              error: {
                code: 'BETUFU-409-MATRICULA_DUPLICADA',
                correlationId: 'corr-dup-409',
              },
            }),
        ),
      );

      const success = await store.registerMatricula('12345678X');

      expect(success).toBe(false);
      expect(mockEnrollmentService.putEnrollment).toHaveBeenCalledTimes(1); // Zero retry
      expect(store.isError()).toBe(true);
      expect(store.isSaving()).toBe(false);
      expect(store.errorMessage()).toBe(
        'Esta matrícula já está registrada em outra conta BETUFU. Se ela é sua, fale com um administrador geral para pedir a correção. Código de referência: corr-dup-409.',
      );
      expect(store.errorCorrelationId()).toBe('corr-dup-409');
    });

    it('trata recusa de conta ja vinculada (BETUFU-409-CONTA_JA_VINCULADA) com mensagem contratada', async () => {
      mockEnrollmentService.putEnrollment.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 409,
              error: {
                code: 'BETUFU-409-CONTA_JA_VINCULADA',
                correlationId: 'corr-vinculada-409',
              },
            }),
        ),
      );

      const success = await store.registerMatricula('12345678X');

      expect(success).toBe(false);
      expect(store.isError()).toBe(true);
      expect(store.errorMessage()).toBe(
        'Sua conta já tem uma matrícula registrada. Só um administrador geral pode alterá-la. Código de referência: corr-vinculada-409.',
      );
    });

    it('trata recusa de limite de tentativas (BETUFU-429-TOO_MANY_ATTEMPTS) com mensagem contratada', async () => {
      mockEnrollmentService.putEnrollment.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 429,
              error: {
                code: 'BETUFU-429-TOO_MANY_ATTEMPTS',
                correlationId: 'corr-limit-429',
              },
            }),
        ),
      );

      const success = await store.registerMatricula('12345678X');

      expect(success).toBe(false);
      expect(store.isError()).toBe(true);
      expect(store.errorMessage()).toBe(
        'Muitas tentativas de registro. Aguarde alguns minutos e tente novamente. Código de referência: corr-limit-429.',
      );
    });

    it('sanitiza mensagens de erro 401 e 403 no registro conforme 02-UI-SPEC.md', async () => {
      mockEnrollmentService.putEnrollment.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 401 })),
      );
      await store.registerMatricula('12345X');
      expect(store.errorMessage()).toBe('Sua sessão expirou. Entre novamente para continuar.');

      mockEnrollmentService.putEnrollment.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 403 })),
      );
      await store.registerMatricula('12345X');
      expect(store.errorMessage()).toBe('Você não tem acesso a esta área.');
    });
  });
});
