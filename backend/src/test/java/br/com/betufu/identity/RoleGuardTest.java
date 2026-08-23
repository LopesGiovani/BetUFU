package br.com.betufu.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import br.com.betufu.audit.AuditEntry;
import br.com.betufu.audit.AuditRecorder;
import br.com.betufu.identity.OperationalRoleQuery.GrantedRole;
import br.com.betufu.identity.internal.BetufuProfile;
import br.com.betufu.identity.internal.BetufuProfileService;
import br.com.betufu.identity.internal.OperationalRoleGrant;
import br.com.betufu.identity.internal.OperationalRoleGrantRepository;
import br.com.betufu.identity.internal.RoleGrantResponse;
import br.com.betufu.identity.internal.RoleGrantService;
import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.session.events.OperationalRoleChanged;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

class RoleGuardTest {

    private OperationalRoleGrantRepository repository;
    private BetufuProfileService profileService;
    private AuditRecorder auditRecorder;
    private ApplicationEventPublisher eventPublisher;
    private RoleGrantService service;

    @BeforeEach
    void setUp() {
        repository = mock(OperationalRoleGrantRepository.class);
        profileService = mock(BetufuProfileService.class);
        auditRecorder = mock(AuditRecorder.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new RoleGrantService(repository, profileService, auditRecorder, eventPublisher);
    }

    @ParameterizedTest
    @EnumSource(OperationalRole.class)
    @DisplayName("Guarda (a) - Autoconcessão é bloqueada e auditada para qualquer um dos três papéis (D-48a)")
    void selfGrantIsBlockedAndAuditedForAllRoles(OperationalRole role) {
        UUID actorAndTargetId = UUID.randomUUID();

        when(repository.findActiveByIdentityId(actorAndTargetId))
                .thenReturn(List.of(createAdminGrant(actorAndTargetId)));

        assertThatThrownBy(() -> service.grantRole(actorAndTargetId, actorAndTargetId, role, null))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> {
                    ApiProblemException apiEx = (ApiProblemException) ex;
                    assertThat(apiEx.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.errorCode()).isEqualTo(ApiErrorCode.SELF_GRANT_BLOCKED);
                });

        verify(repository, never()).insertGrant(any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());

        ArgumentCaptor<AuditEntry> auditCaptor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRecorder).record(auditCaptor.capture());

        AuditEntry entry = auditCaptor.getValue();
        assertThat(entry.actorIdentityId()).isEqualTo(actorAndTargetId);
        assertThat(entry.targetIdentityId()).isEqualTo(actorAndTargetId);
        assertThat(entry.action()).isEqualTo(AuditEntry.ACTION_AUTOCONCESSAO_PAPEL);
        assertThat(entry.outcome()).isEqualTo(AuditEntry.OUTCOME_BLOCKED);
        assertThat(entry.newValue()).isEqualTo(role.name());
    }

    @Test
    @DisplayName("Concessão para outra pessoa passa com sucesso, audita permissão e publica evento")
    void grantToOtherIdentitySucceedsAndAuditsAndPublishesEvent() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String targetSubject = "test-target-subject";

        when(repository.findActiveByIdentityId(actorId))
                .thenReturn(List.of(createAdminGrant(actorId)));
        UUID moderationScope = UUID.fromString("7f2b1c44-0d3e-4a91-9c15-2b8e6d0f4a77");
        when(repository.insertGrant(targetId, "MODERADOR", moderationScope)).thenReturn(1);
        when(repository.findSubjectByIdentityId(targetId)).thenReturn(Optional.of(targetSubject));
        when(profileService.findProfile(targetId)).thenReturn(Optional.empty());

        RoleGrantResponse response = service.grantRole(actorId, targetId, OperationalRole.MODERADOR, moderationScope);

        assertThat(response.identityId()).isEqualTo(targetId);
        verify(repository).insertGrant(eq(targetId), eq("MODERADOR"), eq(moderationScope));

        ArgumentCaptor<AuditEntry> auditCaptor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRecorder).record(auditCaptor.capture());
        AuditEntry entry = auditCaptor.getValue();
        assertThat(entry.actorIdentityId()).isEqualTo(actorId);
        assertThat(entry.targetIdentityId()).isEqualTo(targetId);
        assertThat(entry.action()).isEqualTo(AuditEntry.ACTION_PAPEL_CONCEDIDO);
        assertThat(entry.outcome()).isEqualTo(AuditEntry.OUTCOME_PERMITTED);

        ArgumentCaptor<OperationalRoleChanged> eventCaptor = ArgumentCaptor.forClass(OperationalRoleChanged.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        OperationalRoleChanged event = eventCaptor.getValue();
        assertThat(event.targetIdentityId()).isEqualTo(targetId);
        assertThat(event.targetSubject()).isEqualTo(targetSubject);
        assertThat(event.role()).isEqualTo("MODERADOR");
        assertThat(event.action()).isEqualTo("CONCEDIDO");
        assertThat(event.actorIdentityId()).isEqualTo(actorId);
    }

    @Test
    @DisplayName("Guarda (b) - Revogar o próprio papel de administrador geral com 1 administrador vigente é bloqueado e auditado (D-48b)")
    void lastAdminRevocationIsBlockedWhenSingleAdminVigente() {
        UUID actorAndTargetId = UUID.randomUUID();

        OperationalRoleGrant singleAdminGrant = createAdminGrant(actorAndTargetId);

        when(repository.findActiveByIdentityId(actorAndTargetId))
                .thenReturn(List.of(singleAdminGrant));
        when(repository.findActiveRolesForUpdate(OperationalRole.ADMINISTRADOR_GERAL))
                .thenReturn(List.of(singleAdminGrant));

        assertThatThrownBy(() -> service.revokeRole(
                actorAndTargetId, actorAndTargetId, OperationalRole.ADMINISTRADOR_GERAL, null))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> {
                    ApiProblemException apiEx = (ApiProblemException) ex;
                    assertThat(apiEx.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiEx.errorCode()).isEqualTo(ApiErrorCode.LAST_ADMIN_REVOCATION_BLOCKED);
                });

        verify(repository, never()).revokeGrant(any(), any());
        verify(eventPublisher, never()).publishEvent(any());

        ArgumentCaptor<AuditEntry> auditCaptor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRecorder).record(auditCaptor.capture());
        AuditEntry entry = auditCaptor.getValue();
        assertThat(entry.actorIdentityId()).isEqualTo(actorAndTargetId);
        assertThat(entry.targetIdentityId()).isEqualTo(actorAndTargetId);
        assertThat(entry.action()).isEqualTo(AuditEntry.ACTION_REVOGACAO_ULTIMO_ADMINISTRADOR);
        assertThat(entry.outcome()).isEqualTo(AuditEntry.OUTCOME_BLOCKED);
    }

    @Test
    @DisplayName("Guarda (b) - Revogar o próprio papel de administrador geral com 2 administradores vigentes passa com sucesso")
    void lastAdminRevocationPassesWhenTwoAdminsVigente() {
        UUID actorAndTargetId = UUID.randomUUID();
        UUID otherAdminId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();
        String targetSubject = "admin-target-subject";

        OperationalRoleGrant admin1 = new OperationalRoleGrant(
                grantId,
                actorAndTargetId,
                OperationalRole.ADMINISTRADOR_GERAL,
                null,
                OperationalRoleGrant.STATUS_VIGENTE,
                Instant.now(),
                null);

        OperationalRoleGrant admin2 = new OperationalRoleGrant(
                UUID.randomUUID(),
                otherAdminId,
                OperationalRole.ADMINISTRADOR_GERAL,
                null,
                OperationalRoleGrant.STATUS_VIGENTE,
                Instant.now(),
                null);

        when(repository.findActiveByIdentityId(actorAndTargetId))
                .thenReturn(List.of(admin1));
        when(repository.findActiveRolesForUpdate(OperationalRole.ADMINISTRADOR_GERAL))
                .thenReturn(List.of(admin1, admin2));
        when(repository.findActiveGrant(actorAndTargetId, OperationalRole.ADMINISTRADOR_GERAL, null))
                .thenReturn(Optional.of(admin1));
        when(repository.findSubjectByIdentityId(actorAndTargetId))
                .thenReturn(Optional.of(targetSubject));

        RoleGrantResponse response = service.revokeRole(
                actorAndTargetId, actorAndTargetId, OperationalRole.ADMINISTRADOR_GERAL, null);

        assertThat(response.identityId()).isEqualTo(actorAndTargetId);
        verify(repository).revokeGrant(eq(grantId), any());

        ArgumentCaptor<AuditEntry> auditCaptor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRecorder).record(auditCaptor.capture());
        AuditEntry entry = auditCaptor.getValue();
        assertThat(entry.actorIdentityId()).isEqualTo(actorAndTargetId);
        assertThat(entry.targetIdentityId()).isEqualTo(actorAndTargetId);
        assertThat(entry.action()).isEqualTo(AuditEntry.ACTION_PAPEL_REVOGADO);
        assertThat(entry.outcome()).isEqualTo(AuditEntry.OUTCOME_PERMITTED);

        ArgumentCaptor<OperationalRoleChanged> eventCaptor = ArgumentCaptor.forClass(OperationalRoleChanged.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        OperationalRoleChanged event = eventCaptor.getValue();
        assertThat(event.targetIdentityId()).isEqualTo(actorAndTargetId);
        assertThat(event.targetSubject()).isEqualTo(targetSubject);
        assertThat(event.role()).isEqualTo("ADMINISTRADOR_GERAL");
        assertThat(event.action()).isEqualTo("REVOGADO");
    }

    @Test
    @DisplayName("Revogar o próprio papel de moderador não aciona a guarda de último administrador geral")
    void revokingOwnNonAdminRolePassesWithoutLastAdminGuard() {
        UUID actorAndTargetId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();
        String targetSubject = "moderator-target-subject";

        OperationalRoleGrant admin = createAdminGrant(actorAndTargetId);
        OperationalRoleGrant moderatorGrant = new OperationalRoleGrant(
                grantId,
                actorAndTargetId,
                OperationalRole.MODERADOR,
                null,
                OperationalRoleGrant.STATUS_VIGENTE,
                Instant.now(),
                null);

        when(repository.findActiveByIdentityId(actorAndTargetId))
                .thenReturn(List.of(admin, moderatorGrant));
        UUID revokeScope = UUID.fromString("3c9a5e10-8b72-4d6f-a0e3-15c7d9b28f04");
        when(repository.findActiveGrant(actorAndTargetId, OperationalRole.MODERADOR, revokeScope))
                .thenReturn(Optional.of(moderatorGrant));
        when(repository.findSubjectByIdentityId(actorAndTargetId))
                .thenReturn(Optional.of(targetSubject));

        RoleGrantResponse response = service.revokeRole(
                actorAndTargetId, actorAndTargetId, OperationalRole.MODERADOR, revokeScope);

        assertThat(response.identityId()).isEqualTo(actorAndTargetId);
        verify(repository, never()).findActiveRolesForUpdate(any());
        verify(repository).revokeGrant(eq(grantId), any());
    }

    @Test
    @DisplayName("Ator sem papel ADMINISTRADOR_GERAL é recusado com FORBIDDEN e auditado em consultar, conceder e revogar")
    void actorWithoutAdminGeneralIsBlockedFromQueryGrantAndRevoke() {
        UUID nonAdminActorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        when(repository.findActiveByIdentityId(nonAdminActorId)).thenReturn(List.of());

        // 1. getRoleStatus
        assertThatThrownBy(() -> service.getRoleStatus(nonAdminActorId, targetId))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> {
                    ApiProblemException apiEx = (ApiProblemException) ex;
                    assertThat(apiEx.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.errorCode()).isEqualTo(ApiErrorCode.FORBIDDEN);
                });

        ArgumentCaptor<AuditEntry> auditCaptor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRecorder).record(auditCaptor.capture());
        AuditEntry getAudit = auditCaptor.getValue();
        assertThat(getAudit.actorIdentityId()).isEqualTo(nonAdminActorId);
        assertThat(getAudit.targetIdentityId()).isEqualTo(targetId);
        assertThat(getAudit.action()).isEqualTo(AuditEntry.ACTION_CONSULTA_OPERACIONAL);
        assertThat(getAudit.outcome()).isEqualTo(AuditEntry.OUTCOME_BLOCKED);

        reset(auditRecorder);

        // 2. grantRole
        assertThatThrownBy(() -> service.grantRole(nonAdminActorId, targetId, OperationalRole.MODERADOR, null))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> {
                    ApiProblemException apiEx = (ApiProblemException) ex;
                    assertThat(apiEx.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.errorCode()).isEqualTo(ApiErrorCode.FORBIDDEN);
                });

        verify(auditRecorder).record(auditCaptor.capture());
        AuditEntry grantAudit = auditCaptor.getValue();
        assertThat(grantAudit.actorIdentityId()).isEqualTo(nonAdminActorId);
        assertThat(grantAudit.targetIdentityId()).isEqualTo(targetId);
        assertThat(grantAudit.action()).isEqualTo(AuditEntry.ACTION_PAPEL_CONCEDIDO);
        assertThat(grantAudit.outcome()).isEqualTo(AuditEntry.OUTCOME_BLOCKED);

        reset(auditRecorder);

        // 3. revokeRole
        assertThatThrownBy(() -> service.revokeRole(nonAdminActorId, targetId, OperationalRole.MODERADOR, null))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> {
                    ApiProblemException apiEx = (ApiProblemException) ex;
                    assertThat(apiEx.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.errorCode()).isEqualTo(ApiErrorCode.FORBIDDEN);
                });

        verify(auditRecorder).record(auditCaptor.capture());
        AuditEntry revokeAudit = auditCaptor.getValue();
        assertThat(revokeAudit.actorIdentityId()).isEqualTo(nonAdminActorId);
        assertThat(revokeAudit.targetIdentityId()).isEqualTo(targetId);
        assertThat(revokeAudit.action()).isEqualTo(AuditEntry.ACTION_PAPEL_REVOGADO);
        assertThat(revokeAudit.outcome()).isEqualTo(AuditEntry.OUTCOME_BLOCKED);

        verify(repository, never()).insertGrant(any(), any(), any());
        verify(repository, never()).revokeGrant(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("findActiveRoles preserva múltiplos escopos do mesmo papel sem agrupar somente pelo enum")
    void findActiveRolesPreservesDistinctScopesForSameRole() {
        UUID targetId = UUID.randomUUID();
        UUID scopeA = UUID.randomUUID();
        UUID scopeB = UUID.randomUUID();

        OperationalRoleGrant grant1 = new OperationalRoleGrant(
                UUID.randomUUID(),
                targetId,
                OperationalRole.ADMINISTRADOR_COMPETICAO,
                scopeA,
                OperationalRoleGrant.STATUS_VIGENTE,
                Instant.now(),
                null);

        OperationalRoleGrant grant2 = new OperationalRoleGrant(
                UUID.randomUUID(),
                targetId,
                OperationalRole.ADMINISTRADOR_COMPETICAO,
                scopeB,
                OperationalRoleGrant.STATUS_VIGENTE,
                Instant.now(),
                null);

        when(repository.findActiveByIdentityId(targetId)).thenReturn(List.of(grant1, grant2));

        Set<GrantedRole> activeRoles = service.findActiveRoles(targetId);

        assertThat(activeRoles).containsExactlyInAnyOrder(
                new GrantedRole(OperationalRole.ADMINISTRADOR_COMPETICAO, scopeA),
                new GrantedRole(OperationalRole.ADMINISTRADOR_COMPETICAO, scopeB));
        assertThat(service.hasRole(targetId, OperationalRole.ADMINISTRADOR_COMPETICAO)).isTrue();
        assertThat(service.hasRole(targetId, OperationalRole.ADMINISTRADOR_COMPETICAO, scopeA)).isTrue();
        assertThat(service.hasRole(targetId, OperationalRole.ADMINISTRADOR_COMPETICAO, scopeB)).isTrue();
        assertThat(service.hasRole(targetId, OperationalRole.ADMINISTRADOR_COMPETICAO, UUID.randomUUID())).isFalse();
    }

    private OperationalRoleGrant createAdminGrant(UUID identityId) {
        return new OperationalRoleGrant(
                UUID.randomUUID(),
                identityId,
                OperationalRole.ADMINISTRADOR_GERAL,
                null,
                OperationalRoleGrant.STATUS_VIGENTE,
                Instant.now(),
                null);
    }

    @Test
    @DisplayName("Papel escopado sem escopo é recusado com 400 e auditado como bloqueio")
    void grantScopedRoleWithoutScopeIsRejected() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(repository.findActiveByIdentityId(actorId))
                .thenReturn(List.of(createAdminGrant(actorId)));

        assertThatThrownBy(() -> service.grantRole(actorId, targetId, OperationalRole.MODERADOR, null))
                .isInstanceOf(ApiProblemException.class);

        verify(repository, never()).insertGrant(any(), any(), any());

        ArgumentCaptor<AuditEntry> auditCaptor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRecorder).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().outcome()).isEqualTo(AuditEntry.OUTCOME_BLOCKED);
    }

    @Test
    @DisplayName("Papel global concedido com escopo é recusado com 400 e não chega ao repositório")
    void grantGlobalRoleWithScopeIsRejected() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(repository.findActiveByIdentityId(actorId))
                .thenReturn(List.of(createAdminGrant(actorId)));

        assertThatThrownBy(() -> service.grantRole(
                actorId, targetId, OperationalRole.ADMINISTRADOR_GERAL, UUID.randomUUID()))
                .isInstanceOf(ApiProblemException.class);

        verify(repository, never()).insertGrant(any(), any(), any());
    }

    @Test
    @DisplayName("Revogação também recusa combinação papel/escopo inconsistente")
    void revokeWithInconsistentScopeIsRejected() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(repository.findActiveByIdentityId(actorId))
                .thenReturn(List.of(createAdminGrant(actorId)));

        assertThatThrownBy(() -> service.revokeRole(actorId, targetId, OperationalRole.MODERADOR, null))
                .isInstanceOf(ApiProblemException.class);

        verify(repository, never()).revokeGrant(any(), any());
    }

    @Test
    @DisplayName("ADMINISTRADOR_GERAL concedido dentro de um escopo NAO confere autoridade global")
    void scopedAdminGrantDoesNotConferGlobalAuthority() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID scope = UUID.randomUUID();

        OperationalRoleGrant scopedAdmin = new OperationalRoleGrant(
                UUID.randomUUID(),
                actorId,
                OperationalRole.ADMINISTRADOR_GERAL,
                scope,
                OperationalRoleGrant.STATUS_VIGENTE,
                Instant.now(),
                null);
        when(repository.findActiveByIdentityId(actorId)).thenReturn(List.of(scopedAdmin));

        assertThat(service.hasRole(actorId, OperationalRole.ADMINISTRADOR_GERAL))
                .as("concessao escopada de papel global nao pode valer como autoridade irrestrita")
                .isFalse();

        assertThatThrownBy(() -> service.grantRole(
                actorId, targetId, OperationalRole.MODERADOR, UUID.randomUUID()))
                .isInstanceOf(ApiProblemException.class);

        verify(repository, never()).insertGrant(any(), any(), any());
    }

    @Test
    @DisplayName("Controle positivo: ADMINISTRADOR_GERAL global confere autoridade")
    void globalAdminGrantConfersAuthority() {
        UUID actorId = UUID.randomUUID();
        when(repository.findActiveByIdentityId(actorId))
                .thenReturn(List.of(createAdminGrant(actorId)));

        assertThat(service.hasRole(actorId, OperationalRole.ADMINISTRADOR_GERAL)).isTrue();
    }
}
