import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RoleService } from '../../api/generated/api/role.service';
import { EnrollmentService } from '../../api/generated/api/enrollment.service';
import { RoleGrantResponse } from '../../api/generated/model/role-grant-response';
import { EnrollmentCorrectionResponse } from '../../api/generated/model/enrollment-correction-response';
import { OperationalRole } from '../../api/generated/model/operational-role';
import {
  EnrollmentAdminApiAdapter,
  ResolvedTargetAccount,
  EnrollmentCorrectionResult,
} from './enrollment-admin-api.adapter';

const TARGET_UUID = '11111111-2222-3333-4444-555555555555';
const DIFFERENT_UUID = '99999999-8888-7777-6666-555555555555';

describe('EnrollmentAdminApiAdapter (S6 Dedicated Adapter)', () => {
  let adapter: EnrollmentAdminApiAdapter;
  let mockRoleService: {
    getRoles: ReturnType<typeof vi.fn>;
  };
  let mockEnrollmentService: {
    correctEnrollment: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    mockRoleService = {
      getRoles: vi.fn(),
    };
    mockEnrollmentService = {
      correctEnrollment: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        EnrollmentAdminApiAdapter,
        { provide: RoleService, useValue: mockRoleService },
        { provide: EnrollmentService, useValue: mockEnrollmentService },
      ],
    });

    adapter = TestBed.inject(EnrollmentAdminApiAdapter);
  });

  describe('resolveTarget', () => {
    it('chama RoleService.getRoles com parâmetro targetIdentityId exato', async () => {
      const response: RoleGrantResponse = {
        identityId: TARGET_UUID,
        nickname: 'usuario_alvo',
        roles: [],
      };
      mockRoleService.getRoles.mockReturnValue(of(response));

      let result: ResolvedTargetAccount | null = null;
      adapter.resolveTarget(TARGET_UUID).subscribe((res) => {
        result = res;
      });

      expect(mockRoleService.getRoles).toHaveBeenCalledWith({
        targetIdentityId: TARGET_UUID,
      });
      expect(result).toEqual({
        targetIdentityId: TARGET_UUID,
        nickname: 'usuario_alvo',
      });
    });

    it('retorna targetIdentityId e nickname quando identityId é idêntico e nickname satisfaz D-44', async () => {
      const response: RoleGrantResponse = {
        identityId: TARGET_UUID,
        nickname: 'Alvo-123_ufu',
        roles: [
          { role: OperationalRole.AdministradorGeral, status: 'VIGENTE' },
        ],
      };
      mockRoleService.getRoles.mockReturnValue(of(response));

      let result: ResolvedTargetAccount | null = null;
      adapter.resolveTarget(TARGET_UUID).subscribe((res) => {
        result = res;
      });

      expect(result).toEqual({
        targetIdentityId: TARGET_UUID,
        nickname: 'Alvo-123_ufu',
      });
      // Descarta imediatamente os roles
      expect(result).not.toHaveProperty('roles');
    });

    it('não efetua trim, case-fold ou normalização: apelido com espaços retorna null', async () => {
      const response: RoleGrantResponse = {
        identityId: TARGET_UUID,
        nickname: ' usuario_alvo ',
        roles: [],
      };
      mockRoleService.getRoles.mockReturnValue(of(response));

      let result: ResolvedTargetAccount | null = null;
      adapter.resolveTarget(TARGET_UUID).subscribe((res) => {
        result = res;
      });

      expect(result).toBeNull();
    });

    it('retorna null quando response.identityId diverge do targetIdentityId requisitado', async () => {
      const response: RoleGrantResponse = {
        identityId: DIFFERENT_UUID,
        nickname: 'usuario_alvo',
        roles: [],
      };
      mockRoleService.getRoles.mockReturnValue(of(response));

      let result: ResolvedTargetAccount | null = null;
      adapter.resolveTarget(TARGET_UUID).subscribe((res) => {
        result = res;
      });

      expect(result).toBeNull();
    });

    it('retorna null quando nickname for nulo, indefinido ou string vazia', async () => {
      const responseNullNick: RoleGrantResponse = {
        identityId: TARGET_UUID,
        nickname: null,
        roles: [],
      };
      mockRoleService.getRoles.mockReturnValue(of(responseNullNick));

      let resultNull: ResolvedTargetAccount | null = null;
      adapter.resolveTarget(TARGET_UUID).subscribe((res) => {
        resultNull = res;
      });
      expect(resultNull).toBeNull();

      const responseEmptyNick: RoleGrantResponse = {
        identityId: TARGET_UUID,
        nickname: '',
        roles: [],
      };
      mockRoleService.getRoles.mockReturnValue(of(responseEmptyNick));

      let resultEmpty: ResolvedTargetAccount | null = null;
      adapter.resolveTarget(TARGET_UUID).subscribe((res) => {
        resultEmpty = res;
      });
      expect(resultEmpty).toBeNull();
    });

    it('retorna null quando nickname não respeitar tamanho ou formato D-44', async () => {
      // Curto demais (< 3)
      mockRoleService.getRoles.mockReturnValue(
        of({ identityId: TARGET_UUID, nickname: 'ab', roles: [] }),
      );
      let resShort: ResolvedTargetAccount | null = null;
      adapter.resolveTarget(TARGET_UUID).subscribe((res) => (resShort = res));
      expect(resShort).toBeNull();

      // Longo demais (> 20)
      mockRoleService.getRoles.mockReturnValue(
        of({
          identityId: TARGET_UUID,
          nickname: 'nome_de_usuario_com_mais_de_vinte_caracteres',
          roles: [],
        }),
      );
      let resLong: ResolvedTargetAccount | null = null;
      adapter.resolveTarget(TARGET_UUID).subscribe((res) => (resLong = res));
      expect(resLong).toBeNull();

      // Caractere inválido (@)
      mockRoleService.getRoles.mockReturnValue(
        of({ identityId: TARGET_UUID, nickname: 'user@ufu', roles: [] }),
      );
      let resInvalid: ResolvedTargetAccount | null = null;
      adapter.resolveTarget(TARGET_UUID).subscribe((res) => (resInvalid = res));
      expect(resInvalid).toBeNull();
    });

    it('caso sentinela realista: HTTP 200 com UUID requisitado, apelido vazio e 3 papéis NAO_CONCEDIDO retorna null e descarta roles', async () => {
      const sentinelResponse: RoleGrantResponse = {
        identityId: TARGET_UUID,
        nickname: '',
        roles: [
          { role: OperationalRole.AdministradorGeral, status: 'NAO_CONCEDIDO' },
          { role: OperationalRole.AdministradorCompeticao, status: 'NAO_CONCEDIDO' },
          { role: OperationalRole.Moderador, status: 'NAO_CONCEDIDO' },
        ],
      };
      mockRoleService.getRoles.mockReturnValue(of(sentinelResponse));

      let result: ResolvedTargetAccount | null = null;
      adapter.resolveTarget(TARGET_UUID).subscribe((res) => {
        result = res;
      });

      expect(result).toBeNull();
    });

    it('propaga erros HTTP de RoleService', async () => {
      const mockError = new Error('HTTP 403 Forbidden');
      mockRoleService.getRoles.mockReturnValue(throwError(() => mockError));

      let caughtError: unknown = null;
      adapter.resolveTarget(TARGET_UUID).subscribe({
        error: (err) => {
          caughtError = err;
        },
      });

      expect(caughtError).toBe(mockError);
    });
  });

  describe('correctEnrollment', () => {
    it('chama EnrollmentService.correctEnrollment com payload exato do contrato gerado e preserva valores literais', async () => {
      const response: EnrollmentCorrectionResponse = {
        targetIdentityId: TARGET_UUID,
        status: 'ATIVO',
        correctedAt: '2026-08-26T12:00:00.000Z',
        previousBindingReleased: true,
      };
      mockEnrollmentService.correctEnrollment.mockReturnValue(of(response));

      let result: EnrollmentCorrectionResult | null = null;
      adapter
        .correctEnrollment(TARGET_UUID, '12345ABC', 'Correção de vínculo por solicitação formal')
        .subscribe((res) => {
          result = res;
        });

      expect(mockEnrollmentService.correctEnrollment).toHaveBeenCalledWith({
        enrollmentCorrectionRequest: {
          targetIdentityId: TARGET_UUID,
          matricula: '12345ABC',
          reason: 'Correção de vínculo por solicitação formal',
        },
      });

      expect(result).toEqual({
        targetIdentityId: TARGET_UUID,
        status: 'ATIVO',
        correctedAt: '2026-08-26T12:00:00.000Z',
        previousBindingReleased: true,
      });
      // Verifica que não adicionou estado de permissão ou vazamento de matrícula anterior
      expect(result).not.toHaveProperty('matricula');
      expect(result).not.toHaveProperty('roles');
    });

    it('preserva previousBindingReleased: false literalmente sem inventar defaults', async () => {
      const response: EnrollmentCorrectionResponse = {
        targetIdentityId: TARGET_UUID,
        status: 'PENDENTE_VALIDACAO',
        correctedAt: '2026-08-26T12:00:00.000Z',
        previousBindingReleased: false,
      };
      mockEnrollmentService.correctEnrollment.mockReturnValue(of(response));

      let result: EnrollmentCorrectionResult | null = null;
      adapter
        .correctEnrollment(TARGET_UUID, '12345ABC', 'Correção sem vínculo prévio')
        .subscribe((res) => {
          result = res;
        });

      expect(result).toEqual({
        targetIdentityId: TARGET_UUID,
        status: 'PENDENTE_VALIDACAO',
        correctedAt: '2026-08-26T12:00:00.000Z',
        previousBindingReleased: false,
      });
    });

    it('lança CONTRATO_CORRECAO_INVALIDO se a resposta for nula ou indefinida', async () => {
      mockEnrollmentService.correctEnrollment.mockReturnValue(of(null as unknown as EnrollmentCorrectionResponse));

      let caughtNull: unknown = null;
      adapter.correctEnrollment(TARGET_UUID, '12345ABC', 'Motivo de teste').subscribe({
        error: (err) => (caughtNull = err),
      });

      expect(caughtNull).toBeInstanceOf(Error);
      expect((caughtNull as Error).message).toContain('CONTRATO_CORRECAO_INVALIDO');

      mockEnrollmentService.correctEnrollment.mockReturnValue(of(undefined as unknown as EnrollmentCorrectionResponse));

      let caughtUndefined: unknown = null;
      adapter.correctEnrollment(TARGET_UUID, '12345ABC', 'Motivo de teste').subscribe({
        error: (err) => (caughtUndefined = err),
      });

      expect(caughtUndefined).toBeInstanceOf(Error);
      expect((caughtUndefined as Error).message).toContain('CONTRATO_CORRECAO_INVALIDO');
    });

    it('lança CONTRATO_CORRECAO_INVALIDO se targetIdentityId divergir do UUID solicitado', async () => {
      const divergentResponse: EnrollmentCorrectionResponse = {
        targetIdentityId: DIFFERENT_UUID,
        status: 'ATIVO',
        correctedAt: '2026-08-26T12:00:00.000Z',
        previousBindingReleased: true,
      };
      mockEnrollmentService.correctEnrollment.mockReturnValue(of(divergentResponse));

      let caughtError: unknown = null;
      adapter.correctEnrollment(TARGET_UUID, '12345ABC', 'Motivo de teste').subscribe({
        error: (err) => (caughtError = err),
      });

      expect(caughtError).toBeInstanceOf(Error);
      expect((caughtError as Error).message).toContain('CONTRATO_CORRECAO_INVALIDO: targetIdentityId retornado diverge');
    });

    it('lança CONTRATO_CORRECAO_INVALIDO se status for nulo, indefinido, vazio ou não-string', async () => {
      const testCases = [
        { ...{ targetIdentityId: TARGET_UUID, correctedAt: '2026-08-26T12:00:00.000Z', previousBindingReleased: true }, status: null },
        { ...{ targetIdentityId: TARGET_UUID, correctedAt: '2026-08-26T12:00:00.000Z', previousBindingReleased: true }, status: '' },
        { ...{ targetIdentityId: TARGET_UUID, correctedAt: '2026-08-26T12:00:00.000Z', previousBindingReleased: true }, status: '   ' },
        { ...{ targetIdentityId: TARGET_UUID, correctedAt: '2026-08-26T12:00:00.000Z', previousBindingReleased: true }, status: 123 as unknown as string },
      ];

      for (const invalidCase of testCases) {
        mockEnrollmentService.correctEnrollment.mockReturnValue(of(invalidCase as unknown as EnrollmentCorrectionResponse));
        let caughtError: unknown = null;
        adapter.correctEnrollment(TARGET_UUID, '12345ABC', 'Motivo de teste').subscribe({
          error: (err) => (caughtError = err),
        });
        expect(caughtError).toBeInstanceOf(Error);
        expect((caughtError as Error).message).toContain('CONTRATO_CORRECAO_INVALIDO: status ausente ou invalido');
      }
    });

    it('lança CONTRATO_CORRECAO_INVALIDO se correctedAt for nulo, vazio ou instante inválido', async () => {
      const testCases = [
        { ...{ targetIdentityId: TARGET_UUID, status: 'ATIVO', previousBindingReleased: true }, correctedAt: null },
        { ...{ targetIdentityId: TARGET_UUID, status: 'ATIVO', previousBindingReleased: true }, correctedAt: '' },
        { ...{ targetIdentityId: TARGET_UUID, status: 'ATIVO', previousBindingReleased: true }, correctedAt: '   ' },
        { ...{ targetIdentityId: TARGET_UUID, status: 'ATIVO', previousBindingReleased: true }, correctedAt: 'data-invalida' },
      ];

      for (const invalidCase of testCases) {
        mockEnrollmentService.correctEnrollment.mockReturnValue(of(invalidCase as unknown as EnrollmentCorrectionResponse));
        let caughtError: unknown = null;
        adapter.correctEnrollment(TARGET_UUID, '12345ABC', 'Motivo de teste').subscribe({
          error: (err) => (caughtError = err),
        });
        expect(caughtError).toBeInstanceOf(Error);
        expect((caughtError as Error).message).toContain('CONTRATO_CORRECAO_INVALIDO: correctedAt ausente ou instante invalido');
      }
    });

    it('lança CONTRATO_CORRECAO_INVALIDO se previousBindingReleased não for boolean', async () => {
      const testCases = [
        { ...{ targetIdentityId: TARGET_UUID, status: 'ATIVO', correctedAt: '2026-08-26T12:00:00.000Z' }, previousBindingReleased: null },
        { ...{ targetIdentityId: TARGET_UUID, status: 'ATIVO', correctedAt: '2026-08-26T12:00:00.000Z' }, previousBindingReleased: 'true' as unknown as boolean },
        { ...{ targetIdentityId: TARGET_UUID, status: 'ATIVO', correctedAt: '2026-08-26T12:00:00.000Z' }, previousBindingReleased: 1 as unknown as boolean },
      ];

      for (const invalidCase of testCases) {
        mockEnrollmentService.correctEnrollment.mockReturnValue(of(invalidCase as unknown as EnrollmentCorrectionResponse));
        let caughtError: unknown = null;
        adapter.correctEnrollment(TARGET_UUID, '12345ABC', 'Motivo de teste').subscribe({
          error: (err) => (caughtError = err),
        });
        expect(caughtError).toBeInstanceOf(Error);
        expect((caughtError as Error).message).toContain('CONTRATO_CORRECAO_INVALIDO: previousBindingReleased nao e booleano');
      }
    });

    it('propaga erros HTTP de EnrollmentService', async () => {
      const mockError = new Error('HTTP 409 Conflict');
      mockEnrollmentService.correctEnrollment.mockReturnValue(throwError(() => mockError));

      let caughtError: unknown = null;
      adapter
        .correctEnrollment(TARGET_UUID, '12345ABC', 'Correção administrativa')
        .subscribe({
          error: (err) => {
            caughtError = err;
          },
        });

      expect(caughtError).toBe(mockError);
    });
  });
});
