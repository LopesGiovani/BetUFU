package br.com.betufu.identity.internal;

import br.com.betufu.audit.AuditEntry;
import br.com.betufu.audit.AuditRecorder;
import br.com.betufu.identity.OperationalRole;
import br.com.betufu.identity.OperationalRoleQuery;
import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.session.events.OperationalRoleChanged;
import br.com.betufu.sports.CompetitionScopeCatalog;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("runtime | integrated")
public class RoleGrantService implements OperationalRoleQuery {

  private final OperationalRoleGrantRepository repository;
  private final BetufuProfileService profileService;
  private final AuditRecorder auditRecorder;
  private final ApplicationEventPublisher eventPublisher;
  @Nullable private final CompetitionScopeCatalog competitionScopeCatalog;
  private final TransactionOperations transactionOperations;

  public RoleGrantService(
      OperationalRoleGrantRepository repository,
      BetufuProfileService profileService,
      AuditRecorder auditRecorder,
      ApplicationEventPublisher eventPublisher) {
    this(repository, profileService, auditRecorder, eventPublisher, null, null);
  }

  @Autowired
  public RoleGrantService(
      OperationalRoleGrantRepository repository,
      BetufuProfileService profileService,
      AuditRecorder auditRecorder,
      ApplicationEventPublisher eventPublisher,
      ObjectProvider<CompetitionScopeCatalog> competitionScopeCatalogProvider,
      @Nullable PlatformTransactionManager transactionManager) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.profileService = profileService;
    this.auditRecorder = auditRecorder;
    this.eventPublisher = eventPublisher;
    this.competitionScopeCatalog =
        competitionScopeCatalogProvider != null
            ? competitionScopeCatalogProvider.getIfAvailable()
            : null;
    this.transactionOperations =
        transactionManager != null
            ? new TransactionTemplate(transactionManager)
            : TransactionOperations.withoutTransaction();
  }

  @Override
  @Transactional(readOnly = true)
  public Set<GrantedRole> findActiveRoles(UUID identityId) {
    if (identityId == null) {
      return Set.of();
    }
    List<OperationalRoleGrant> grants = repository.findActiveByIdentityId(identityId);
    if (grants.isEmpty()) {
      return Set.of();
    }
    Set<GrantedRole> roles = new HashSet<>();
    for (OperationalRoleGrant grant : grants) {
      roles.add(new GrantedRole(grant.getRoleName(), grant.getScopeRef()));
    }
    return Collections.unmodifiableSet(roles);
  }

  // Sem @Transactional: a recusa por falta de autoridade precisa GRAVAR a trilha de
  // auditoria, e uma transacao readOnly rejeita a escrita, mascarando o 403 com um 500.
  // Segue o padrao do EnrollmentService, que tambem nao e transacional por este motivo.
  public RoleGrantResponse getRoleStatus(UUID actorIdentityId, UUID targetIdentityId) {
    Objects.requireNonNull(actorIdentityId, "actorIdentityId must not be null");
    Objects.requireNonNull(targetIdentityId, "targetIdentityId must not be null");

    if (!hasRole(actorIdentityId, OperationalRole.ADMINISTRADOR_GERAL)) {
      recordAudit(
          actorIdentityId,
          targetIdentityId,
          AuditEntry.ACTION_CONSULTA_OPERACIONAL,
          AuditEntry.OUTCOME_BLOCKED,
          null,
          null,
          "Ator não possui papel ADMINISTRADOR_GERAL");
      throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
    }

    return findRoleStatus(targetIdentityId);
  }

  @Transactional(readOnly = true)
  public RoleGrantResponse getRoleStatus(UUID targetIdentityId) {
    return findRoleStatus(targetIdentityId);
  }

  private RoleGrantResponse findRoleStatus(UUID targetIdentityId) {
    Objects.requireNonNull(targetIdentityId, "targetIdentityId must not be null");

    String nickname = "";
    if (profileService != null) {
      nickname =
          profileService.findProfile(targetIdentityId).map(BetufuProfile::getNickname).orElse("");
    }

    List<OperationalRoleGrant> activeGrants = repository.findActiveByIdentityId(targetIdentityId);
    List<RoleGrantResponse.RoleStatusDto> roleList = new ArrayList<>();
    Set<OperationalRole> presentRoles = EnumSet.noneOf(OperationalRole.class);

    for (OperationalRoleGrant grant : activeGrants) {
      roleList.add(
          new RoleGrantResponse.RoleStatusDto(
              grant.getRoleName(), OperationalRoleGrant.STATUS_VIGENTE, grant.getScopeRef()));
      presentRoles.add(grant.getRoleName());
    }

    for (OperationalRole role : OperationalRole.values()) {
      if (!presentRoles.contains(role)) {
        roleList.add(new RoleGrantResponse.RoleStatusDto(role, "NAO_CONCEDIDO", null));
      }
    }

    return new RoleGrantResponse(targetIdentityId, nickname, roleList);
  }

  public RoleGrantResponse grantRole(
      UUID actorIdentityId, UUID targetIdentityId, OperationalRole role, UUID scopeRef) {
    Objects.requireNonNull(actorIdentityId, "actorIdentityId must not be null");
    Objects.requireNonNull(targetIdentityId, "targetIdentityId must not be null");
    Objects.requireNonNull(role, "role must not be null");

    // Verificação de autoridade: ator deve ser ADMINISTRADOR_GERAL
    if (!hasRole(actorIdentityId, OperationalRole.ADMINISTRADOR_GERAL)) {
      recordAudit(
          actorIdentityId,
          targetIdentityId,
          AuditEntry.ACTION_PAPEL_CONCEDIDO,
          AuditEntry.OUTCOME_BLOCKED,
          null,
          formatRoleValue(role, scopeRef),
          "Ator não possui papel ADMINISTRADOR_GERAL");
      throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
    }

    // Guarda (a): autoconcessão bloqueada (D-48a)
    if (actorIdentityId.equals(targetIdentityId)) {
      recordAudit(
          actorIdentityId,
          targetIdentityId,
          AuditEntry.ACTION_AUTOCONCESSAO_PAPEL,
          AuditEntry.OUTCOME_BLOCKED,
          null,
          role.name(),
          "Autoconcessão bloqueada");
      throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.SELF_GRANT_BLOCKED);
    }

    requireConsistentScope(
        actorIdentityId, targetIdentityId, role, scopeRef, AuditEntry.ACTION_PAPEL_CONCEDIDO);
    requireExistingCompetition(actorIdentityId, targetIdentityId, role, scopeRef);

    transactionOperations.execute(
        status -> {
          int inserted = repository.insertGrant(targetIdentityId, role.name(), scopeRef);
          if (inserted > 0) {
            recordAudit(
                actorIdentityId,
                targetIdentityId,
                AuditEntry.ACTION_PAPEL_CONCEDIDO,
                AuditEntry.OUTCOME_PERMITTED,
                null,
                formatRoleValue(role, scopeRef),
                null);

            String targetSubject =
                repository.findSubjectByIdentityId(targetIdentityId).orElse(null);
            if (eventPublisher != null) {
              eventPublisher.publishEvent(
                  new OperationalRoleChanged(
                      targetIdentityId, targetSubject, role.name(), "CONCEDIDO", actorIdentityId));
            }
          }
          return null;
        });

    return findRoleStatus(targetIdentityId);
  }

  public RoleGrantResponse revokeRole(
      UUID actorIdentityId, UUID targetIdentityId, OperationalRole role, UUID scopeRef) {
    Objects.requireNonNull(actorIdentityId, "actorIdentityId must not be null");
    Objects.requireNonNull(targetIdentityId, "targetIdentityId must not be null");
    Objects.requireNonNull(role, "role must not be null");

    // Verificação de autoridade: ator deve ser ADMINISTRADOR_GERAL
    if (!hasRole(actorIdentityId, OperationalRole.ADMINISTRADOR_GERAL)) {
      recordAudit(
          actorIdentityId,
          targetIdentityId,
          AuditEntry.ACTION_PAPEL_REVOGADO,
          AuditEntry.OUTCOME_BLOCKED,
          formatRoleValue(role, scopeRef),
          null,
          "Ator não possui papel ADMINISTRADOR_GERAL");
      throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
    }

    requireConsistentScope(
        actorIdentityId, targetIdentityId, role, scopeRef, AuditEntry.ACTION_PAPEL_REVOGADO);

    try {
      transactionOperations.execute(
          status -> {
            // Guarda (b): revogar o próprio papel de administrador geral quando for o último
            // (D-48b)
            if (role == OperationalRole.ADMINISTRADOR_GERAL
                && actorIdentityId.equals(targetIdentityId)) {
              List<OperationalRoleGrant> activeAdmins =
                  repository.findActiveRolesForUpdate(OperationalRole.ADMINISTRADOR_GERAL);
              if (activeAdmins.size() <= 1) {
                throw new LastAdminRevocationBlockedException();
              }
            }

            Optional<OperationalRoleGrant> activeGrant =
                repository.findActiveGrant(targetIdentityId, role, scopeRef);
            if (activeGrant.isPresent()) {
              OperationalRoleGrant grant = activeGrant.get();
              repository.revokeGrant(grant.getGrantId(), Instant.now());

              recordAudit(
                  actorIdentityId,
                  targetIdentityId,
                  AuditEntry.ACTION_PAPEL_REVOGADO,
                  AuditEntry.OUTCOME_PERMITTED,
                  formatRoleValue(role, scopeRef),
                  null,
                  null);

              String targetSubject =
                  repository.findSubjectByIdentityId(targetIdentityId).orElse(null);
              if (eventPublisher != null) {
                eventPublisher.publishEvent(
                    new OperationalRoleChanged(
                        targetIdentityId, targetSubject, role.name(), "REVOGADO", actorIdentityId));
              }
            }

            return null;
          });
    } catch (LastAdminRevocationBlockedException ex) {
      recordAudit(
          actorIdentityId,
          targetIdentityId,
          AuditEntry.ACTION_REVOGACAO_ULTIMO_ADMINISTRADOR,
          AuditEntry.OUTCOME_BLOCKED,
          role.name(),
          null,
          "Último administrador geral");
      throw new ApiProblemException(
          HttpStatus.CONFLICT, ApiErrorCode.LAST_ADMIN_REVOCATION_BLOCKED);
    }

    return findRoleStatus(targetIdentityId);
  }

  private void recordAudit(
      UUID actorIdentityId,
      UUID targetIdentityId,
      String action,
      String outcome,
      String previousValue,
      String newValue,
      String reason) {
    if (auditRecorder == null) {
      return;
    }
    auditRecorder.record(
        AuditEntry.builder()
            .actorIdentityId(actorIdentityId)
            .targetIdentityId(targetIdentityId)
            .action(action)
            .outcome(outcome)
            .previousValue(previousValue)
            .newValue(newValue)
            .reason(reason)
            .correlationId(MDC.get("correlationId"))
            .build());
  }

  /**
   * Papel escopado exige escopo; papel global o proíbe. Sem esta guarda, um
   * ADMINISTRADOR_COMPETICAO nasce valendo em toda competição, e um ADMINISTRADOR_GERAL "escopado"
   * aparenta autoridade limitada enquanto a concessão é irrestrita. A mesma regra é repetida como
   * CHECK no banco: o serviço não é a única porta para a tabela.
   */
  private void requireConsistentScope(
      UUID actorIdentityId,
      UUID targetIdentityId,
      OperationalRole role,
      UUID scopeRef,
      String action) {
    boolean consistent = role.requiresScope() ? scopeRef != null : scopeRef == null;
    if (consistent) {
      return;
    }
    String reason =
        role.requiresScope() ? "Papel escopado exige scopeRef" : "Papel global não admite scopeRef";
    recordAudit(
        actorIdentityId,
        targetIdentityId,
        action,
        AuditEntry.OUTCOME_BLOCKED,
        null,
        formatRoleValue(role, scopeRef),
        reason);
    throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
  }

  private static String formatRoleValue(OperationalRole role, UUID scopeRef) {
    if (scopeRef == null) {
      return role.name();
    }
    return role.name() + ";scope=" + scopeRef;
  }

  private void requireExistingCompetition(
      UUID actorIdentityId, UUID targetIdentityId, OperationalRole role, UUID scopeRef) {
    if (role != OperationalRole.ADMINISTRADOR_COMPETICAO) {
      return;
    }
    if (competitionScopeCatalog == null || !competitionScopeCatalog.existsCompetition(scopeRef)) {
      recordAudit(
          actorIdentityId,
          targetIdentityId,
          AuditEntry.ACTION_PAPEL_CONCEDIDO,
          AuditEntry.OUTCOME_BLOCKED,
          null,
          formatRoleValue(role, scopeRef),
          "Competição usada como escopo não existe");
      throw new ApiProblemException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND);
    }
  }

  private static final class LastAdminRevocationBlockedException extends RuntimeException {}
}
