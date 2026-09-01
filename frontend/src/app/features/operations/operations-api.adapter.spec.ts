import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { OperationalPanelService } from '../../api/generated/api/operational-panel.service';
import { OperationalCapabilitiesService } from '../../api/generated/api/operational-capabilities.service';
import { RoleService } from '../../api/generated/api/role.service';
import { OperationalPanelResponse } from '../../api/generated/model/operational-panel-response';
import { OperationsApiAdapter } from './operations-api.adapter';

const MOCK_FULL_PANEL_RESPONSE: OperationalPanelResponse = {
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

describe('OperationsApiAdapter (Contrato e Mapeamento S4)', () => {
  let adapter: OperationsApiAdapter;
  let mockOperationalPanelService: { getOperationalPanel: ReturnType<typeof vi.fn> };
  let mockOperationalCapabilitiesService: { getOperationalCapabilities: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    mockOperationalPanelService = {
      getOperationalPanel: vi.fn(),
    };
    mockOperationalCapabilitiesService = {
      getOperationalCapabilities: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        OperationsApiAdapter,
        { provide: OperationalPanelService, useValue: mockOperationalPanelService },
        { provide: OperationalCapabilitiesService, useValue: mockOperationalCapabilitiesService },
        { provide: RoleService, useValue: {} },
      ],
    });

    adapter = TestBed.inject(OperationsApiAdapter);
  });

  it('adapta com sucesso payload com todos os 4 blocos presentes (administrador geral)', () => {
    const adapted = adapter.adapt(MOCK_FULL_PANEL_RESPONSE);

    expect(adapted).not.toBeNull();
    expect(adapted?.hasAnyBlock).toBe(true);
    expect(adapted?.hasAnyPending).toBe(true);

    // Bloco 1: Recusas de matrícula
    expect(adapted?.rejectedEnrollments).not.toBeNull();
    expect(adapted?.rejectedEnrollments?.totalCount).toBe(3);
    expect(adapted?.rejectedEnrollments?.duplicateCount).toBe(2);
    expect(adapted?.rejectedEnrollments?.attemptLimitCount).toBe(1);
    expect(adapted?.rejectedEnrollments?.label).toBe('3 recusas registradas');
    expect(adapted?.rejectedEnrollments?.duplicateLabel).toBe('2 por duplicidade');
    expect(adapted?.rejectedEnrollments?.attemptLimitLabel).toBe('1 por limite de tentativas');
    expect(adapted?.rejectedEnrollments?.hasPending).toBe(true);

    // Bloco 2: Correções administrativas
    expect(adapted?.administrativeCorrections).not.toBeNull();
    expect(adapted?.administrativeCorrections?.totalCount).toBe(1);
    expect(adapted?.administrativeCorrections?.label).toBe('1 correção registrada');
    expect(adapted?.administrativeCorrections?.events[0].targetNickname).toBe('aluno_ufu');
    expect(adapted?.administrativeCorrections?.events[0].reason).toContain(
      'Correção de dígito verificador',
    );

    // Bloco 3: Alterações de papel
    expect(adapted?.roleChanges).not.toBeNull();
    expect(adapted?.roleChanges?.totalCount).toBe(2);
    expect(adapted?.roleChanges?.blockedCount).toBe(1);
    expect(adapted?.roleChanges?.hasPending).toBe(true);
    expect(adapted?.roleChanges?.events[0].action).toBe('PAPEL_CONCEDIDO');
    expect(adapted?.roleChanges?.events[0].actionLabel).toBe('Papel concedido');
    expect(adapted?.roleChanges?.events[0].outcome).toBe('PERMITIDO');
    expect(adapted?.roleChanges?.events[0].isBlocked).toBe(false);
    expect(adapted?.roleChanges?.events[1].action).toBe('AUTOCONCESSAO_PAPEL');
    expect(adapted?.roleChanges?.events[1].actionLabel).toBe('Autoconcessão de papel');
    expect(adapted?.roleChanges?.events[1].outcome).toBe('BLOQUEADO');
    expect(adapted?.roleChanges?.events[1].isBlocked).toBe(true);

    // Bloco 4: Contas sem matrícula
    expect(adapted?.unregisteredEnrollments).not.toBeNull();
    expect(adapted?.unregisteredEnrollments?.count).toBe(14);
    expect(adapted?.unregisteredEnrollments?.label).toBe('14 contas sem matrícula');
    expect(adapted?.unregisteredEnrollments?.hasPending).toBe(true);
  });

  it('adapta resposta parcial omitindo blocos ausentes (ausência no servidor -> null no ViewModel)', () => {
    // Exemplo: Administrador de competição só recebe unregisteredEnrollmentsCount
    const partialResponse: OperationalPanelResponse = {
      queriedAt: '2026-08-20T14:30:00.000Z',
      timeWindow: {
        since: '2026-08-13T00:00:00.000Z',
        until: '2026-08-20T14:30:00.000Z',
      },
      unregisteredEnrollmentsCount: {
        count: 5,
      },
      unavailableBlocks: [],
    };

    const adapted = adapter.adapt(partialResponse);

    expect(adapted).not.toBeNull();
    expect(adapted?.hasAnyBlock).toBe(true);
    expect(adapted?.rejectedEnrollments).toBeNull();
    expect(adapted?.administrativeCorrections).toBeNull();
    expect(adapted?.roleChanges).toBeNull();
    expect(adapted?.unregisteredEnrollments).not.toBeNull();
    expect(adapted?.unregisteredEnrollments?.count).toBe(5);
  });

  it('adapta resposta sem blocos para escopo de moderador (hasAnyBlock = false)', () => {
    const emptyScopeResponse: OperationalPanelResponse = {
      queriedAt: '2026-08-20T14:30:00.000Z',
      timeWindow: {
        since: '2026-08-13T00:00:00.000Z',
        until: '2026-08-20T14:30:00.000Z',
      },
      unavailableBlocks: [],
    };

    const adapted = adapter.adapt(emptyScopeResponse);

    expect(adapted).not.toBeNull();
    expect(adapted?.hasAnyBlock).toBe(false);
    expect(adapted?.rejectedEnrollments).toBeNull();
    expect(adapted?.administrativeCorrections).toBeNull();
    expect(adapted?.roleChanges).toBeNull();
    expect(adapted?.unregisteredEnrollments).toBeNull();
  });

  it('retorna null para resposta vazia ou indefinida', () => {
    expect(adapter.adapt(null)).toBeNull();
    expect(adapter.adapt(undefined)).toBeNull();
  });

  it('aplica concordância zero-one-many para recusas de matrícula', () => {
    // 0 recusas
    const zeroAdapted = adapter.adapt({
      queriedAt: '2026-08-20T14:30:00.000Z',
      timeWindow: { since: '', until: '' },
      rejectedEnrollmentsCount: { duplicateCount: 0, attemptLimitCount: 0, totalCount: 0 },
      unavailableBlocks: [],
    });
    expect(zeroAdapted?.rejectedEnrollments?.label).toBe('0 recusas registradas');
    expect(zeroAdapted?.rejectedEnrollments?.hasPending).toBe(false);

    // 1 recusa
    const oneAdapted = adapter.adapt({
      queriedAt: '2026-08-20T14:30:00.000Z',
      timeWindow: { since: '', until: '' },
      rejectedEnrollmentsCount: { duplicateCount: 1, attemptLimitCount: 0, totalCount: 1 },
      unavailableBlocks: [],
    });
    expect(oneAdapted?.rejectedEnrollments?.label).toBe('1 recusa registrada');
    expect(oneAdapted?.rejectedEnrollments?.duplicateLabel).toBe('1 por duplicidade');
    expect(oneAdapted?.rejectedEnrollments?.hasPending).toBe(true);

    // Múltiplas recusas
    const manyAdapted = adapter.adapt({
      queriedAt: '2026-08-20T14:30:00.000Z',
      timeWindow: { since: '', until: '' },
      rejectedEnrollmentsCount: { duplicateCount: 4, attemptLimitCount: 6, totalCount: 10 },
      unavailableBlocks: [],
    });
    expect(manyAdapted?.rejectedEnrollments?.label).toBe('10 recusas registradas');
    expect(manyAdapted?.rejectedEnrollments?.duplicateLabel).toBe('4 por duplicidade');
    expect(manyAdapted?.rejectedEnrollments?.attemptLimitLabel).toBe('6 por limite de tentativas');
  });

  it('aplica concordância zero-one-many para contas sem matrícula', () => {
    // 0
    const zero = adapter.adapt({
      queriedAt: '2026-08-20T14:30:00.000Z',
      timeWindow: { since: '', until: '' },
      unregisteredEnrollmentsCount: { count: 0 },
      unavailableBlocks: [],
    });
    expect(zero?.unregisteredEnrollments?.label).toBe('0 contas sem matrícula');
    expect(zero?.unregisteredEnrollments?.hasPending).toBe(false);

    // 1
    const one = adapter.adapt({
      queriedAt: '2026-08-20T14:30:00.000Z',
      timeWindow: { since: '', until: '' },
      unregisteredEnrollmentsCount: { count: 1 },
      unavailableBlocks: [],
    });
    expect(one?.unregisteredEnrollments?.label).toBe('1 conta sem matrícula');
    expect(one?.unregisteredEnrollments?.hasPending).toBe(true);

    // Many
    const many = adapter.adapt({
      queriedAt: '2026-08-20T14:30:00.000Z',
      timeWindow: { since: '', until: '' },
      unregisteredEnrollmentsCount: { count: 42 },
      unavailableBlocks: [],
    });
    expect(many?.unregisteredEnrollments?.label).toBe('42 contas sem matrícula');
  });

  it('mapeia corretamente todo o vocabulário real do backend e rótulos de alteração de papel', () => {
    const response: OperationalPanelResponse = {
      queriedAt: '2026-08-20T14:30:00.000Z',
      timeWindow: { since: '', until: '' },
      roleChanges: {
        recentEvents: [
          {
            eventId: 'evt-1',
            occurredAt: '2026-08-20T10:00:00.000Z',
            action: 'PAPEL_CONCEDIDO',
            outcome: 'PERMITIDO',
            hasReason: false,
          },
          {
            eventId: 'evt-2',
            occurredAt: '2026-08-20T10:05:00.000Z',
            action: 'PAPEL_REVOGADO',
            outcome: 'PERMITIDO',
            hasReason: false,
          },
          {
            eventId: 'evt-3',
            occurredAt: '2026-08-20T10:10:00.000Z',
            action: 'AUTOCONCESSAO_PAPEL',
            outcome: 'BLOQUEADO',
            hasReason: false,
          },
          {
            eventId: 'evt-4',
            occurredAt: '2026-08-20T10:15:00.000Z',
            action: 'ULTIMO_ADMINISTRADOR_GERAL',
            outcome: 'BLOQUEADO',
            hasReason: false,
          },
          {
            eventId: 'evt-5',
            occurredAt: '2026-08-20T10:20:00.000Z',
            action: 'REVOGACAO_ULTIMO_ADMINISTRADOR',
            outcome: 'BLOQUEADO',
            hasReason: false,
          },
          {
            eventId: 'evt-6',
            occurredAt: '2026-08-20T10:25:00.000Z',
            action: 'AUTOCONCESSAO_BLOQUEADA',
            outcome: 'BLOCKED',
            hasReason: false,
          },
          {
            eventId: 'evt-7',
            occurredAt: '2026-08-20T10:30:00.000Z',
            action: 'ULTIMO_ADMIN_BLOQUEADO',
            outcome: 'BLOQUEADO',
            hasReason: false,
          },
        ],
      },
      unavailableBlocks: [],
    };

    const adapted = adapter.adapt(response);
    const events = adapted?.roleChanges?.events ?? [];

    expect(events.length).toBe(7);

    // 1. PAPEL_CONCEDIDO
    expect(events[0].actionLabel).toBe('Papel concedido');
    expect(events[0].isBlocked).toBe(false);

    // 2. PAPEL_REVOGADO
    expect(events[1].actionLabel).toBe('Papel revogado');
    expect(events[1].isBlocked).toBe(false);

    // 3. AUTOCONCESSAO_PAPEL (com outcome BLOQUEADO)
    expect(events[2].actionLabel).toBe('Autoconcessão de papel');
    expect(events[2].isBlocked).toBe(true);

    // 4. ULTIMO_ADMINISTRADOR_GERAL (com outcome BLOQUEADO)
    expect(events[3].actionLabel).toBe('Último administrador geral');
    expect(events[3].isBlocked).toBe(true);

    // 5. REVOGACAO_ULTIMO_ADMINISTRADOR (com outcome BLOQUEADO)
    expect(events[4].actionLabel).toBe('Revogação do último administrador');
    expect(events[4].isBlocked).toBe(true);

    // 6. Defensivo legado: AUTOCONCESSAO_BLOQUEADA / BLOCKED
    expect(events[5].actionLabel).toBe('Autoconcessão bloqueada');
    expect(events[5].isBlocked).toBe(true);

    // 7. Defensivo legado: ULTIMO_ADMIN_BLOQUEADO
    expect(events[6].actionLabel).toBe('Último administrador bloqueado');
    expect(events[6].isBlocked).toBe(true);
  });

  it('preserva literalmente o texto do motivo da correção administrativa com caracteres < e >', () => {
    const literalReason = '<script>alert("xss")</script>Motivo com delimitadores < e > preservados';
    const auditResponse: OperationalPanelResponse = {
      queriedAt: '2026-08-20T14:30:00.000Z',
      timeWindow: { since: '', until: '' },
      administrativeCorrections: {
        recentEvents: [
          {
            eventId: 'evt-01',
            occurredAt: '2026-08-20T14:00:00.000Z',
            action: 'CORRECAO_MATRICULA',
            outcome: 'PERMITIDO',
            hasReason: true,
            reason: literalReason,
          },
        ],
      },
      unavailableBlocks: [],
    };

    const adapted = adapter.adapt(auditResponse);
    expect(adapted?.administrativeCorrections?.events[0].reason).toBe(literalReason);
  });

  it('mapeia unavailableBlocks e define flags de indisponibilidade isolada para blocos autorizados que falharam', () => {
    const partialFailureResponse: OperationalPanelResponse = {
      queriedAt: '2026-08-20T14:30:00.000Z',
      timeWindow: { since: '', until: '' },
      administrativeCorrections: {
        recentEvents: [],
      },
      unavailableBlocks: [
        OperationalPanelResponse.UnavailableBlocksEnum.RoleChanges,
        OperationalPanelResponse.UnavailableBlocksEnum.RejectedEnrollmentsCount,
      ],
    };

    const adapted = adapter.adapt(partialFailureResponse);

    expect(adapted).not.toBeNull();
    expect(adapted?.hasAnyBlock).toBe(true);
    expect(adapted?.unavailableBlocks).toEqual(['ROLE_CHANGES', 'REJECTED_ENROLLMENTS_COUNT']);
    expect(adapted?.isRoleChangesUnavailable).toBe(true);
    expect(adapted?.isRejectedEnrollmentsUnavailable).toBe(true);
    expect(adapted?.isAdministrativeCorrectionsUnavailable).toBe(false);
    expect(adapted?.isUnregisteredEnrollmentsUnavailable).toBe(false);
    expect(adapted?.unavailableBlockMessage).toBe('Este bloco não pôde ser carregado.');

    // Bloco presente continua populado
    expect(adapted?.administrativeCorrections).not.toBeNull();
    // Bloco ausente e não listado em unavailableBlocks permanece nulo
    expect(adapted?.unregisteredEnrollments).toBeNull();
  });

  it('chama fetchOperationalPanel delegando para o servico gerado', async () => {
    mockOperationalPanelService.getOperationalPanel.mockReturnValue(of(MOCK_FULL_PANEL_RESPONSE));

    const result = await new Promise((resolve) => {
      adapter.fetchOperationalPanel().subscribe((res) => resolve(res));
    });

    expect(mockOperationalPanelService.getOperationalPanel).toHaveBeenCalledTimes(1);
    expect(result).not.toBeNull();
  });

  it('consulta capacidades pelo cliente gerado, ordena valores unicos e rejeita o valor duplicado', async () => {
    mockOperationalCapabilitiesService.getOperationalCapabilities.mockReturnValue(
      of({
        capabilities: [
          'CORRECT_ENROLLMENT',
          'VIEW_OPERATIONAL_PANEL',
          'MANAGE_ROLES',
          'VIEW_OPERATIONAL_PANEL',
        ],
      }),
    );

    const result = await new Promise<readonly string[]>((resolve) => {
      adapter.fetchOperationalCapabilities().subscribe(resolve);
    });

    expect(mockOperationalCapabilitiesService.getOperationalCapabilities).toHaveBeenCalledTimes(1);
    expect(result).toEqual(['MANAGE_ROLES', 'CORRECT_ENROLLMENT']);
  });

  it('falha fechado para valores desconhecidos, nulos, papel e identidade sem promover autoridade', () => {
    const result = adapter.adaptOperationalCapabilities({
      capabilities: [
        null,
        'ADMINISTRADOR_GERAL',
        'identity-123',
        'UNKNOWN_CAPABILITY',
        'MANAGE_ROLES',
      ],
      role: 'ADMINISTRADOR_GERAL',
      identityId: 'identity-123',
    });

    expect(result).toEqual(['MANAGE_ROLES']);
  });

  it.each([
    null,
    undefined,
    {},
    { capabilities: null },
    { capabilities: 'VIEW_OPERATIONAL_PANEL' },
    { capabilities: new Set(['VIEW_OPERATIONAL_PANEL']) },
  ])('trata payload de capacidades malformado como nenhuma permissao: %j', (response) => {
    expect(adapter.adaptOperationalCapabilities(response)).toEqual([]);
  });
});
