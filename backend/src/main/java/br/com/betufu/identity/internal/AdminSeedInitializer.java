package br.com.betufu.identity.internal;

import br.com.betufu.audit.AuditEntry;
import br.com.betufu.audit.AuditRecorder;
import br.com.betufu.identity.OperationalRole;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Semente idempotente do primeiro administrador geral (D-47, OQ-4).
 *
 * <p>Regras:
 *
 * <ul>
 *   <li>O identificador configurado deve ser o UUID público de uma conta já existente em {@code
 *       local_identity};
 *   <li>Se a propriedade estiver ausente, em branco, malformada ou apontar para identidade
 *       inexistente, a semente permanece pendente e observável sem criar cadastro, conta ou
 *       conceder papel (fail-closed);
 *   <li>A execução é idempotente: reexecuções deixam uma única concessão, um único registro de
 *       auditoria e não alteram carimbos de data/hora;
 *   <li>Nenhum endpoint privilegiado de bootstrap é exposto.
 * </ul>
 */
@Component
@Profile("runtime | integrated")
public class AdminSeedInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminSeedInitializer.class);

  private final OperationalRoleGrantRepository repository;
  private final AuditRecorder auditRecorder;
  private final String configuredAdminIdentifier;
  private final TransactionOperations transactions;

  @Autowired
  public AdminSeedInitializer(
      OperationalRoleGrantRepository repository,
      AuditRecorder auditRecorder,
      @Value("${betufu.bootstrap.admin-identifier:}") String configuredAdminIdentifier,
      ObjectProvider<PlatformTransactionManager> transactionManagerProvider) {
    this(
        repository,
        auditRecorder,
        configuredAdminIdentifier,
        transactionManagerProvider != null ? transactionManagerProvider.getIfAvailable() : null);
  }

  public AdminSeedInitializer(
      OperationalRoleGrantRepository repository,
      AuditRecorder auditRecorder,
      String configuredAdminIdentifier,
      PlatformTransactionManager transactionManager) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder must not be null");
    this.configuredAdminIdentifier = configuredAdminIdentifier;
    this.transactions =
        transactionManager != null ? new TransactionTemplate(transactionManager) : null;
  }

  @Override
  public void run(ApplicationArguments args) {
    initializeSeed();
  }

  public boolean initializeSeed() {
    return initializeSeedWithIdentifier(this.configuredAdminIdentifier);
  }

  public boolean initializeSeedWithIdentifier(String rawIdentifier) {
    if (rawIdentifier == null || rawIdentifier.isBlank()) {
      return doInitializeSeed(rawIdentifier);
    }
    if (transactions == null) {
      throw new IllegalStateException(
          "Semente D-47 exige PlatformTransactionManager para gravar concessao e auditoria atomicamente");
    }
    Boolean initialized = transactions.execute(status -> doInitializeSeed(rawIdentifier));
    return Boolean.TRUE.equals(initialized);
  }

  private boolean doInitializeSeed(String rawIdentifier) {
    if (rawIdentifier == null || rawIdentifier.isBlank()) {
      log.info(
          "Semente D-47: BETUFU_BOOTSTRAP_ADMIN_IDENTIFIER ausente ou em branco; semente pendente.");
      return false;
    }

    UUID targetIdentityId;
    try {
      targetIdentityId = UUID.fromString(rawIdentifier.trim());
    } catch (IllegalArgumentException ex) {
      log.warn(
          "Semente D-47: BETUFU_BOOTSTRAP_ADMIN_IDENTIFIER contém formato inválido (não é UUID); semente pendente.");
      return false;
    }

    if (!repository.existsIdentityById(targetIdentityId)) {
      log.warn(
          "Semente D-47: Identidade {} não encontrada em local_identity; semente permanece pendente.",
          targetIdentityId);
      return false;
    }

    // Tenta inserir a concessão de forma idempotente via ON CONFLICT DO NOTHING
    int inserted =
        repository.insertGrant(targetIdentityId, OperationalRole.ADMINISTRADOR_GERAL.name(), null);

    if (inserted > 0) {
      auditRecorder.record(
          AuditEntry.builder()
              .actorIdentityId(targetIdentityId)
              .targetIdentityId(targetIdentityId)
              .action(AuditEntry.ACTION_SEMENTE_ADMINISTRADOR_INICIAL)
              .outcome(AuditEntry.OUTCOME_PERMITTED)
              .newValue(OperationalRole.ADMINISTRADOR_GERAL.name())
              .reason("Semente do primeiro administrador geral D-47")
              .build());
      log.info(
          "Semente D-47: Papel ADMINISTRADOR_GERAL concedido com sucesso à identidade {}.",
          targetIdentityId);
      return true;
    }

    log.debug(
        "Semente D-47: Concessão para identidade {} já existia; nenhuma alteração realizada.",
        targetIdentityId);
    return false;
  }
}
