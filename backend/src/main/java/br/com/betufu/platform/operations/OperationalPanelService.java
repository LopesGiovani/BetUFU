package br.com.betufu.platform.operations;

import br.com.betufu.audit.AuditAggregateQuery;
import br.com.betufu.audit.AuditAggregateQuery.RecentAdministrativeCorrectionEvent;
import br.com.betufu.audit.AuditAggregateQuery.RecentRoleChangeEvent;
import br.com.betufu.audit.AuditAggregateQuery.RejectionReasonCount;
import br.com.betufu.audit.AuditEntry;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.identity.OperationalIdentityLookup.GrantedRole;
import br.com.betufu.platform.identity.OperationalIdentityLookup.OperationalIdentity;
import br.com.betufu.platform.operations.OperationalPanelResponse.AdministrativeCorrectionEventDto;
import br.com.betufu.platform.operations.OperationalPanelResponse.AdministrativeCorrectionsBlock;
import br.com.betufu.platform.operations.OperationalPanelResponse.OperationalBlock;
import br.com.betufu.platform.operations.OperationalPanelResponse.RejectedEnrollmentsBlock;
import br.com.betufu.platform.operations.OperationalPanelResponse.RoleChangeEventDto;
import br.com.betufu.platform.operations.OperationalPanelResponse.RoleChangesBlock;
import br.com.betufu.platform.operations.OperationalPanelResponse.TimeWindow;
import br.com.betufu.platform.operations.OperationalPanelResponse.UnregisteredEnrollmentsBlock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Serviço de montagem do painel operacional por papel vigente (OPER-06, S4 do 02-UI-SPEC.md).
 *
 * <p>Consome exclusivamente o contrato consumidor {@link OperationalIdentityLookup} pertencente ao
 * {@code platform}, o contrato {@link EnrollmentAggregateLookup} e a API pública {@link
 * AuditAggregateQuery} do módulo de auditoria. Nenhum tipo de domínio de {@code
 * br.com.betufu.identity} ou {@code br.com.betufu.verification} é importado.
 *
 * <p>Garante que blocos não autorizados pelo papel vigente fiquem {@code null} no servidor, sem
 * vazar a existência do escopo ao cliente (OPER-02, OPER-04).
 *
 * <p>Falhas parciais em blocos autorizados são capturadas de forma isolada, preservando os demais
 * blocos e enumerando somente os blocos autorizados que falharam em {@code unavailableBlocks}.
 */
@Service
@Profile("runtime | integrated")
public class OperationalPanelService {

  private static final String GENERAL_ADMIN = "ADMINISTRADOR_GERAL";
  private static final String COMPETITION_ADMIN = "ADMINISTRADOR_COMPETICAO";

  private static final Logger log = LoggerFactory.getLogger(OperationalPanelService.class);

  public static final Duration DEFAULT_TIME_WINDOW = Duration.ofDays(7);
  public static final int RECENT_EVENTS_LIMIT = 20;

  private static final Set<String> ROLE_CHANGE_ACTIONS =
      Set.of(
          AuditEntry.ACTION_PAPEL_CONCEDIDO,
          AuditEntry.ACTION_PAPEL_REVOGADO,
          AuditEntry.ACTION_AUTOCONCESSAO_PAPEL,
          AuditEntry.ACTION_REVOGACAO_ULTIMO_ADMINISTRADOR,
          AuditEntry.ACTION_ULTIMO_ADMINISTRADOR_GERAL);

  private static final Set<String> ADMIN_CORRECTION_ACTIONS =
      Set.of(AuditEntry.ACTION_CORRECAO_MATRICULA);

  private static final Set<String> DUPLICATE_REJECTION_REASONS =
      Set.of("Conta já possui matrícula ativa", "Matrícula já registrada em outra conta");

  private static final String ATTEMPT_LIMIT_REJECTION_REASON = "Limite de tentativas excedido";

  private final AuditAggregateQuery auditAggregateQuery;
  private final OperationalIdentityLookup identityLookup;
  private final EnrollmentAggregateLookup enrollmentCountLookup;
  private final Clock clock;

  public OperationalPanelService(
      AuditAggregateQuery auditAggregateQuery,
      OperationalIdentityLookup identityLookup,
      EnrollmentAggregateLookup enrollmentCountLookup,
      @Autowired(required = false) Clock clock) {
    this.auditAggregateQuery =
        Objects.requireNonNull(auditAggregateQuery, "auditAggregateQuery must not be null");
    this.identityLookup = Objects.requireNonNull(identityLookup, "identityLookup must not be null");
    this.enrollmentCountLookup =
        Objects.requireNonNull(enrollmentCountLookup, "enrollmentCountLookup must not be null");
    this.clock = clock != null ? clock : Clock.systemUTC();
  }

  /**
   * Monta a visão autorizada do painel operacional para o par exato {@code (issuer, subject)}.
   *
   * @param issuer emissor OIDC
   * @param subject sujeito OIDC
   * @return resposta com exatamente os blocos autorizados
   */
  public OperationalPanelResponse getPanel(String issuer, String subject) {
    Instant now = Instant.now(clock);
    Instant since = now.minus(DEFAULT_TIME_WINDOW);
    TimeWindow timeWindow = new TimeWindow(since, now);

    if (issuer == null || subject == null || issuer.isBlank() || subject.isBlank()) {
      return new OperationalPanelResponse(now, timeWindow, null, null, null, null, List.of());
    }

    Optional<OperationalIdentity> identityOpt = identityLookup.lookupIdentity(issuer, subject);
    if (identityOpt.isEmpty()) {
      return new OperationalPanelResponse(now, timeWindow, null, null, null, null, List.of());
    }

    Set<GrantedRole> roles = identityOpt.get().roles();
    boolean isGlobalAdmin =
        roles.stream()
            .anyMatch(grant -> GENERAL_ADMIN.equals(grant.roleName()) && grant.scopeRef() == null);
    boolean isCompAdmin =
        roles.stream()
            .anyMatch(
                grant -> COMPETITION_ADMIN.equals(grant.roleName()) && grant.scopeRef() != null);

    RoleChangesBlock roleChanges = null;
    RejectedEnrollmentsBlock rejectedEnrollments = null;
    AdministrativeCorrectionsBlock administrativeCorrections = null;
    UnregisteredEnrollmentsBlock unregisteredEnrollments = null;
    List<OperationalBlock> unavailableBlocks = new ArrayList<>();

    if (isGlobalAdmin) {
      // 1. Bloco de alterações de papel
      try {
        List<RecentRoleChangeEvent> recentRoleEvents =
            auditAggregateQuery.findRecentRoleChangeEvents(
                ROLE_CHANGE_ACTIONS, RECENT_EVENTS_LIMIT);

        List<RoleChangeEventDto> roleDtos =
            recentRoleEvents.stream()
                .filter(event -> isWithinTimeWindow(event.occurredAt(), since, now))
                .map(
                    e ->
                        new RoleChangeEventDto(
                            e.eventId(),
                            e.occurredAt(),
                            e.actorIdentityId(),
                            e.actorRole(),
                            e.targetIdentityId(),
                            e.targetNickname(),
                            e.action(),
                            e.outcome(),
                            e.hasReason()))
                .toList();
        roleChanges = new RoleChangesBlock(roleDtos);
      } catch (RuntimeException e) {
        log.warn("Falha ao consultar bloco ROLE_CHANGES do painel operacional");
        unavailableBlocks.add(OperationalBlock.ROLE_CHANGES);
      }

      // 2. Bloco de registros de matrícula recusados
      try {
        List<RejectionReasonCount> rejections =
            auditAggregateQuery.countRejectionsByReason(
                AuditEntry.ACTION_REGISTRO_MATRICULA, since, now);

        long duplicateCount = 0;
        long attemptLimitCount = 0;
        for (RejectionReasonCount rrc : rejections) {
          String reason = rrc.reason() != null ? rrc.reason() : "";
          if (DUPLICATE_REJECTION_REASONS.contains(reason)) {
            duplicateCount += rrc.count();
          } else if (ATTEMPT_LIMIT_REJECTION_REASON.equals(reason)) {
            attemptLimitCount += rrc.count();
          }
        }
        long totalCount = duplicateCount + attemptLimitCount;
        rejectedEnrollments =
            new RejectedEnrollmentsBlock(duplicateCount, attemptLimitCount, totalCount);
      } catch (RuntimeException e) {
        log.warn("Falha ao consultar bloco REJECTED_ENROLLMENTS_COUNT do painel operacional");
        unavailableBlocks.add(OperationalBlock.REJECTED_ENROLLMENTS_COUNT);
      }

      // 3. Bloco de correções administrativas de matrícula
      try {
        List<RecentAdministrativeCorrectionEvent> recentCorrections =
            auditAggregateQuery.findRecentAdministrativeCorrectionEvents(
                ADMIN_CORRECTION_ACTIONS, RECENT_EVENTS_LIMIT);

        List<AdministrativeCorrectionEventDto> correctionDtos =
            recentCorrections.stream()
                .filter(event -> isWithinTimeWindow(event.occurredAt(), since, now))
                .map(
                    e ->
                        new AdministrativeCorrectionEventDto(
                            e.eventId(),
                            e.occurredAt(),
                            e.actorIdentityId(),
                            e.actorRole(),
                            e.targetIdentityId(),
                            e.targetNickname(),
                            e.action(),
                            e.outcome(),
                            e.hasReason(),
                            e.reason()))
                .toList();
        administrativeCorrections = new AdministrativeCorrectionsBlock(correctionDtos);
      } catch (RuntimeException e) {
        log.warn("Falha ao consultar bloco ADMINISTRATIVE_CORRECTIONS do painel operacional");
        unavailableBlocks.add(OperationalBlock.ADMINISTRATIVE_CORRECTIONS);
      }
    }

    if (isGlobalAdmin || isCompAdmin) {
      // 4. Bloco de contas sem matrícula registrada
      try {
        int count = enrollmentCountLookup.countAccountsWithoutActiveEnrollment();
        unregisteredEnrollments = new UnregisteredEnrollmentsBlock(count);
      } catch (RuntimeException e) {
        log.warn("Falha ao consultar bloco UNREGISTERED_ENROLLMENTS_COUNT do painel operacional");
        unavailableBlocks.add(OperationalBlock.UNREGISTERED_ENROLLMENTS_COUNT);
      }
    }

    return new OperationalPanelResponse(
        now,
        timeWindow,
        roleChanges,
        rejectedEnrollments,
        administrativeCorrections,
        unregisteredEnrollments,
        unavailableBlocks);
  }

  private static boolean isWithinTimeWindow(Instant occurredAt, Instant since, Instant until) {
    return !occurredAt.isBefore(since) && !occurredAt.isAfter(until);
  }
}
