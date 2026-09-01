import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { OperationalPanelService } from '../../api/generated/api/operational-panel.service';
import { OperationalPanelResponse } from '../../api/generated/model/operational-panel-response';
import { OperationsPanelPageComponent } from './operations-panel-page.component';
import { OperationsPanelStore } from './operations-panel.store';
import { OperationsApiAdapter } from './operations-api.adapter';

const MOCK_POPULATED_RESPONSE: OperationalPanelResponse = {
  queriedAt: '2026-08-20T14:30:00.000Z',
  timeWindow: {
    since: '2026-08-13T00:00:00.000Z',
    until: '2026-08-20T14:30:00.000Z',
  },
  rejectedEnrollmentsCount: {
    duplicateCount: 2,
    attemptLimitCount: 1,
    totalCount: 3,
  },
  administrativeCorrections: {
    recentEvents: [
      {
        eventId: 'evt-corr-01',
        occurredAt: '2026-08-19T10:00:00.000Z',
        actorIdentityId: 'admin-id-01',
        actorRole: 'administrador geral',
        targetIdentityId: 'user-id-01',
        targetNickname: 'aluno_ufu',
        action: 'CORRECAO_MATRICULA',
        outcome: 'PERMITIDO',
        hasReason: true,
        reason: 'Correção de dígito verificador solicitado via chamado',
      },
    ],
  },
  roleChanges: {
    recentEvents: [
      {
        eventId: 'evt-role-01',
        occurredAt: '2026-08-19T11:00:00.000Z',
        actorIdentityId: 'admin-id-01',
        actorRole: 'administrador geral',
        targetIdentityId: 'user-id-02',
        targetNickname: 'moderador_01',
        action: 'PAPEL_CONCEDIDO',
        outcome: 'PERMITIDO',
        hasReason: true,
      },
      {
        eventId: 'evt-role-02',
        occurredAt: '2026-08-19T12:00:00.000Z',
        actorIdentityId: 'admin-id-01',
        actorRole: 'administrador geral',
        targetIdentityId: 'admin-id-01',
        targetNickname: 'admin_01',
        action: 'AUTOCONCESSAO_PAPEL',
        outcome: 'BLOQUEADO',
        hasReason: false,
      },
    ],
  },
  unregisteredEnrollmentsCount: {
    count: 14,
  },
  unavailableBlocks: [],
};

describe('OperationsPanelPageComponent (Garantias de UI S4)', () => {
  let fixture: ComponentFixture<OperationsPanelPageComponent>;
  let component: OperationsPanelPageComponent;
  let store: OperationsPanelStore;
  let mockOperationalPanelService: { getOperationalPanel: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    mockOperationalPanelService = {
      getOperationalPanel: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [OperationsPanelPageComponent],
      providers: [
        OperationsApiAdapter,
        OperationsPanelStore,
        { provide: OperationalPanelService, useValue: mockOperationalPanelService },
      ],
    }).compileComponents();

    store = TestBed.inject(OperationsPanelStore);
  });

  function createComponent(): void {
    fixture = TestBed.createComponent(OperationsPanelPageComponent);
    component = fixture.componentInstance;
  }

  it('Garantia [populated]: renderiza os 4 blocos presentes com agregados, pendencias e listas de auditoria', async () => {
    mockOperationalPanelService.getOperationalPanel.mockReturnValue(of(MOCK_POPULATED_RESPONSE));
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    // Header
    expect(compiled.querySelector('.page-title')?.textContent).toContain('Painel operacional');
    expect(compiled.querySelector('.queried-at-label')).not.toBeNull();

    // 4 Blocos
    const cards = compiled.querySelectorAll('.panel-card');
    expect(cards.length).toBe(4);

    // Bloco 1: Recusas
    const rejectedCard = compiled.querySelector('.rejected-card');
    expect(rejectedCard).not.toBeNull();
    expect(rejectedCard?.querySelector('.aggregate-number')?.textContent).toContain('3');
    expect(rejectedCard?.querySelector('.aggregate-label')?.textContent).toContain(
      '3 recusas registradas',
    );
    expect(rejectedCard?.querySelector('.pending-chip')?.textContent).toContain('Pendência');

    // Bloco 2: Correções
    const correctionsCard = compiled.querySelector('.corrections-card');
    expect(correctionsCard).not.toBeNull();
    expect(correctionsCard?.textContent).toContain('aluno_ufu');
    expect(correctionsCard?.textContent).toContain('Correção de dígito verificador');
    expect(correctionsCard?.querySelector('.audit-actor')?.textContent).toContain('admin-id-01');

    // Bloco 3: Papéis
    const rolesCard = compiled.querySelector('.roles-card');
    expect(rolesCard).not.toBeNull();
    expect(rolesCard?.textContent).toContain('Tentativa bloqueada');
    expect(rolesCard?.textContent).toContain('Papel concedido');
    expect(rolesCard?.querySelector('.audit-actor')?.textContent).toContain('admin-id-01');

    // Bloco 4: Contas sem matrícula
    const unregisteredCard = compiled.querySelector('.unregistered-card');
    expect(unregisteredCard).not.toBeNull();
    expect(unregisteredCard?.querySelector('.aggregate-number')?.textContent).toContain('14');
    expect(unregisteredCard?.querySelector('.aggregate-label')?.textContent).toContain(
      '14 contas sem matrícula',
    );
  });

  it('Garantia [ausência no DOM]: blocos omitidos na resposta não existem no DOM', async () => {
    // Escopo parcial: somente contas sem matrícula
    const partialResponse: OperationalPanelResponse = {
      queriedAt: '2026-08-20T14:30:00.000Z',
      timeWindow: { since: '', until: '' },
      unregisteredEnrollmentsCount: { count: 8 },
      unavailableBlocks: [],
    };

    mockOperationalPanelService.getOperationalPanel.mockReturnValue(of(partialResponse));
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.unregistered-card')).not.toBeNull();
    expect(compiled.querySelector('.rejected-card')).toBeNull();
    expect(compiled.querySelector('.corrections-card')).toBeNull();
    expect(compiled.querySelector('.roles-card')).toBeNull();
  });

  it('Garantia [empty - escopo moderador]: exibe mensagem de escopo sem blocos', async () => {
    const emptyScopeResponse: OperationalPanelResponse = {
      queriedAt: '2026-08-20T14:30:00.000Z',
      timeWindow: { since: '', until: '' },
      unavailableBlocks: [],
    };

    mockOperationalPanelService.getOperationalPanel.mockReturnValue(of(emptyScopeResponse));
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.empty-state')).not.toBeNull();
    expect(compiled.querySelector('h2')?.textContent).toContain('Nenhum item no seu escopo');
    expect(compiled.querySelector('.empty-state p')?.textContent).toContain(
      'Seu papel não tem pendências abertas',
    );
    expect(compiled.querySelectorAll('.panel-card').length).toBe(0);
  });

  it('Garantia [empty - sem pendências]: exibe Nada pendente agora quando contagens são zero', async () => {
    const noPendingResponse: OperationalPanelResponse = {
      queriedAt: '2026-08-20T14:30:00.000Z',
      timeWindow: { since: '', until: '' },
      rejectedEnrollmentsCount: { duplicateCount: 0, attemptLimitCount: 0, totalCount: 0 },
      unregisteredEnrollmentsCount: { count: 0 },
      roleChanges: { recentEvents: [] },
      administrativeCorrections: { recentEvents: [] },
      unavailableBlocks: [],
    };

    mockOperationalPanelService.getOperationalPanel.mockReturnValue(of(noPendingResponse));
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.empty-pending-state')).not.toBeNull();
    expect(compiled.querySelector('h2')?.textContent).toContain('Nada pendente agora');
  });

  it('Garantia [loading]: desabilita o botão Atualizar painel e exibe Atualizando… preservando blocos existentes', async () => {
    mockOperationalPanelService.getOperationalPanel.mockReturnValue(of(MOCK_POPULATED_RESPONSE));
    createComponent();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const refreshBtn = compiled.querySelector('.btn-primary') as HTMLButtonElement;

    // Dispara refresh
    const refreshPromise = component.onRefresh();
    fixture.detectChanges();

    expect(refreshBtn.disabled).toBe(true);
    expect(refreshBtn.textContent).toContain('Atualizando…');

    await refreshPromise;
    fixture.detectChanges();

    expect(refreshBtn.disabled).toBe(false);
    expect(refreshBtn.textContent).toContain('Atualizar painel');
  });

  it('Garantia [error inicial]: exibe erro bloqueante com código de referência', async () => {
    mockOperationalPanelService.getOperationalPanel.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 500,
            error: { correlationId: 'corr-oper-fail-7788' },
          }),
      ),
    );
    createComponent();
    fixture.detectChanges();

    // Aguarda retries
    await vi.waitFor(
      () => {
        expect(store.isError()).toBe(true);
      },
      { timeout: 3000 },
    );
    expect(mockOperationalPanelService.getOperationalPanel).toHaveBeenCalledTimes(3);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.error-state')).not.toBeNull();
    expect(compiled.querySelector('.error-text')?.textContent).toContain(
      'Não foi possível carregar o painel. Tente novamente.',
    );
    expect(compiled.querySelector('.code-value')?.textContent).toContain('corr-oper-fail-7788');
    expect(compiled.querySelector('.btn-copy')).not.toBeNull();
  });

  it('Garantia [error atualização / stale]: preserva dados anteriores e exibe aviso de desatualização', async () => {
    // 1ª carga: sucesso
    mockOperationalPanelService.getOperationalPanel.mockReturnValue(of(MOCK_POPULATED_RESPONSE));
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('.panel-card').length).toBe(4);

    // 2ª carga: falha de conexão
    mockOperationalPanelService.getOperationalPanel.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 503,
            error: { correlationId: 'corr-stale-0099' },
          }),
      ),
    );

    await component.onRefresh();
    fixture.detectChanges();

    // Blocos anteriores continuam renderizados
    expect(compiled.querySelectorAll('.panel-card').length).toBe(4);

    // Banner de dados anteriores é visível
    expect(compiled.querySelector('.stale-warning')).not.toBeNull();
    expect(compiled.querySelector('.stale-note')?.textContent).toContain(
      'Dados da última consulta; atualização não concluída.',
    );
    expect(compiled.querySelector('.stale-message')?.textContent).toContain(
      'Não foi possível atualizar o painel. Verifique a conexão e tente novamente.',
    );
    expect(compiled.querySelector('.code-inline')?.textContent).toContain('corr-stale-0099');
  });

  it('Garantia [clipboard & aria-live]: copia correlationId e emite anúncio acessível', async () => {
    mockOperationalPanelService.getOperationalPanel.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 500,
            error: { correlationId: 'corr-copy-test-11' },
          }),
      ),
    );
    createComponent();
    fixture.detectChanges();

    await vi.waitFor(
      () => {
        expect(store.isError()).toBe(true);
      },
      { timeout: 3000 },
    );
    fixture.detectChanges();

    const writeTextSpy = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, {
      clipboard: {
        writeText: writeTextSpy,
      },
    });

    await component.copyCorrelationId('corr-copy-test-11');
    fixture.detectChanges();

    expect(writeTextSpy).toHaveBeenCalledWith('corr-copy-test-11');
    expect(component.copyStatus()).toBe('copied');

    const liveRegion = (fixture.nativeElement as HTMLElement).querySelector(
      '#operations-live-region',
    );
    expect(liveRegion?.textContent).toContain('Código de referência copiado com sucesso.');
  });

  it('Garantia [auditoria de papéis]: renderiza ações bloqueadas reais do backend com ícone de erro e destaque', async () => {
    const roleBlockedResponse: OperationalPanelResponse = {
      queriedAt: '2026-08-20T14:30:00.000Z',
      timeWindow: { since: '', until: '' },
      roleChanges: {
        recentEvents: [
          {
            eventId: 'evt-role-last-admin',
            occurredAt: '2026-08-20T12:00:00.000Z',
            actorIdentityId: 'admin-id-01',
            actorRole: 'administrador geral',
            targetIdentityId: 'admin-id-01',
            targetNickname: 'ultimo_admin',
            action: 'REVOGACAO_ULTIMO_ADMINISTRADOR',
            outcome: 'BLOQUEADO',
            hasReason: false,
          },
          {
            eventId: 'evt-role-general-admin',
            occurredAt: '2026-08-20T12:05:00.000Z',
            actorIdentityId: 'admin-id-01',
            actorRole: 'administrador geral',
            targetIdentityId: 'admin-id-01',
            targetNickname: 'admin_geral',
            action: 'ULTIMO_ADMINISTRADOR_GERAL',
            outcome: 'BLOQUEADO',
            hasReason: false,
          },
        ],
      },
      unavailableBlocks: [],
    };

    mockOperationalPanelService.getOperationalPanel.mockReturnValue(of(roleBlockedResponse));
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const rolesCard = compiled.querySelector('.roles-card');
    expect(rolesCard).not.toBeNull();

    const blockedBadges = rolesCard?.querySelectorAll('.badge-blocked');
    expect(blockedBadges?.length).toBe(2);
    expect(rolesCard?.textContent).toContain('Tentativa bloqueada');
    expect(rolesCard?.textContent).toContain('ultimo_admin');
    expect(rolesCard?.textContent).toContain('admin_geral');
  });

  it('Garantia discriminante [401]: sucesso seguido de 401 não faz retry, limpa snapshots e deixa zero cartões no DOM', async () => {
    // 1ª carga: sucesso populado com 4 cartões
    mockOperationalPanelService.getOperationalPanel.mockReturnValue(of(MOCK_POPULATED_RESPONSE));
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('.panel-card').length).toBe(4);
    expect(mockOperationalPanelService.getOperationalPanel).toHaveBeenCalledTimes(1);

    // 2ª carga: 401 Unauthorized
    mockOperationalPanelService.getOperationalPanel.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 401,
            error: { correlationId: 'corr-401-test' },
          }),
      ),
    );

    await component.onRefresh();
    fixture.detectChanges();

    // Prova de zero retentativas (exatamente 1 chamada na segunda consulta, total 2)
    expect(mockOperationalPanelService.getOperationalPanel).toHaveBeenCalledTimes(2);

    // Prova de limpeza total do snapshot (zero cartões operacionais no DOM)
    expect(store.isError()).toBe(true);
    expect(store.displaySnapshot()).toBeNull();
    expect(compiled.querySelectorAll('.panel-card').length).toBe(0);
    expect(compiled.querySelector('.operations-grid')).toBeNull();
    expect(compiled.querySelector('.audit-item')).toBeNull();
    expect(compiled.querySelector('.aggregate-number')).toBeNull();
    expect(compiled.querySelector('.stale-warning')).toBeNull();

    // Mensagem segura de 401 do contrato
    expect(compiled.querySelector('.error-state')).not.toBeNull();
    expect(compiled.querySelector('.error-text')?.textContent).toContain(
      'Sua sessão expirou. Entre novamente para continuar.',
    );
  });

  it('Garantia discriminante [403]: sucesso seguido de 403 não faz retry, limpa snapshots e deixa zero cartões no DOM', async () => {
    // 1ª carga: sucesso populado com 4 cartões
    mockOperationalPanelService.getOperationalPanel.mockReturnValue(of(MOCK_POPULATED_RESPONSE));
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('.panel-card').length).toBe(4);
    expect(mockOperationalPanelService.getOperationalPanel).toHaveBeenCalledTimes(1);

    // 2ª carga: 403 Forbidden
    mockOperationalPanelService.getOperationalPanel.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 403,
            error: { correlationId: 'corr-403-test' },
          }),
      ),
    );

    await component.onRefresh();
    fixture.detectChanges();

    // Prova de zero retentativas (exatamente 1 chamada na segunda consulta, total 2)
    expect(mockOperationalPanelService.getOperationalPanel).toHaveBeenCalledTimes(2);

    // Prova de limpeza total do snapshot (zero cartões operacionais no DOM)
    expect(store.isError()).toBe(true);
    expect(store.displaySnapshot()).toBeNull();
    expect(compiled.querySelectorAll('.panel-card').length).toBe(0);
    expect(compiled.querySelector('.stale-warning')).toBeNull();

    // Mensagem segura de 403 do contrato
    expect(compiled.querySelector('.error-state')).not.toBeNull();
    expect(compiled.querySelector('.error-text')?.textContent?.trim()).toBe(
      'Você não tem acesso a esta área.',
    );
    expect(compiled.textContent).not.toMatch(
      /ADMINISTRADOR|VIEW_OPERATIONAL_PANEL|MANAGE_ROLES|CORRECT_ENROLLMENT|admin-id-|user-id-|aluno_ufu|moderador_01/u,
    );
  });

  it('Garantia [unavailableBlocks & partial]: exibe mensagem segura no cartão indisponível e preserva os outros blocos', async () => {
    const partialUnavailableResponse: OperationalPanelResponse = {
      queriedAt: '2026-08-20T14:30:00.000Z',
      timeWindow: { since: '', until: '' },
      administrativeCorrections: {
        recentEvents: [
          {
            eventId: 'evt-corr-01',
            occurredAt: '2026-08-19T10:00:00.000Z',
            action: 'CORRECAO_MATRICULA',
            outcome: 'PERMITIDO',
            hasReason: true,
            reason: 'Correção de teste',
          },
        ],
      },
      unavailableBlocks: [OperationalPanelResponse.UnavailableBlocksEnum.RoleChanges],
    };

    mockOperationalPanelService.getOperationalPanel.mockReturnValue(of(partialUnavailableResponse));
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    // Estado partial ativado
    expect(store.isPartial()).toBe(true);

    // Bloco populado disponível
    const correctionsCard = compiled.querySelector('.corrections-card');
    expect(correctionsCard).not.toBeNull();
    expect(correctionsCard?.classList.contains('card-unavailable')).toBe(false);

    // Bloco indisponível renderiza mensagem segura
    const rolesCard = compiled.querySelector('.roles-card');
    expect(rolesCard).not.toBeNull();
    expect(rolesCard?.classList.contains('card-unavailable')).toBe(true);
    expect(rolesCard?.textContent).toContain('Alterações de papel');
    expect(rolesCard?.textContent).toContain('Este bloco não pôde ser carregado.');

    // Blocos ausentes fora do escopo não existem no DOM
    expect(compiled.querySelector('.rejected-card')).toBeNull();
    expect(compiled.querySelector('.unregistered-card')).toBeNull();
  });

  it('Garantia [interpolação Angular do motivo]: preserva caracteres < e > textualmente sem interpretar HTML', async () => {
    const literalReason = 'Motivo <especial> com <b>tags</b> e caracteres < e >';
    const auditResponse: OperationalPanelResponse = {
      queriedAt: '2026-08-20T14:30:00.000Z',
      timeWindow: { since: '', until: '' },
      administrativeCorrections: {
        recentEvents: [
          {
            eventId: 'evt-corr-literal',
            occurredAt: '2026-08-19T10:00:00.000Z',
            actorRole: 'administrador geral',
            targetNickname: 'aluno_teste',
            action: 'CORRECAO_MATRICULA',
            outcome: 'PERMITIDO',
            hasReason: true,
            reason: literalReason,
          },
        ],
      },
      unavailableBlocks: [],
    };

    mockOperationalPanelService.getOperationalPanel.mockReturnValue(of(auditResponse));
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const reasonElement = compiled.querySelector('.reason-text');

    expect(reasonElement).not.toBeNull();
    expect(reasonElement?.textContent).toBe(literalReason);
    // Assegura que <b> não foi interpretado como elemento HTML no DOM
    expect(reasonElement?.querySelector('b')).toBeNull();
  });
});
