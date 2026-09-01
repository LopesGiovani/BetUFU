import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, Subject, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RoleService } from '../../api/generated/api/role.service';
import { EnrollmentService } from '../../api/generated/api/enrollment.service';
import { RoleGrantResponse } from '../../api/generated/model/role-grant-response';
import { EnrollmentCorrectionResponse } from '../../api/generated/model/enrollment-correction-response';
import { OperationalRole } from '../../api/generated/model/operational-role';
import { EnrollmentAdminPageComponent } from './enrollment-admin-page.component';
import { EnrollmentAdminStore } from './enrollment-admin.store';
import { EnrollmentAdminApiAdapter } from './enrollment-admin-api.adapter';

const TARGET_ID = '11111111-2222-3333-4444-555555555555';
const DIFFERENT_TARGET_ID = '99999999-8888-7777-6666-555555555555';
const VALID_NICKNAME = 'alvo_correto';

const MOCK_ROLE_RESPONSE_VALID: RoleGrantResponse = {
  identityId: TARGET_ID,
  nickname: VALID_NICKNAME,
  roles: [
    { role: OperationalRole.AdministradorGeral, status: 'NAO_CONCEDIDO' },
    { role: OperationalRole.AdministradorCompeticao, status: 'NAO_CONCEDIDO' },
    { role: OperationalRole.Moderador, status: 'NAO_CONCEDIDO' },
  ],
};

const MOCK_CORRECTION_RESPONSE_SUCCESS: EnrollmentCorrectionResponse = {
  targetIdentityId: TARGET_ID,
  status: 'ATIVO',
  correctedAt: '2026-08-26T14:30:00.000Z',
  previousBindingReleased: true,
};

describe('EnrollmentAdminPageComponent (Superfície S6 - D-32, VERI-06, VERI-07, OPER-01, PRIV-01)', () => {
  let fixture: ComponentFixture<EnrollmentAdminPageComponent>;
  let component: EnrollmentAdminPageComponent;
  let store: EnrollmentAdminStore;

  let mockRoleService: {
    getRoles: ReturnType<typeof vi.fn>;
  };
  let mockEnrollmentService: {
    correctEnrollment: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    mockRoleService = {
      getRoles: vi.fn(),
    };
    mockEnrollmentService = {
      correctEnrollment: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [EnrollmentAdminPageComponent],
      providers: [
        EnrollmentAdminApiAdapter,
        EnrollmentAdminStore,
        { provide: RoleService, useValue: mockRoleService },
        { provide: EnrollmentService, useValue: mockEnrollmentService },
      ],
    }).compileComponents();

    store = TestBed.inject(EnrollmentAdminStore);
  });

  async function createComponent(): Promise<void> {
    fixture = TestBed.createComponent(EnrollmentAdminPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('Garantia D-32/UI-SPEC: renderiza título, subtítulo e garante ausência da matrícula anterior e de papéis no DOM', async () => {
    await createComponent();

    const titleEl = fixture.nativeElement.querySelector('.page-title');
    const subtitleEl = fixture.nativeElement.querySelector('.page-subtitle');

    expect(titleEl?.textContent?.trim()).toBe('Corrigir matrícula');
    expect(subtitleEl?.textContent?.trim()).toBe(
      'A correção libera a matrícula registrada nesta conta e registra a nova. O número anterior não é exibido, porque a plataforma não o conserva.',
    );

    // Garantia de privacidade D-32: a matrícula anterior nunca aparece em nenhum elemento
    expect(fixture.nativeElement.textContent).not.toContain('Matrícula anterior');
    expect(fixture.nativeElement.textContent).not.toContain('Número anterior:');

    // Garantia de fronteira S6: dados e chips de papéis não são renderizados nesta superfície
    expect(fixture.nativeElement.querySelector('.status-chip')).toBeNull();
    expect(fixture.nativeElement.querySelector('.roles-list')).toBeNull();
  });

  it('valida identificador público e exibe erro de formato sem chamar API', async () => {
    await createComponent();

    component.form.controls.targetIdentityId.setValue('identificador-invalido');
    await component.onSearchTarget();
    fixture.detectChanges();

    expect(mockRoleService.getRoles).not.toHaveBeenCalled();
    const errorEl = fixture.nativeElement.querySelector('#targetIdentity-error');
    expect(errorEl?.textContent?.trim()).toBe(
      'Este identificador não tem o formato esperado. Copie-o do perfil da pessoa.',
    );
    expect(document.activeElement).toBe(
      fixture.nativeElement.querySelector('#targetIdentityInput'),
    );
  });

  it('exibe estado vazio quando a conta não é localizada', async () => {
    mockRoleService.getRoles.mockReturnValue(
      of({ identityId: TARGET_ID, nickname: '', roles: [] }),
    );

    await createComponent();

    component.form.controls.targetIdentityId.setValue(TARGET_ID);
    await component.onSearchTarget();
    fixture.detectChanges();

    const emptyState = fixture.nativeElement.querySelector('.empty-state');
    expect(emptyState).not.toBeNull();
    expect(emptyState.textContent).toContain('Nenhuma conta com esse identificador');
    expect(emptyState.textContent).toContain(
      'Confira o identificador público e tente novamente. Ele é diferente do apelido.',
    );

    // Campos de correção não são abertos
    expect(fixture.nativeElement.querySelector('#matriculaInput')).toBeNull();
  });

  it('exibe dados da conta e abre formulário de correção quando o alvo é resolvido com sucesso', async () => {
    mockRoleService.getRoles.mockReturnValue(of(MOCK_ROLE_RESPONSE_VALID));

    await createComponent();

    component.form.controls.targetIdentityId.setValue(TARGET_ID);
    await component.onSearchTarget();
    fixture.detectChanges();

    const card = fixture.nativeElement.querySelector('.target-profile-card');
    expect(card).not.toBeNull();
    expect(card.textContent).toContain(VALID_NICKNAME);
    expect(card.textContent).toContain(TARGET_ID);

    // Formulário de nova matrícula e motivo disponíveis
    expect(fixture.nativeElement.querySelector('#matriculaInput')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#reasonInput')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.btn-correct')).not.toBeNull();
  });

  it('canonicaliza o UUID público após a busca e mantém a ação disponível', async () => {
    mockRoleService.getRoles.mockReturnValue(of(MOCK_ROLE_RESPONSE_VALID));

    await createComponent();

    component.form.controls.targetIdentityId.setValue(`  ${TARGET_ID}  `);
    await component.onSearchTarget();

    expect(mockRoleService.getRoles).toHaveBeenCalledWith({ targetIdentityId: TARGET_ID });
    expect(component.form.controls.targetIdentityId.value).toBe(TARGET_ID);
    expect(store.targetIdentityId()).toBe(TARGET_ID);
    expect(store.resolvedTarget()?.targetIdentityId).toBe(TARGET_ID);

    component.form.controls.matricula.setValue('12345ABC');
    component.form.controls.reason.setValue('Motivo válido com mais de dez caracteres');
    expect(component.isSubmitDisabled).toBe(false);
  });

  it('distingue mensagens para motivo vazio e motivo curto demais, inclusive contornando estado desabilitado', async () => {
    mockRoleService.getRoles.mockReturnValue(of(MOCK_ROLE_RESPONSE_VALID));

    await createComponent();

    component.form.controls.targetIdentityId.setValue(TARGET_ID);
    await component.onSearchTarget();
    fixture.detectChanges();

    component.form.controls.matricula.setValue('12345ABC');

    // 1. Motivo vazio
    component.form.controls.reason.setValue('');
    component.openConfirmation(fixture.nativeElement.querySelector('.btn-correct'));
    fixture.detectChanges();

    expect(component.reasonErrorMessage).toBe('Informe o motivo da correção.');
    const errorEmpty = fixture.nativeElement.querySelector('#reason-error');
    expect(errorEmpty?.textContent?.trim()).toBe('Informe o motivo da correção.');
    expect(component.isConfirmDialogOpen()).toBe(false);

    // 2. Motivo curto (< 10 caracteres)
    const reasonInput = fixture.nativeElement.querySelector('#reasonInput') as HTMLTextAreaElement;
    reasonInput.value = 'curto';
    reasonInput.dispatchEvent(new Event('input'));
    component.openConfirmation(fixture.nativeElement.querySelector('.btn-correct'));
    fixture.detectChanges();

    expect(component.reasonErrorMessage).toBe(
      'Descreva o motivo com mais detalhe. Ele precisa fazer sentido para quem ler a auditoria depois.',
    );
    const errorShort = fixture.nativeElement.querySelector('#reason-error');
    expect(errorShort?.textContent?.trim()).toBe(
      'Descreva o motivo com mais detalhe. Ele precisa fazer sentido para quem ler a auditoria depois.',
    );
    expect(component.isConfirmDialogOpen()).toBe(false);
  });

  it('valida formato da matrícula com mensagens específicas', async () => {
    mockRoleService.getRoles.mockReturnValue(of(MOCK_ROLE_RESPONSE_VALID));

    await createComponent();

    component.form.controls.targetIdentityId.setValue(TARGET_ID);
    await component.onSearchTarget();
    fixture.detectChanges();

    // 1. Matrícula vazia
    component.form.controls.matricula.setValue('');
    component.form.controls.reason.setValue('Motivo válido com mais de dez caracteres');
    component.openConfirmation(fixture.nativeElement.querySelector('.btn-correct'));
    fixture.detectChanges();

    expect(component.matriculaErrorMessage).toBe('Informe o número da matrícula.');
    expect(component.isConfirmDialogOpen()).toBe(false);

    // 2. Matrícula fora do padrão (< 5 chars)
    component.form.controls.matricula.setValue('12');
    component.openConfirmation(fixture.nativeElement.querySelector('.btn-correct'));
    fixture.detectChanges();

    expect(component.matriculaErrorMessage).toBe(
      'Este número não corresponde ao formato de matrícula da UFU. Confira e digite novamente.',
    );
    expect(component.isConfirmDialogOpen()).toBe(false);
  });

  it('abre diálogo de confirmação destrutiva com apelido literal e foco em Cancelar', async () => {
    mockRoleService.getRoles.mockReturnValue(of(MOCK_ROLE_RESPONSE_VALID));
    mockEnrollmentService.correctEnrollment.mockReturnValue(of(MOCK_CORRECTION_RESPONSE_SUCCESS));

    await createComponent();

    component.form.controls.targetIdentityId.setValue(TARGET_ID);
    await component.onSearchTarget();
    fixture.detectChanges();

    component.form.controls.matricula.setValue('12345ABC');
    component.form.controls.reason.setValue('Solicitação de correção fundamentada');
    fixture.detectChanges();

    const correctBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-correct');
    expect(correctBtn.disabled).toBe(false);

    correctBtn.click();
    fixture.detectChanges();

    // Diálogo aberto
    expect(component.isConfirmDialogOpen()).toBe(true);
    const dialogTitle = fixture.nativeElement.querySelector('#confirm-dialog-title');
    const dialogBody = fixture.nativeElement.querySelector('#confirm-dialog-body');

    expect(dialogTitle?.textContent?.trim()).toBe(`Corrigir a matrícula de ${VALID_NICKNAME}?`);
    expect(dialogBody?.textContent?.trim()).toBe(
      'A matrícula registrada hoje é liberada e substituída pela nova. Não há como desfazer sozinho — seria preciso outra correção. O motivo informado fica na auditoria.',
    );

    // Foco no Cancelar
    const cancelBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-cancel');
    await vi.waitFor(() => expect(document.activeElement).toBe(cancelBtn));

    // Cancela primeiro: não deve chamar a mutação
    cancelBtn.click();
    fixture.detectChanges();
    expect(component.isConfirmDialogOpen()).toBe(false);
    expect(mockEnrollmentService.correctEnrollment).not.toHaveBeenCalled();

    // Abre novamente e confirma
    correctBtn.click();
    fixture.detectChanges();

    const confirmBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-confirm');
    confirmBtn.click();
    await vi.waitFor(() =>
      expect(mockEnrollmentService.correctEnrollment).toHaveBeenCalledTimes(1),
    );
    await fixture.whenStable();
    fixture.detectChanges();

    expect(mockEnrollmentService.correctEnrollment).toHaveBeenCalledWith({
      enrollmentCorrectionRequest: {
        targetIdentityId: TARGET_ID,
        matricula: '12345ABC',
        reason: 'Solicitação de correção fundamentada',
      },
    });

    // Sucesso: campos de mutação são limpos e mensagem polite é exibida
    expect(component.form.controls.matricula.value).toBe('');
    expect(component.form.controls.reason.value).toBe('');
    const liveRegion = fixture.nativeElement.querySelector('#enrollment-admin-live-region');
    expect(liveRegion.textContent).toContain(
      'Matrícula corrigida. O motivo foi registrado na auditoria.',
    );
  });

  it('Garantia de Privacidade D-32/T-02-20-01: valores sobrevivem apenas na memória do formulário após falha e nunca são persistidos em storage', async () => {
    const localStorageSpy = vi.spyOn(Storage.prototype, 'setItem');
    const sessionStorageSpy = vi.spyOn(sessionStorage, 'setItem');

    mockRoleService.getRoles.mockReturnValue(of(MOCK_ROLE_RESPONSE_VALID));
    const conflictError = new HttpErrorResponse({
      status: 409,
      error: {
        code: 'BETUFU-409-MATRICULA_DUPLICADA',
        title: 'Matrícula duplicada',
        detail: 'A nova matrícula já está registrada em outra conta.',
        action: 'Verifique a matrícula informada.',
        correlationId: 'corr-dup-999',
      },
    });
    mockEnrollmentService.correctEnrollment.mockReturnValue(throwError(() => conflictError));

    await createComponent();

    component.form.controls.targetIdentityId.setValue(TARGET_ID);
    await component.onSearchTarget();
    fixture.detectChanges();

    const typedMatricula = '12345ABC';
    const typedReason = 'Motivo de correção para teste de persistência em memória';
    component.form.controls.matricula.setValue(typedMatricula);
    component.form.controls.reason.setValue(typedReason);
    fixture.detectChanges();

    component.openConfirmation(fixture.nativeElement.querySelector('.btn-correct'));
    fixture.detectChanges();

    const confirmBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-confirm');
    confirmBtn.click();
    await fixture.whenStable();
    fixture.detectChanges();

    // Verificações de armazenamento: nenhuma chamada a localStorage ou sessionStorage
    expect(localStorageSpy).not.toHaveBeenCalled();
    expect(sessionStorageSpy).not.toHaveBeenCalled();

    // Valores permanecem na memória do componente/form para não penalizar o operador
    expect(component.form.controls.matricula.value).toBe(typedMatricula);
    expect(component.form.controls.reason.value).toBe(typedReason);

    // Alerta de recusa de negócio exibido com cópia exata do contrato UI-SPEC §332
    const errorAlert = fixture.nativeElement.querySelector('.error-alert');
    expect(errorAlert).not.toBeNull();
    expect(errorAlert.textContent).toContain(
      'A nova matrícula já está registrada em outra conta. A correção não foi aplicada. Código de referência: corr-dup-999.',
    );
  });

  it('preserva motivo longo integralmente na área de texto', async () => {
    mockRoleService.getRoles.mockReturnValue(of(MOCK_ROLE_RESPONSE_VALID));

    await createComponent();

    component.form.controls.targetIdentityId.setValue(TARGET_ID);
    await component.onSearchTarget();
    fixture.detectChanges();

    const longReason = 'A'.repeat(500);
    component.form.controls.reason.setValue(longReason);
    fixture.detectChanges();

    const textarea: HTMLTextAreaElement = fixture.nativeElement.querySelector('#reasonInput');
    expect(textarea.value).toBe(longReason);
    expect(textarea.value.length).toBe(500);
  });

  it('limpa dados transitórios quando a página é destruída', async () => {
    mockRoleService.getRoles.mockReturnValue(of(MOCK_ROLE_RESPONSE_VALID));
    mockEnrollmentService.correctEnrollment.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409 })),
    );

    await createComponent();
    component.form.controls.targetIdentityId.setValue(TARGET_ID);
    await component.onSearchTarget();
    await store.correctEnrollment(
      TARGET_ID,
      '12345ABC',
      'Motivo válido que deve existir somente durante o uso da página',
    );

    expect(store.replacementEnrollment()).toBe('12345ABC');
    expect(store.reason()).not.toBe('');
    expect(store.resolvedTarget()).not.toBeNull();

    fixture.destroy();

    expect(store.targetIdentityId()).toBe('');
    expect(store.replacementEnrollment()).toBe('');
    expect(store.reason()).toBe('');
    expect(store.resolvedTarget()).toBeNull();
    expect(store.isInitial()).toBe(true);
  });

  it('não repopula o store quando uma busca termina depois da destruição da página', async () => {
    const responseSubject = new Subject<RoleGrantResponse>();
    mockRoleService.getRoles.mockReturnValue(responseSubject.asObservable());

    await createComponent();
    component.form.controls.targetIdentityId.setValue(TARGET_ID);
    const pendingSearch = component.onSearchTarget();

    expect(store.isResolving()).toBe(true);
    fixture.destroy();
    responseSubject.next(MOCK_ROLE_RESPONSE_VALID);
    responseSubject.complete();

    expect(await pendingSearch).toBeUndefined();
    expect(store.targetIdentityId()).toBe('');
    expect(store.resolvedTarget()).toBeNull();
    expect(store.isInitial()).toBe(true);
  });

  it('impede segundo envio enquanto a mutação está em andamento (sem retentativa automática)', async () => {
    mockRoleService.getRoles.mockReturnValue(of(MOCK_ROLE_RESPONSE_VALID));
    const responseSubject = new Subject<EnrollmentCorrectionResponse>();
    mockEnrollmentService.correctEnrollment.mockReturnValue(responseSubject.asObservable());

    await createComponent();

    component.form.controls.targetIdentityId.setValue(TARGET_ID);
    await component.onSearchTarget();
    fixture.detectChanges();

    component.form.controls.matricula.setValue('12345ABC');
    component.form.controls.reason.setValue('Motivo válido e suficiente');
    fixture.detectChanges();

    // Primeiro envio
    const firstCall = store.correctEnrollment(TARGET_ID, '12345ABC', 'Motivo válido e suficiente');
    expect(store.isSubmitting()).toBe(true);

    // Tentativa concorrente enquanto pendente
    const secondCall = store.correctEnrollment(TARGET_ID, '12345ABC', 'Motivo válido e suficiente');
    expect(await secondCall).toBe(false);
    expect(mockEnrollmentService.correctEnrollment).toHaveBeenCalledTimes(1);

    responseSubject.next(MOCK_CORRECTION_RESPONSE_SUCCESS);
    responseSubject.complete();
    expect(await firstCall).toBe(true);
    expect(store.isSubmitting()).toBe(false);
  });

  it('ignora a resposta de correção que chega depois da destruição da página', async () => {
    mockRoleService.getRoles.mockReturnValue(of(MOCK_ROLE_RESPONSE_VALID));
    const responseSubject = new Subject<EnrollmentCorrectionResponse>();
    mockEnrollmentService.correctEnrollment.mockReturnValue(responseSubject.asObservable());

    await createComponent();
    component.form.controls.targetIdentityId.setValue(TARGET_ID);
    await component.onSearchTarget();

    const pendingCorrection = store.correctEnrollment(
      TARGET_ID,
      '12345ABC',
      'Motivo válido que não pode reaparecer depois da saída',
    );
    expect(store.isSubmitting()).toBe(true);

    fixture.destroy();
    responseSubject.next(MOCK_CORRECTION_RESPONSE_SUCCESS);
    responseSubject.complete();

    expect(await pendingCorrection).toBe(false);
    expect(store.isInitial()).toBe(true);
    expect(store.replacementEnrollment()).toBe('');
    expect(store.reason()).toBe('');
    expect(store.feedbackMessage()).toBeNull();
  });

  it('invalida o alvo após 403 e impede uma segunda tentativa sem nova localização', async () => {
    mockRoleService.getRoles.mockReturnValue(of(MOCK_ROLE_RESPONSE_VALID));

    await createComponent();

    component.form.controls.targetIdentityId.setValue(TARGET_ID);
    await component.onSearchTarget();
    fixture.detectChanges();

    mockEnrollmentService.correctEnrollment.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403 })),
    );
    expect(
      await store.correctEnrollment(TARGET_ID, '12345ABC', 'Motivo válido para teste 403'),
    ).toBe(false);
    fixture.detectChanges();

    const errorAlert = fixture.nativeElement.querySelector('.error-alert');
    expect(errorAlert.textContent).toContain('Você não tem acesso a esta área.');
    expect(store.resolvedTarget()).toBeNull();
    expect(component.isSubmitDisabled).toBe(true);

    expect(await store.correctEnrollment(TARGET_ID, '12345ABC', 'Segunda tentativa proibida')).toBe(
      false,
    );
    expect(mockEnrollmentService.correctEnrollment).toHaveBeenCalledTimes(1);
  });

  it('exibe mensagem sanitizada para erro 401 sem expor detalhe bruto', async () => {
    mockRoleService.getRoles.mockReturnValue(of(MOCK_ROLE_RESPONSE_VALID));

    await createComponent();
    component.form.controls.targetIdentityId.setValue(TARGET_ID);
    await component.onSearchTarget();

    mockEnrollmentService.correctEnrollment.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status: 401, error: { detail: 'token interno inválido' } }),
      ),
    );
    await store.correctEnrollment(TARGET_ID, '12345ABC', 'Motivo válido para teste 401');
    fixture.detectChanges();

    const errorAlert = fixture.nativeElement.querySelector('.error-alert');
    expect(errorAlert.textContent).toContain('Sua sessão expirou. Entre novamente para continuar.');
    expect(errorAlert.textContent).not.toContain('token interno inválido');
  });

  it('invalida o alvo após 404 e exige uma nova localização', async () => {
    mockRoleService.getRoles.mockReturnValue(of(MOCK_ROLE_RESPONSE_VALID));

    await createComponent();
    component.form.controls.targetIdentityId.setValue(TARGET_ID);
    await component.onSearchTarget();

    mockEnrollmentService.correctEnrollment.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404 })),
    );
    expect(
      await store.correctEnrollment(TARGET_ID, '12345ABC', 'Motivo válido para teste 404'),
    ).toBe(false);

    expect(store.errorMessage()).toBe(
      'A conta informada não foi localizada. Localize-a novamente antes de corrigir a matrícula.',
    );
    expect(store.resolvedTarget()).toBeNull();
    expect(component.isSubmitDisabled).toBe(true);
    expect(mockEnrollmentService.correctEnrollment).toHaveBeenCalledTimes(1);
  });

  it.each([400, 422])(
    'distingue erro de validação HTTP %s e preserva erros de campo',
    async (status) => {
      mockRoleService.getRoles.mockReturnValue(of(MOCK_ROLE_RESPONSE_VALID));

      await createComponent();
      component.form.controls.targetIdentityId.setValue(TARGET_ID);
      await component.onSearchTarget();

      mockEnrollmentService.correctEnrollment.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status,
              error: {
                detail: 'detalhe técnico que não deve aparecer',
                fieldErrors: [{ field: 'reason', message: 'Motivo recusado pela validação.' }],
              },
            }),
        ),
      );
      expect(await store.correctEnrollment(TARGET_ID, '12345ABC', 'Motivo válido para teste')).toBe(
        false,
      );

      expect(store.errorMessage()).toBe('Revise os campos destacados e tente novamente.');
      expect(store.errorMessage()).not.toContain('detalhe técnico');
      expect(store.fieldErrors().reason).toBe('Motivo recusado pela validação.');
      expect(mockEnrollmentService.correctEnrollment).toHaveBeenCalledTimes(1);
    },
  );

  it.each([500, 0])('sanitiza falha HTTP %s sem retentar a mutação', async (status) => {
    mockRoleService.getRoles.mockReturnValue(of(MOCK_ROLE_RESPONSE_VALID));

    await createComponent();
    component.form.controls.targetIdentityId.setValue(TARGET_ID);
    await component.onSearchTarget();

    mockEnrollmentService.correctEnrollment.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status, error: { detail: 'stack trace sensível' } }),
      ),
    );
    expect(await store.correctEnrollment(TARGET_ID, '12345ABC', 'Motivo válido para teste')).toBe(
      false,
    );

    expect(store.errorMessage()).toBe(
      'Não foi possível corrigir a matrícula. Verifique a conexão e tente novamente.',
    );
    expect(store.errorMessage()).not.toContain('stack trace sensível');
    expect(mockEnrollmentService.correctEnrollment).toHaveBeenCalledTimes(1);
  });

  it('TOCTOU: após resolver conta A, alterar targetIdentityId para conta B invalida imediatamente resolvedTarget, desabilita envio, impede diálogo de confirmação e não chama EnrollmentService', async () => {
    mockRoleService.getRoles.mockReturnValue(of(MOCK_ROLE_RESPONSE_VALID));

    await createComponent();

    // 1. Resolve a conta A com sucesso
    component.form.controls.targetIdentityId.setValue(TARGET_ID);
    await component.onSearchTarget();
    fixture.detectChanges();

    expect(store.isResolved()).toBe(true);
    expect(store.targetNickname()).toBe(VALID_NICKNAME);

    // Preenche matrícula e motivo válidos
    component.form.controls.matricula.setValue('12345ABC');
    component.form.controls.reason.setValue('Motivo válido com mais de dez caracteres');
    fixture.detectChanges();

    expect(component.isSubmitDisabled).toBe(false);

    // 2. Operador altera o campo para conta B
    component.form.controls.targetIdentityId.setValue(DIFFERENT_TARGET_ID);
    fixture.detectChanges();

    // Invalidação imediata: resolvedTarget torna-se nulo
    expect(store.resolvedTarget()).toBeNull();
    expect(store.isResolved()).toBe(false);
    expect(component.isSubmitDisabled).toBe(true);

    // 3. Tentativa de abrir confirmação com estado divergente não abre modal
    const correctBtn = fixture.nativeElement.querySelector('.btn-correct');
    if (correctBtn) {
      component.openConfirmation(correctBtn);
    } else {
      component.openConfirmation(document.createElement('button'));
    }
    fixture.detectChanges();

    expect(component.isConfirmDialogOpen()).toBe(false);

    // 4. EnrollmentService nunca foi invocado
    expect(mockEnrollmentService.correctEnrollment).not.toHaveBeenCalled();
  });

  it('EnrollmentAdminStore: guarda de correctEnrollment rejeita execução quando o alvo não está resolvido ou quando targetIdentityId diverge', async () => {
    // Caso 1: alvo não resolvido
    store.reset();
    const resultUnresolved = await store.correctEnrollment(
      TARGET_ID,
      '12345ABC',
      'Motivo válido para teste de guarda',
    );
    expect(resultUnresolved).toBe(false);
    expect(mockEnrollmentService.correctEnrollment).not.toHaveBeenCalled();
    expect(store.fieldErrors().targetIdentityId).toBe(
      'Identificador divergente ou conta não localizada.',
    );

    // Caso 2: alvo resolvido para A, mas chamada para B
    mockRoleService.getRoles.mockReturnValue(of(MOCK_ROLE_RESPONSE_VALID));
    const resolved = await store.resolveTargetAccount(TARGET_ID);
    expect(resolved).toBe(true);
    expect(store.isResolved()).toBe(true);

    const resultDivergent = await store.correctEnrollment(
      DIFFERENT_TARGET_ID,
      '12345ABC',
      'Motivo válido para teste de guarda divergente',
    );
    expect(resultDivergent).toBe(false);
    expect(mockEnrollmentService.correctEnrollment).not.toHaveBeenCalled();
    expect(store.resolvedTarget()).toBeNull();
    expect(store.fieldErrors().targetIdentityId).toBe(
      'Identificador divergente ou conta não localizada.',
    );
  });
});
