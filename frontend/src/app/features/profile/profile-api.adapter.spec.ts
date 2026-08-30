import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProfileService } from '../../api/generated/api/profile.service';
import { CurrentProfileResponse } from '../../api/generated/model/current-profile-response';
import { ProfileApiAdapter } from './profile-api.adapter';
import { ProfileStore } from './profile.store';

const MOCK_VALID_PROFILE_RESPONSE: CurrentProfileResponse = {
  identityId: 'id-uuid-1234-5678',
  nickname: 'usuario_teste',
  nicknameUpdatedAt: '2026-08-20T12:00:00.000Z',
  matriculaStatus: 'REGISTRADA',
  favoriteOrganizationId: null,
};

describe('ProfileApiAdapter & ProfileStore', () => {
  let adapter: ProfileApiAdapter;
  let store: ProfileStore;
  let mockProfileService: {
    getMyProfile: ReturnType<typeof vi.fn>;
    putMyProfile: ReturnType<typeof vi.fn>;
    putMyFavoriteOrganization: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    mockProfileService = {
      getMyProfile: vi.fn(),
      putMyProfile: vi.fn(),
      putMyFavoriteOrganization: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        ProfileApiAdapter,
        ProfileStore,
        { provide: ProfileService, useValue: mockProfileService },
      ],
    });

    adapter = TestBed.inject(ProfileApiAdapter);
    store = TestBed.inject(ProfileStore);
  });

  describe('ProfileApiAdapter (Contrato e Mapeamento)', () => {
    it('adapta com sucesso resposta valida do perfil próprio', () => {
      const adapted = adapter.adapt(MOCK_VALID_PROFILE_RESPONSE);

      expect(adapted).not.toBeNull();
      expect(adapted?.identityId).toBe('id-uuid-1234-5678');
      expect(adapted?.nickname).toBe('usuario_teste');
      expect(adapted?.nicknameUpdatedAt).toBe('2026-08-20T12:00:00.000Z');
      expect(adapted?.matriculaStatus).toBe('REGISTRADA');
    });

    it('retorna null para resposta vazia ou indefinida', () => {
      expect(adapter.adapt(null)).toBeNull();
      expect(adapter.adapt(undefined)).toBeNull();
    });

    it('lanca erro CONTRATO_PERFIL_INVALIDO se identityId ou nickname estiverem ausentes ou em branco', () => {
      expect(() =>
        adapter.adapt({
          identityId: '',
          nickname: 'valido',
          nicknameUpdatedAt: null,
          matriculaStatus: 'NAO_REGISTRADA',
          favoriteOrganizationId: null,
        }),
      ).toThrow(/CONTRATO_PERFIL_INVALIDO/);

      expect(() =>
        adapter.adapt({
          identityId: 'id-123',
          nickname: '',
          nicknameUpdatedAt: null,
          matriculaStatus: 'NAO_REGISTRADA',
          favoriteOrganizationId: null,
        }),
      ).toThrow(/CONTRATO_PERFIL_INVALIDO/);
    });

    it('chama getMyProfile no ProfileService e adapta o resultado', async () => {
      mockProfileService.getMyProfile.mockReturnValue(of(MOCK_VALID_PROFILE_RESPONSE));

      const result = await new Promise((resolve) => {
        adapter.fetchProfile().subscribe((val) => resolve(val));
      });

      expect(mockProfileService.getMyProfile).toHaveBeenCalledTimes(1);
      expect(result).toEqual({
        identityId: 'id-uuid-1234-5678',
        nickname: 'usuario_teste',
        nicknameUpdatedAt: '2026-08-20T12:00:00.000Z',
        matriculaStatus: 'REGISTRADA',
        favoriteOrganizationId: null,
      });
    });

    it('chama putMyProfile com apelido formatado e adapta o resultado', async () => {
      mockProfileService.putMyProfile.mockReturnValue(of(MOCK_VALID_PROFILE_RESPONSE));

      const result = await new Promise((resolve) => {
        adapter.updateProfile('  novo_apelido  ').subscribe((val) => resolve(val));
      });

      expect(mockProfileService.putMyProfile).toHaveBeenCalledWith({
        profileUpdateRequest: { nickname: 'novo_apelido' },
      });
      expect(result).toEqual({
        identityId: 'id-uuid-1234-5678',
        nickname: 'usuario_teste',
        nicknameUpdatedAt: '2026-08-20T12:00:00.000Z',
        matriculaStatus: 'REGISTRADA',
        favoriteOrganizationId: null,
      });
    });
  });

  describe('ProfileStore (Sinais e Gerenciamento de Estado)', () => {
    it('inicia no estado initial com dados nulos', () => {
      expect(store.status()).toBe('initial');
      expect(store.profile()).toBeNull();
      expect(store.nickname()).toBeNull();
      expect(store.hasProfile()).toBe(false);
      expect(store.isSaving()).toBe(false);
      expect(store.isRetrying()).toBe(false);
      expect(store.errorMessage()).toBeNull();
    });

    it('transita para populated quando o perfil e carregado com sucesso', async () => {
      mockProfileService.getMyProfile.mockReturnValue(of(MOCK_VALID_PROFILE_RESPONSE));

      await store.loadProfile();

      expect(store.status()).toBe('populated');
      expect(store.isPopulated()).toBe(true);
      expect(store.nickname()).toBe('usuario_teste');
      expect(store.identityId()).toBe('id-uuid-1234-5678');
      expect(store.errorMessage()).toBeNull();
    });

    it('transita para empty quando o endpoint retorna 404 (identidade sem perfil criado D-39)', async () => {
      mockProfileService.getMyProfile.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 404 })),
      );

      await store.loadProfile();

      expect(store.status()).toBe('empty');
      expect(store.isEmpty()).toBe(true);
      expect(store.profile()).toBeNull();
      expect(store.hasProfile()).toBe(false);
      expect(store.errorMessage()).toBeNull();
    });

    it('aplica retry limitado a no maximo 2 vezes na leitura e transita para error em falha de rede', async () => {
      mockProfileService.getMyProfile.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 503,
              error: { correlationId: 'corr-net-fail' },
            }),
        ),
      );

      await store.loadProfile();

      expect(mockProfileService.getMyProfile).toHaveBeenCalledTimes(3); // 1 chamada inicial + 2 retries
      expect(store.status()).toBe('error');
      expect(store.isError()).toBe(true);
      expect(store.errorMessage()).toContain('Algo não funcionou como esperado.');
      expect(store.errorCorrelationId()).toBe('corr-net-fail');
    });

    it('salva apelido com sucesso sem auto-retry e atualiza o estado para populated', async () => {
      mockProfileService.putMyProfile.mockReturnValue(of(MOCK_VALID_PROFILE_RESPONSE));

      const success = await store.saveNickname('usuario_teste');

      expect(success).toBe(true);
      expect(mockProfileService.putMyProfile).toHaveBeenCalledTimes(1); // Exatamente 1 tentativa (zero retry automático em mutação)
      expect(store.isPopulated()).toBe(true);
      expect(store.nickname()).toBe('usuario_teste');
      expect(store.isSaving()).toBe(false);
      expect(store.errorMessage()).toBeNull();
    });

    it('trata erro de salvamento sem retentativa automatica e preserva previousProfile', async () => {
      // 1ª carga com sucesso
      mockProfileService.getMyProfile.mockReturnValue(of(MOCK_VALID_PROFILE_RESPONSE));
      await store.loadProfile();
      expect(store.isPopulated()).toBe(true);

      // Tentativa de alteração com erro de transporte/servidor
      mockProfileService.putMyProfile.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 500,
              error: { correlationId: 'corr-save-err-500' },
            }),
        ),
      );

      const success = await store.saveNickname('outro_apelido');

      expect(success).toBe(false);
      expect(mockProfileService.putMyProfile).toHaveBeenCalledTimes(1); // Zero retry
      expect(store.isError()).toBe(true);
      expect(store.isSaving()).toBe(false);
      expect(store.errorMessage()).toContain('Não foi possível salvar seu perfil.');
      expect(store.errorCorrelationId()).toBe('corr-save-err-500');
      expect(store.previousProfile()).not.toBeNull();
    });

    it('sanitiza mensagens de erro 401 e 403 no salvamento conforme 02-UI-SPEC.md', async () => {
      mockProfileService.putMyProfile.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 401 })),
      );
      await store.saveNickname('nick401');
      expect(store.errorMessage()).toBe('Sua sessão expirou. Entre novamente para continuar.');

      mockProfileService.putMyProfile.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 403 })),
      );
      await store.saveNickname('nick403');
      expect(store.errorMessage()).toBe('Você não tem acesso a esta área.');
    });
  });
});
