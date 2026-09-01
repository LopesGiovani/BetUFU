package br.com.betufu.platform.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.betufu.audit.AuditAggregateQuery;
import br.com.betufu.audit.AuditAggregateQuery.RecentAdministrativeCorrectionEvent;
import br.com.betufu.audit.AuditAggregateQuery.RecentRoleChangeEvent;
import br.com.betufu.audit.AuditAggregateQuery.RejectionReasonCount;
import br.com.betufu.audit.AuditEntry;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.identity.OperationalIdentityLookup.GrantedRole;
import br.com.betufu.platform.identity.OperationalIdentityLookup.OperationalIdentity;
import br.com.betufu.platform.operations.OperationalPanelResponse.OperationalBlock;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OperationalPanelServiceTest {

  private static final String ISSUER = "https://idp.betufu.internal";
  private static final String ADMIN_SUBJECT = "admin-sub-100";
  private static final String COMP_ADMIN_SUBJECT = "comp-admin-sub-200";
  private static final String MODERATOR_SUBJECT = "moderator-sub-300";

  private static final Instant FIXED_NOW = Instant.parse("2026-08-24T12:00:00Z");
  private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

  private AuditAggregateQuery auditAggregateQuery;
  private OperationalIdentityLookup identityLookup;
  private EnrollmentAggregateLookup enrollmentCountLookup;
  private OperationalPanelService service;

  private UUID adminIdentityId;
  private UUID compAdminIdentityId;
  private UUID moderatorIdentityId;
  private UUID targetIdentityId;
  private UUID scopeId;

  @BeforeEach
  void setUp() {
    auditAggregateQuery = mock(AuditAggregateQuery.class);
    identityLookup = mock(OperationalIdentityLookup.class);
    enrollmentCountLookup = mock(EnrollmentAggregateLookup.class);
    service =
        new OperationalPanelService(
            auditAggregateQuery, identityLookup, enrollmentCountLookup, clock);

    adminIdentityId = UUID.randomUUID();
    compAdminIdentityId = UUID.randomUUID();
    moderatorIdentityId = UUID.randomUUID();
    targetIdentityId = UUID.randomUUID();
    scopeId = UUID.randomUUID();

    // Configura identidades e papéis
    OperationalIdentity adminIdentity =
        new OperationalIdentity(
            adminIdentityId, Set.of(new GrantedRole("ADMINISTRADOR_GERAL", null)));

    OperationalIdentity compAdminIdentity =
        new OperationalIdentity(
            compAdminIdentityId, Set.of(new GrantedRole("ADMINISTRADOR_COMPETICAO", scopeId)));

    OperationalIdentity moderatorIdentity =
        new OperationalIdentity(moderatorIdentityId, Set.of(new GrantedRole("MODERADOR", scopeId)));

    when(identityLookup.lookupIdentity(ISSUER, ADMIN_SUBJECT))
        .thenReturn(Optional.of(adminIdentity));
    when(identityLookup.lookupIdentity(ISSUER, COMP_ADMIN_SUBJECT))
        .thenReturn(Optional.of(compAdminIdentity));
    when(identityLookup.lookupIdentity(ISSUER, MODERATOR_SUBJECT))
        .thenReturn(Optional.of(moderatorIdentity));

    // Dados padrão saudáveis para todos os blocos
    when(auditAggregateQuery.findRecentRoleChangeEvents(anySet(), anyInt()))
        .thenReturn(
            List.of(
                new RecentRoleChangeEvent(
                    UUID.randomUUID(),
                    FIXED_NOW.minusSeconds(3600),
                    adminIdentityId,
                    "ADMINISTRADOR_GERAL",
                    targetIdentityId,
                    "target_user",
                    AuditEntry.ACTION_PAPEL_CONCEDIDO,
                    AuditEntry.OUTCOME_PERMITTED,
                    false)));

    when(auditAggregateQuery.countRejectionsByReason(
            eq(AuditEntry.ACTION_REGISTRO_MATRICULA), any(), any()))
        .thenReturn(
            List.of(
                new RejectionReasonCount("Conta já possui matrícula ativa", 2L),
                new RejectionReasonCount("Limite de tentativas excedido", 1L)));

    when(auditAggregateQuery.findRecentAdministrativeCorrectionEvents(anySet(), anyInt()))
        .thenReturn(
            List.of(
                new RecentAdministrativeCorrectionEvent(
                    UUID.randomUUID(),
                    FIXED_NOW.minusSeconds(7200),
                    adminIdentityId,
                    "ADMINISTRADOR_GERAL",
                    targetIdentityId,
                    "target_user",
                    AuditEntry.ACTION_CORRECAO_MATRICULA,
                    AuditEntry.OUTCOME_PERMITTED,
                    true,
                    "Correção administrativa por protocolo 12345")));

    when(enrollmentCountLookup.countAccountsWithoutActiveEnrollment()).thenReturn(42);
  }

  @Test
  @DisplayName(
      "Administrador Geral com todos os blocos saudáveis recebe os 4 blocos e unavailableBlocks vazio")
  void globalAdminWithAllHealthyBlocksReceivesAllFourAndEmptyUnavailableBlocks() {
    OperationalPanelResponse response = service.getPanel(ISSUER, ADMIN_SUBJECT);

    assertThat(response.queriedAt()).isEqualTo(FIXED_NOW);
    assertThat(response.timeWindow().since())
        .isEqualTo(FIXED_NOW.minus(OperationalPanelService.DEFAULT_TIME_WINDOW));
    assertThat(response.timeWindow().until()).isEqualTo(FIXED_NOW);

    assertThat(response.roleChanges()).isNotNull();
    assertThat(response.roleChanges().recentEvents()).hasSize(1);

    assertThat(response.rejectedEnrollmentsCount()).isNotNull();
    assertThat(response.rejectedEnrollmentsCount().totalCount()).isEqualTo(3L);

    assertThat(response.administrativeCorrections()).isNotNull();
    assertThat(response.administrativeCorrections().recentEvents()).hasSize(1);

    assertThat(response.unregisteredEnrollmentsCount()).isNotNull();
    assertThat(response.unregisteredEnrollmentsCount().count()).isEqualTo(42);

    assertThat(response.unavailableBlocks()).isEmpty();
  }

  @Test
  @DisplayName(
      "Eventos recentes respeitam inclusivamente o recorte temporal declarado de sete dias")
  void recentEventsRespectDeclaredTimeWindowInclusively() {
    Instant since = FIXED_NOW.minus(OperationalPanelService.DEFAULT_TIME_WINDOW);

    UUID roleAtSince = UUID.fromString("10000000-0000-0000-0000-000000000001");
    UUID roleAtUntil = UUID.fromString("10000000-0000-0000-0000-000000000002");
    when(auditAggregateQuery.findRecentRoleChangeEvents(anySet(), anyInt()))
        .thenReturn(
            List.of(
                new RecentRoleChangeEvent(
                    UUID.fromString("10000000-0000-0000-0000-000000000003"),
                    FIXED_NOW.plusNanos(1),
                    adminIdentityId,
                    "ADMINISTRADOR_GERAL",
                    targetIdentityId,
                    "future_role",
                    AuditEntry.ACTION_PAPEL_CONCEDIDO,
                    AuditEntry.OUTCOME_PERMITTED,
                    false),
                new RecentRoleChangeEvent(
                    roleAtUntil,
                    FIXED_NOW,
                    adminIdentityId,
                    "ADMINISTRADOR_GERAL",
                    targetIdentityId,
                    "until_role",
                    AuditEntry.ACTION_PAPEL_CONCEDIDO,
                    AuditEntry.OUTCOME_PERMITTED,
                    false),
                new RecentRoleChangeEvent(
                    roleAtSince,
                    since,
                    adminIdentityId,
                    "ADMINISTRADOR_GERAL",
                    targetIdentityId,
                    "since_role",
                    AuditEntry.ACTION_PAPEL_CONCEDIDO,
                    AuditEntry.OUTCOME_PERMITTED,
                    false),
                new RecentRoleChangeEvent(
                    UUID.fromString("10000000-0000-0000-0000-000000000004"),
                    since.minusNanos(1),
                    adminIdentityId,
                    "ADMINISTRADOR_GERAL",
                    targetIdentityId,
                    "old_role",
                    AuditEntry.ACTION_PAPEL_CONCEDIDO,
                    AuditEntry.OUTCOME_PERMITTED,
                    false)));

    UUID correctionAtSince = UUID.fromString("20000000-0000-0000-0000-000000000001");
    UUID correctionAtUntil = UUID.fromString("20000000-0000-0000-0000-000000000002");
    when(auditAggregateQuery.findRecentAdministrativeCorrectionEvents(anySet(), anyInt()))
        .thenReturn(
            List.of(
                new RecentAdministrativeCorrectionEvent(
                    UUID.fromString("20000000-0000-0000-0000-000000000003"),
                    FIXED_NOW.plusNanos(1),
                    adminIdentityId,
                    "ADMINISTRADOR_GERAL",
                    targetIdentityId,
                    "future_correction",
                    AuditEntry.ACTION_CORRECAO_MATRICULA,
                    AuditEntry.OUTCOME_PERMITTED,
                    true,
                    "fora da janela futura"),
                new RecentAdministrativeCorrectionEvent(
                    correctionAtUntil,
                    FIXED_NOW,
                    adminIdentityId,
                    "ADMINISTRADOR_GERAL",
                    targetIdentityId,
                    "until_correction",
                    AuditEntry.ACTION_CORRECAO_MATRICULA,
                    AuditEntry.OUTCOME_PERMITTED,
                    true,
                    "limite final"),
                new RecentAdministrativeCorrectionEvent(
                    correctionAtSince,
                    since,
                    adminIdentityId,
                    "ADMINISTRADOR_GERAL",
                    targetIdentityId,
                    "since_correction",
                    AuditEntry.ACTION_CORRECAO_MATRICULA,
                    AuditEntry.OUTCOME_PERMITTED,
                    true,
                    "limite inicial"),
                new RecentAdministrativeCorrectionEvent(
                    UUID.fromString("20000000-0000-0000-0000-000000000004"),
                    since.minusNanos(1),
                    adminIdentityId,
                    "ADMINISTRADOR_GERAL",
                    targetIdentityId,
                    "old_correction",
                    AuditEntry.ACTION_CORRECAO_MATRICULA,
                    AuditEntry.OUTCOME_PERMITTED,
                    true,
                    "fora da janela antiga")));

    OperationalPanelResponse response = service.getPanel(ISSUER, ADMIN_SUBJECT);

    assertThat(response.roleChanges().recentEvents())
        .extracting(OperationalPanelResponse.RoleChangeEventDto::eventId)
        .containsExactly(roleAtUntil, roleAtSince);
    assertThat(response.administrativeCorrections().recentEvents())
        .extracting(OperationalPanelResponse.AdministrativeCorrectionEventDto::eventId)
        .containsExactly(correctionAtUntil, correctionAtSince);
  }

  @Test
  @DisplayName(
      "Falha isolada em ROLE_CHANGES preserva os outros 3 blocos e enumera apenas ROLE_CHANGES")
  void isolatedFailureInRoleChangesPreservesOtherThreeBlocksForGlobalAdmin() {
    when(auditAggregateQuery.findRecentRoleChangeEvents(anySet(), anyInt()))
        .thenThrow(new RuntimeException("Database timeout querying audit role changes"));

    OperationalPanelResponse response = service.getPanel(ISSUER, ADMIN_SUBJECT);

    assertThat(response.roleChanges()).isNull();
    assertThat(response.rejectedEnrollmentsCount()).isNotNull();
    assertThat(response.rejectedEnrollmentsCount().totalCount()).isEqualTo(3L);
    assertThat(response.administrativeCorrections()).isNotNull();
    assertThat(response.administrativeCorrections().recentEvents()).hasSize(1);
    assertThat(response.unregisteredEnrollmentsCount()).isNotNull();
    assertThat(response.unregisteredEnrollmentsCount().count()).isEqualTo(42);

    assertThat(response.unavailableBlocks()).containsExactly(OperationalBlock.ROLE_CHANGES);
  }

  @Test
  @DisplayName(
      "Falha isolada em REJECTED_ENROLLMENTS_COUNT preserva os outros 3 blocos e enumera apenas REJECTED_ENROLLMENTS_COUNT")
  void isolatedFailureInRejectedEnrollmentsPreservesOtherThreeBlocksForGlobalAdmin() {
    when(auditAggregateQuery.countRejectionsByReason(
            eq(AuditEntry.ACTION_REGISTRO_MATRICULA), any(), any()))
        .thenThrow(new RuntimeException("Audit aggregation service unreachable"));

    OperationalPanelResponse response = service.getPanel(ISSUER, ADMIN_SUBJECT);

    assertThat(response.roleChanges()).isNotNull();
    assertThat(response.roleChanges().recentEvents()).hasSize(1);
    assertThat(response.rejectedEnrollmentsCount()).isNull();
    assertThat(response.administrativeCorrections()).isNotNull();
    assertThat(response.administrativeCorrections().recentEvents()).hasSize(1);
    assertThat(response.unregisteredEnrollmentsCount()).isNotNull();
    assertThat(response.unregisteredEnrollmentsCount().count()).isEqualTo(42);

    assertThat(response.unavailableBlocks())
        .containsExactly(OperationalBlock.REJECTED_ENROLLMENTS_COUNT);
  }

  @Test
  @DisplayName(
      "Falha isolada em ADMINISTRATIVE_CORRECTIONS preserva os outros 3 blocos e enumera apenas ADMINISTRATIVE_CORRECTIONS")
  void isolatedFailureInAdministrativeCorrectionsPreservesOtherThreeBlocksForGlobalAdmin() {
    when(auditAggregateQuery.findRecentAdministrativeCorrectionEvents(anySet(), anyInt()))
        .thenThrow(new RuntimeException("Audit connection dropped"));

    OperationalPanelResponse response = service.getPanel(ISSUER, ADMIN_SUBJECT);

    assertThat(response.roleChanges()).isNotNull();
    assertThat(response.roleChanges().recentEvents()).hasSize(1);
    assertThat(response.rejectedEnrollmentsCount()).isNotNull();
    assertThat(response.rejectedEnrollmentsCount().totalCount()).isEqualTo(3L);
    assertThat(response.administrativeCorrections()).isNull();
    assertThat(response.unregisteredEnrollmentsCount()).isNotNull();
    assertThat(response.unregisteredEnrollmentsCount().count()).isEqualTo(42);

    assertThat(response.unavailableBlocks())
        .containsExactly(OperationalBlock.ADMINISTRATIVE_CORRECTIONS);
  }

  @Test
  @DisplayName(
      "Falha isolada em UNREGISTERED_ENROLLMENTS_COUNT preserva os outros 3 blocos e enumera apenas UNREGISTERED_ENROLLMENTS_COUNT")
  void isolatedFailureInUnregisteredEnrollmentsPreservesOtherThreeBlocksForGlobalAdmin() {
    when(enrollmentCountLookup.countAccountsWithoutActiveEnrollment())
        .thenThrow(new RuntimeException("Verification lookup failure"));

    OperationalPanelResponse response = service.getPanel(ISSUER, ADMIN_SUBJECT);

    assertThat(response.roleChanges()).isNotNull();
    assertThat(response.roleChanges().recentEvents()).hasSize(1);
    assertThat(response.rejectedEnrollmentsCount()).isNotNull();
    assertThat(response.rejectedEnrollmentsCount().totalCount()).isEqualTo(3L);
    assertThat(response.administrativeCorrections()).isNotNull();
    assertThat(response.administrativeCorrections().recentEvents()).hasSize(1);
    assertThat(response.unregisteredEnrollmentsCount()).isNull();

    assertThat(response.unavailableBlocks())
        .containsExactly(OperationalBlock.UNREGISTERED_ENROLLMENTS_COUNT);
  }

  @Test
  @DisplayName("Múltiplas falhas isoladas acumulam em unavailableBlocks sem vazamento de exceção")
  void multipleBlockFailuresAccumulateInUnavailableBlocksWithoutExceptionLeak() {
    when(auditAggregateQuery.findRecentRoleChangeEvents(anySet(), anyInt()))
        .thenThrow(new RuntimeException("Audit DB error"));
    when(enrollmentCountLookup.countAccountsWithoutActiveEnrollment())
        .thenThrow(new RuntimeException("Verification DB error"));

    OperationalPanelResponse response = service.getPanel(ISSUER, ADMIN_SUBJECT);

    assertThat(response.roleChanges()).isNull();
    assertThat(response.rejectedEnrollmentsCount()).isNotNull();
    assertThat(response.administrativeCorrections()).isNotNull();
    assertThat(response.unregisteredEnrollmentsCount()).isNull();

    assertThat(response.unavailableBlocks())
        .containsExactly(
            OperationalBlock.ROLE_CHANGES, OperationalBlock.UNREGISTERED_ENROLLMENTS_COUNT);
  }

  @Test
  @DisplayName(
      "Administrador de Competição: falha em contagem sem matrícula enumera APENAS UNREGISTERED_ENROLLMENTS_COUNT")
  void competitionAdminEnumeratesOnlyAuthorizedFailedBlockAndNeverTouchesAudit() {
    when(enrollmentCountLookup.countAccountsWithoutActiveEnrollment())
        .thenThrow(new RuntimeException("Enrollment service down"));

    OperationalPanelResponse response = service.getPanel(ISSUER, COMP_ADMIN_SUBJECT);

    // Bloco autorizado com falha
    assertThat(response.unregisteredEnrollmentsCount()).isNull();
    assertThat(response.unavailableBlocks())
        .containsExactly(OperationalBlock.UNREGISTERED_ENROLLMENTS_COUNT);

    // Blocos fora do escopo permanecem nulos e NUNCA constam em unavailableBlocks
    assertThat(response.roleChanges()).isNull();
    assertThat(response.rejectedEnrollmentsCount()).isNull();
    assertThat(response.administrativeCorrections()).isNull();

    // Garante que a auditoria nunca foi consultada para blocos fora do papel
    verifyNoInteractions(auditAggregateQuery);
  }

  @Test
  @DisplayName(
      "Moderador recebe escopo vazio com zero blocos e zero unavailableBlocks sem acionar consultas")
  void moderatorReceivesEmptyScopeWithZeroUnavailableBlocksWithoutTriggeringQueries() {
    OperationalPanelResponse response = service.getPanel(ISSUER, MODERATOR_SUBJECT);

    assertThat(response.roleChanges()).isNull();
    assertThat(response.rejectedEnrollmentsCount()).isNull();
    assertThat(response.administrativeCorrections()).isNull();
    assertThat(response.unregisteredEnrollmentsCount()).isNull();
    assertThat(response.unavailableBlocks()).isEmpty();

    verifyNoInteractions(auditAggregateQuery, enrollmentCountLookup);
  }

  @Test
  @DisplayName(
      "Identidade desconhecida ou anônima retorna resposta com escopo vazio e zero unavailableBlocks")
  void unknownOrAnonymousIdentityReturnsEmptyScopeWithZeroUnavailableBlocks() {
    when(identityLookup.lookupIdentity(ISSUER, "unknown-sub")).thenReturn(Optional.empty());

    OperationalPanelResponse response = service.getPanel(ISSUER, "unknown-sub");

    assertThat(response.roleChanges()).isNull();
    assertThat(response.rejectedEnrollmentsCount()).isNull();
    assertThat(response.administrativeCorrections()).isNull();
    assertThat(response.unregisteredEnrollmentsCount()).isNull();
    assertThat(response.unavailableBlocks()).isEmpty();

    OperationalPanelResponse nullResponse = service.getPanel(null, null);
    assertThat(nullResponse.unavailableBlocks()).isEmpty();
  }
}
