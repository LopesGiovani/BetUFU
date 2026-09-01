import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { of, Subject, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RoleService } from '../../api/generated/api/role.service';
import { SessionService } from '../../api/generated/api/session.service';
import { ProfileService } from '../../api/generated/api/profile.service';
import { RoleGrantResponse } from '../../api/generated/model/role-grant-response';
import { OperationalRole } from '../../api/generated/model/operational-role';
import { RoleAdminPageComponent } from './role-admin-page.component';
import { RoleAdminStore } from './role-admin.store';
import { OperationsApiAdapter } from './operations-api.adapter';
import { ProfileStore } from '../profile/profile.store';
import { ProfileApiAdapter } from '../profile/profile-api.adapter';
import { SessionStore } from '../../core/session/session.store';

const MOCK_ACTOR_ID = 'aaaaaaaa-1111-4111-8111-111111111111';
const MOCK_OTHER_TARGET_ID = 'bbbbbbbb-2222-4222-8222-222222222222';

const MOCK_OTHER_USER_RESPONSE_NO_ROLES: RoleGrantResponse = {
  identityId: MOCK_OTHER_TARGET_ID,
  nickname: 'usuario_alvo',
  roles: [
    { role: OperationalRole.AdministradorGeral, status: 'NAO_CONCEDIDO' },
    { role: OperationalRole.AdministradorCompeticao, status: 'NAO_CONCEDIDO' },
    { role: OperationalRole.Moderador, status: 'NAO_CONCEDIDO' },
  ],
};

const MOCK_OTHER_USER_RESPONSE_ADMIN_VIGENTE: RoleGrantResponse = {
  identityId: MOCK_OTHER_TARGET_ID,
  nickname: 'usuario_alvo',
  roles: [
    { role: OperationalRole.AdministradorGeral, status: 'VIGENTE' },
    { role: OperationalRole.AdministradorCompeticao, status: 'NAO_CONCEDIDO' },
    { role: OperationalRole.Moderador, status: 'NAO_CONCEDIDO' },
  ],
};

const MOCK_ACTOR_RESPONSE_ADMIN_VIGENTE: RoleGrantResponse = {
  identityId: MOCK_ACTOR_ID,
  nickname: 'admin_ator',
  roles: [
    { role: OperationalRole.AdministradorGeral, status: 'VIGENTE' },
    { role: OperationalRole.AdministradorCompeticao, status: 'NAO_CONCEDIDO' },
    { role: OperationalRole.Moderador, status: 'NAO_CONCEDIDO' },
  ],
};

const MOCK_ACTOR_RESPONSE_NO_ADMIN: RoleGrantResponse = {
  identityId: MOCK_ACTOR_ID,
  nickname: 'admin_ator',
  roles: [
    { role: OperationalRole.AdministradorGeral, status: 'NAO_CONCEDIDO' },
    { role: OperationalRole.AdministradorCompeticao, status: 'NAO_CONCEDIDO' },
    { role: OperationalRole.Moderador, status: 'NAO_CONCEDIDO' },
  ],
};

describe('RoleAdminPageComponent (Superfície S5 - OPER-01, OPER-02, OPER-04, D-41, D-42, D-48 e D-49)', () => {
  let fixture: ComponentFixture<RoleAdminPageComponent>;
  let component: RoleAdminPageComponent;
  let store: RoleAdminStore;
  let profileStore: ProfileStore;
  let sessionStore: SessionStore;

  let mockRoleService: {
    getRoles: ReturnType<typeof vi.fn>;
    grantRole: ReturnType<typeof vi.fn>;
    revokeRole: ReturnType<typeof vi.fn>;
  };
  let mockProfileService: {
    getMyProfile: ReturnType<typeof vi.fn>;
    putMyProfile: ReturnType<typeof vi.fn>;
  };
  let mockSessionService: {
    getSession: ReturnType<typeof vi.fn>;
    logoutCurrentSession: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    mockRoleService = {
      getRoles: vi.fn(),
      grantRole: vi.fn(),
      revokeRole: vi.fn(),
    };
    mockProfileService = {
      getMyProfile: vi.fn().mockReturnValue(
        of({
          identityId: MOCK_ACTOR_ID,
          nickname: 'admin_ator',
          nicknameUpdatedAt: '2026-08-20T10:00:00.000Z',
          matriculaStatus: 'REGISTRADA',
          favoriteOrganizationId: null,
        }),
      ),
      putMyProfile: vi.fn(),
    };
    mockSessionService = {
      getSession: vi.fn().mockReturnValue(
        of({
          authenticated: true,
          displayName: 'Admin Ator',
          idleTimeout: 'PT15M',
          lastValidActivityAt: '2026-08-20T10:00:00.000Z',
        }),
      ),
      logoutCurrentSession: vi.fn().mockReturnValue(of({ endSessionUrl: '/logged_out' })),
    };

    await TestBed.configureTestingModule({
      imports: [RoleAdminPageComponent],
      providers: [
        OperationsApiAdapter,
        RoleAdminStore,
        ProfileApiAdapter,
        ProfileStore,
        SessionStore,
        { provide: RoleService, useValue: mockRoleService },
        { provide: ProfileService, useValue: mockProfileService },
        { provide: SessionService, useValue: mockSessionService },
      ],
    }).compileComponents();

    store = TestBed.inject(RoleAdminStore);
    profileStore = TestBed.inject(ProfileStore);
    sessionStore = TestBed.inject(SessionStore);
  });

  async function createComponent(): Promise<void> {
    fixture = TestBed.createComponent(RoleAdminPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('Garantia D-49: renderiza título e aviso obrigatório de encerramento de sessões antes de qualquer ação', async () => {
    await createComponent();

    const titleEl = fixture.nativeElement.querySelector('.page-title');
    const subtitleEl = fixture.nativeElement.querySelector('.page-subtitle');

    expect(titleEl?.textContent?.trim()).toBe('Papéis operacionais');
    expect(subtitleEl?.textContent?.trim()).toBe(
      'Conceda e revogue papéis. Toda alteração é auditada e encerra as sessões da pessoa afetada.',
    );
  });

  it('Garantia D-41/D-42: valida que a busca aceita apenas UUID válido e exibe erro de formato sem chamar API', async () => {
    await createComponent();

    component.searchControl.setValue('email@invalido.com');
    await component.onSearchSubmit();
    fixture.detectChanges();

    expect(mockRoleService.getRoles).not.toHaveBeenCalled();
    const errorEl = fixture.nativeElement.querySelector('#targetIdentity-error');
    expect(errorEl?.textContent?.trim()).toBe(
      'Este identificador não tem o formato esperado. Copie-o do perfil da pessoa.',
    );
    expect(errorEl?.getAttribute('role')).toBe('alert');
    expect(document.activeElement).toBe(
      fixture.nativeElement.querySelector('#targetIdentityInput'),
    );
  });

  it('Garantia Empty State: quando a busca retorna 404, exibe estado vazio de pessoa não localizada', async () => {
    const notFoundError = new HttpErrorResponse({
      status: 404,
      statusText: 'Not Found',
    });
    mockRoleService.getRoles.mockReturnValue(throwError(() => notFoundError));

    await createComponent();

    component.searchControl.setValue(MOCK_OTHER_TARGET_ID);
    component.onSearchSubmit();
    await fixture.whenStable();
    fixture.detectChanges();

    const emptyState = fixture.nativeElement.querySelector('.empty-state');
    expect(emptyState).not.toBeNull();
    expect(emptyState.textContent).toContain('Nenhuma conta com esse identificador');
    expect(emptyState.textContent).toContain(
      'Confira o identificador público e tente novamente. Ele é diferente do apelido.',
    );
  });

  it('Garantia Três Linhas Fixas: pessoa sem papéis ainda exibe as 3 linhas fixas e aviso de nenhum papel concedido', async () => {
    mockRoleService.getRoles.mockReturnValue(of(MOCK_OTHER_USER_RESPONSE_NO_ROLES));

    await createComponent();

    component.searchControl.setValue(MOCK_OTHER_TARGET_ID);
    component.onSearchSubmit();
    await fixture.whenStable();
    fixture.detectChanges();

    // Aviso de nenhum papel concedido
    const emptyRolesNotice = fixture.nativeElement.querySelector('.empty-roles-notice');
    expect(emptyRolesNotice).not.toBeNull();
    expect(emptyRolesNotice.textContent).toContain('Nenhum papel concedido');
    expect(emptyRolesNotice.textContent).toContain(
      'Esta pessoa não tem papéis operacionais. Selecione um papel para conceder.',
    );

    // 3 linhas fixas
    const roleItems = fixture.nativeElement.querySelectorAll('.role-card-item');
    expect(roleItems.length).toBe(3);

    expect(roleItems[0].textContent).toContain('Administrador geral');
    expect(roleItems[0].textContent).toContain('Não concedido');

    expect(roleItems[1].textContent).toContain('Administrador de competição');
    expect(roleItems[1].textContent).toContain('Indisponível nesta fase');

    expect(roleItems[2].textContent).toContain('Moderador');
    expect(roleItems[2].textContent).toContain('Indisponível nesta fase');
  });

  it('Garantia Papéis Escopados Deferidos: ADMINISTRADOR_COMPETICAO e MODERADOR exibem motivo acessível e nenhum controle de mutação ou scopeRef', async () => {
    mockRoleService.getRoles.mockReturnValue(of(MOCK_OTHER_USER_RESPONSE_NO_ROLES));

    await createComponent();

    component.searchControl.setValue(MOCK_OTHER_TARGET_ID);
    component.onSearchSubmit();
    await fixture.whenStable();
    fixture.detectChanges();

    const roleItems = fixture.nativeElement.querySelectorAll('.role-card-item');

    // Linha 2: Administrador de competição
    const compRow = roleItems[1];
    expect(compRow.textContent).toContain(
      'Disponível na Fase 3, quando o catálogo de competições fornecer escopos válidos.',
    );
    expect(compRow.querySelector('button')).toBeNull();
    expect(compRow.querySelector('input')).toBeNull();

    // Linha 3: Moderador
    const modRow = roleItems[2];
    expect(modRow.textContent).toContain(
      'Disponível na Fase 7, quando o escopo de moderação estiver definido.',
    );
    expect(modRow.querySelector('button')).toBeNull();
    expect(modRow.querySelector('input')).toBeNull();

    // Invocação direta na store para papéis escopados é bloqueada pelo cliente
    const grantComp = await store.grantRole(OperationalRole.AdministradorCompeticao);
    const revokeMod = await store.revokeRole(OperationalRole.Moderador);
    expect(grantComp).toBe(false);
    expect(revokeMod).toBe(false);
    expect(mockRoleService.grantRole).not.toHaveBeenCalled();
    expect(mockRoleService.revokeRole).not.toHaveBeenCalled();
  });

  it('Garantia Guarda D-48a (Autoconcessão): quando o alvo é o próprio ator, botão Conceder é desabilitado com motivo visível e aria-describedby', async () => {
    mockRoleService.getRoles.mockReturnValue(of(MOCK_ACTOR_RESPONSE_NO_ADMIN));

    await createComponent();

    component.searchControl.setValue(MOCK_ACTOR_ID);
    component.onSearchSubmit();
    await fixture.whenStable();
    fixture.detectChanges();

    const grantBtn: HTMLButtonElement | null = fixture.nativeElement.querySelector('.btn-grant');
    expect(grantBtn).not.toBeNull();
    expect(grantBtn?.disabled).toBe(true);

    const reasonEl = fixture.nativeElement.querySelector('#self-grant-blocked-reason');
    expect(reasonEl).not.toBeNull();
    expect(reasonEl?.textContent?.trim()).toBe(
      'Concessão a si mesmo bloqueada. Peça a outro administrador geral.',
    );
    expect(grantBtn?.getAttribute('aria-describedby')).toBe('self-grant-blocked-reason');
  });

  it('Garantia Concessão com Confirmação: clicar em Conceder papel abre diálogo modal com textos contratados e foco em Cancelar', async () => {
    mockRoleService.getRoles.mockReturnValue(of(MOCK_OTHER_USER_RESPONSE_NO_ROLES));
    mockRoleService.grantRole.mockReturnValue(of(MOCK_OTHER_USER_RESPONSE_ADMIN_VIGENTE));

    await createComponent();

    component.searchControl.setValue(MOCK_OTHER_TARGET_ID);
    component.onSearchSubmit();
    await fixture.whenStable();
    fixture.detectChanges();

    const grantBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-grant');
    expect(grantBtn.disabled).toBe(false);

    grantBtn.click();
    fixture.detectChanges();

    // Confere diálogo aberto
    const dialogTitle = fixture.nativeElement.querySelector('#confirm-dialog-title');
    const dialogBody = fixture.nativeElement.querySelector('#confirm-dialog-body');
    expect(dialogTitle?.textContent?.trim()).toBe('Conceder administrador geral a usuario_alvo?');
    expect(dialogBody?.textContent?.trim()).toBe(
      'A pessoa passa a ter esse papel imediatamente e todas as sessões dela são encerradas. A ação fica registrada na auditoria.',
    );

    // Foco inicial no Cancelar
    const cancelBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-cancel');
    await vi.waitFor(() => expect(document.activeElement).toBe(cancelBtn));

    // Confirma
    const confirmBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-confirm');
    confirmBtn.click();
    await vi.waitFor(() => expect(mockRoleService.grantRole).toHaveBeenCalledTimes(1));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(mockRoleService.grantRole).toHaveBeenCalledWith({
      roleGrantRequest: {
        targetIdentityId: MOCK_OTHER_TARGET_ID,
        role: OperationalRole.AdministradorGeral,
      },
    });

    // Mensagem de sucesso polite
    const liveRegion = fixture.nativeElement.querySelector('#role-admin-live-region');
    expect(liveRegion.textContent).toContain(
      'Administrador geral concedido a usuario_alvo. As sessões dessa pessoa foram encerradas.',
    );
    const replacementRevokeButton: HTMLButtonElement =
      fixture.nativeElement.querySelector('.btn-revoke');
    await vi.waitFor(() => expect(document.activeElement).toBe(replacementRevokeButton));
  });

  it('Garantia Revogação do Próprio Papel: quando há múltiplos admins, exibe cópia específica e atualiza sessão', async () => {
    mockRoleService.getRoles.mockReturnValue(of(MOCK_ACTOR_RESPONSE_ADMIN_VIGENTE));
    mockRoleService.revokeRole.mockReturnValue(of(MOCK_ACTOR_RESPONSE_NO_ADMIN));

    await createComponent();

    component.searchControl.setValue(MOCK_ACTOR_ID);
    component.onSearchSubmit();
    await fixture.whenStable();
    fixture.detectChanges();

    const revokeBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-revoke');
    expect(revokeBtn.disabled).toBe(false);

    revokeBtn.click();
    fixture.detectChanges();
    await vi.waitFor(() =>
      expect(document.activeElement).toBe(fixture.nativeElement.querySelector('.btn-cancel')),
    );

    const dialogTitle = fixture.nativeElement.querySelector('#confirm-dialog-title');
    const dialogBody = fixture.nativeElement.querySelector('#confirm-dialog-body');
    const confirmBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-confirm');

    expect(dialogTitle?.textContent?.trim()).toBe('Revogar seu papel de administrador geral?');
    expect(dialogBody?.textContent?.trim()).toBe(
      'Você perde o papel imediatamente e sua sessão é encerrada. Só outro administrador geral poderá devolvê-lo.',
    );
    expect(confirmBtn.textContent?.trim()).toBe('Revogar meu papel');

    confirmBtn.click();
    await vi.waitFor(() => expect(mockRoleService.revokeRole).toHaveBeenCalledTimes(1));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(mockRoleService.revokeRole).toHaveBeenCalledWith({
      roleGrantRequest: {
        targetIdentityId: MOCK_ACTOR_ID,
        role: OperationalRole.AdministradorGeral,
      },
    });
    expect(mockSessionService.getSession).toHaveBeenCalled();
  });

  it('Garantia Recusa Autoritativa do Servidor: exibe 403 SELF_GRANT_BLOCKED mesmo com estado defasado do cliente', async () => {
    mockRoleService.getRoles.mockReturnValue(of(MOCK_OTHER_USER_RESPONSE_NO_ROLES));
    const forbiddenProblem = new HttpErrorResponse({
      status: 403,
      error: {
        code: 'BETUFU-403-SELF_GRANT_BLOCKED',
        title: 'Autoconcessão bloqueada',
        detail: 'Você não pode conceder papéis a si mesmo.',
        action: 'Peça a outro administrador geral.',
        correlationId: 'corr-self-grant-123',
      },
    });
    mockRoleService.grantRole.mockReturnValue(throwError(() => forbiddenProblem));

    await createComponent();

    component.searchControl.setValue(MOCK_OTHER_TARGET_ID);
    component.onSearchSubmit();
    await fixture.whenStable();
    fixture.detectChanges();

    const grantBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-grant');
    grantBtn.click();
    fixture.detectChanges();

    const confirmBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-confirm');
    confirmBtn.click();
    await fixture.whenStable();
    fixture.detectChanges();

    const errorAlert = fixture.nativeElement.querySelector('.error-alert');
    expect(errorAlert).not.toBeNull();
    expect(errorAlert.textContent).toContain(
      'Você não pode conceder papéis a si mesmo. Peça a outro administrador geral. Código de referência: corr-self-grant-123.',
    );
    expect(mockRoleService.grantRole).toHaveBeenCalledTimes(1);
  });

  it('Garantia Recusa Autoritativa do Servidor: exibe 409 LAST_ADMIN mesmo com estado defasado do cliente', async () => {
    mockRoleService.getRoles.mockReturnValue(of(MOCK_ACTOR_RESPONSE_ADMIN_VIGENTE));
    const conflictProblem = new HttpErrorResponse({
      status: 409,
      error: {
        code: 'BETUFU-409-LAST_ADMIN',
        title: 'Último administrador geral',
        detail: 'Você é o único administrador geral.',
        action: 'Conceda o papel a outra pessoa antes de revogar o seu.',
        correlationId: 'corr-last-admin-456',
      },
    });
    mockRoleService.revokeRole.mockReturnValue(throwError(() => conflictProblem));

    await createComponent();

    component.searchControl.setValue(MOCK_ACTOR_ID);
    component.onSearchSubmit();
    await fixture.whenStable();
    fixture.detectChanges();

    const revokeBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-revoke');
    revokeBtn.click();
    fixture.detectChanges();

    const confirmBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-confirm');
    confirmBtn.click();
    await fixture.whenStable();
    fixture.detectChanges();

    const errorAlert = fixture.nativeElement.querySelector('.error-alert');
    expect(errorAlert).not.toBeNull();
    expect(errorAlert.textContent).toContain(
      'Você é o único administrador geral. Conceda o papel a outra pessoa antes de revogar o seu. Código de referência: corr-last-admin-456.',
    );
    expect(mockRoleService.revokeRole).toHaveBeenCalledTimes(1);
    await vi.waitFor(() => expect(document.activeElement).toBe(revokeBtn));
  });

  it('falha fechado quando a identidade do ator não pode ser confirmada', async () => {
    mockProfileService.getMyProfile.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 503 })),
    );
    mockRoleService.getRoles.mockReturnValue(of(MOCK_OTHER_USER_RESPONSE_NO_ROLES));

    await createComponent();
    component.searchControl.setValue(MOCK_OTHER_TARGET_ID);
    await component.onSearchSubmit();
    store.setActorContext(null);
    fixture.detectChanges();

    const grantBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-grant');
    expect(grantBtn.disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('.guard-reason')?.textContent).toContain(
      'Não foi possível confirmar sua identidade.',
    );
    expect(await store.grantRole(OperationalRole.AdministradorGeral)).toBe(false);
    expect(mockRoleService.grantRole).not.toHaveBeenCalled();
  });

  it('impede segundo envio enquanto uma mutação está em andamento', async () => {
    const response = new Subject<RoleGrantResponse>();
    mockRoleService.getRoles.mockReturnValue(of(MOCK_OTHER_USER_RESPONSE_NO_ROLES));
    mockRoleService.grantRole.mockReturnValue(response.asObservable());

    await createComponent();
    component.searchControl.setValue(MOCK_OTHER_TARGET_ID);
    await component.onSearchSubmit();

    const firstMutation = store.grantRole(OperationalRole.AdministradorGeral);
    expect(await store.grantRole(OperationalRole.AdministradorGeral)).toBe(false);
    expect(mockRoleService.grantRole).toHaveBeenCalledTimes(1);

    response.next(MOCK_OTHER_USER_RESPONSE_ADMIN_VIGENTE);
    response.complete();
    expect(await firstMutation).toBe(true);
  });
});
