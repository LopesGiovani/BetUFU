package br.com.betufu.verification.internal;

import br.com.betufu.audit.AuditEntry;
import br.com.betufu.audit.AuditRecorder;
import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.verification.EnrollmentEligibility;
import br.com.betufu.verification.EnrollmentVerified;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("runtime | integrated")
public class EnrollmentService implements EnrollmentEligibility {

  private static final Pattern VERSION_PATTERN = Pattern.compile("^v([1-9][0-9]*)\\z");

  private final UfuEnrollmentRepository enrollmentRepository;
  private final MatriculaHmac matriculaHmac;
  private final MatriculaHmacKeyProperties keyProperties;
  private final AuditRecorder auditRecorder;
  private final TransactionOperations transactionOperations;
  private final ApplicationEventPublisher eventPublisher;

  public EnrollmentService(
      UfuEnrollmentRepository enrollmentRepository,
      MatriculaHmac matriculaHmac,
      MatriculaHmacKeyProperties keyProperties,
      AuditRecorder auditRecorder) {
    this(enrollmentRepository, matriculaHmac, keyProperties, auditRecorder, null, null);
  }

  public EnrollmentService(
      UfuEnrollmentRepository enrollmentRepository,
      MatriculaHmac matriculaHmac,
      MatriculaHmacKeyProperties keyProperties,
      AuditRecorder auditRecorder,
      PlatformTransactionManager transactionManager) {
    this(
        enrollmentRepository,
        matriculaHmac,
        keyProperties,
        auditRecorder,
        transactionManager,
        null);
  }

  @Autowired
  public EnrollmentService(
      UfuEnrollmentRepository enrollmentRepository,
      MatriculaHmac matriculaHmac,
      MatriculaHmacKeyProperties keyProperties,
      AuditRecorder auditRecorder,
      PlatformTransactionManager transactionManager,
      ApplicationEventPublisher eventPublisher) {
    this.enrollmentRepository =
        Objects.requireNonNull(enrollmentRepository, "enrollmentRepository must not be null");
    this.matriculaHmac = matriculaHmac;
    this.keyProperties = keyProperties;
    this.auditRecorder = auditRecorder;
    this.eventPublisher = eventPublisher;
    this.transactionOperations =
        transactionManager != null
            ? new TransactionTemplate(transactionManager)
            : TransactionOperations.withoutTransaction();
  }

  @Override
  public boolean hasActiveEnrollment(UUID identityId) {
    if (identityId == null) {
      return false;
    }
    return enrollmentRepository.findActiveByIdentityId(identityId).isPresent();
  }

  @Override
  public Status getStatus(UUID identityId) {
    if (identityId == null) {
      return Status.SEM_VINCULO;
    }
    Optional<UfuEnrollment> active = enrollmentRepository.findActiveByIdentityId(identityId);
    if (active.isPresent()) {
      return Status.ATIVO;
    }
    List<UfuEnrollment> all = enrollmentRepository.findAllByIdentityId(identityId);
    if (all.isEmpty()) {
      return Status.SEM_VINCULO;
    }
    return Status.LIBERADO;
  }

  public Optional<UfuEnrollment> findActiveEnrollment(UUID identityId) {
    if (identityId == null) {
      return Optional.empty();
    }
    return enrollmentRepository.findActiveByIdentityId(identityId);
  }

  public EnrollmentStatusResponse register(UUID identityId, String rawMatricula) {
    Objects.requireNonNull(identityId, "identityId must not be null");

    String normalized = MatriculaNormalizer.normalize(rawMatricula);
    if (!MatriculaNormalizer.isValid(normalized)) {
      auditRejection(identityId, "Formato de matrícula inválido");
      throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
    }

    // 1. Recusa conta que já possui vínculo ativo
    Optional<UfuEnrollment> activeForIdentity =
        enrollmentRepository.findActiveByIdentityId(identityId);
    if (activeForIdentity.isPresent()) {
      auditRejection(identityId, "Conta já possui matrícula ativa", activeForIdentity.get());
      throw new ApiProblemException(HttpStatus.CONFLICT, ApiErrorCode.ACCOUNT_ALREADY_ENROLLED);
    }

    // 2. Calcula candidato sob todas as versões de chave ativas e verifica duplicidade
    Set<String> activeVersions =
        (keyProperties != null
                && keyProperties.getKeys() != null
                && !keyProperties.getKeys().isEmpty())
            ? keyProperties.getKeys().keySet()
            : (matriculaHmac != null ? Set.of(matriculaHmac.currentVersion()) : Set.of());

    List<byte[]> candidateDigests = new ArrayList<>();
    for (String v : activeVersions) {
      candidateDigests.add(matriculaHmac.calculate(normalized, v));
    }

    if (!candidateDigests.isEmpty()) {
      List<UfuEnrollment> duplicateEnrollments =
          enrollmentRepository.findActiveByMatriculaHmacIn(candidateDigests);
      if (!duplicateEnrollments.isEmpty()) {
        auditRejection(
            identityId, "Matrícula já registrada em outra conta", duplicateEnrollments.getFirst());
        throw new ApiProblemException(
            HttpStatus.CONFLICT, ApiErrorCode.MATRICULA_ALREADY_REGISTERED);
      }
    }

    // 3. Valida versão HMAC estritamente conforme v[1-9][0-9]* e dentro do SMALLINT positivo antes
    // de gravar
    if (matriculaHmac == null) {
      throw new IllegalStateException("MatriculaHmac component is required for registration");
    }
    short version = parseHmacVersion(matriculaHmac.currentVersion());
    byte[] currentDigest = matriculaHmac.calculateCurrent(normalized);

    EnrollmentStatusResponse response;
    try {
      response =
          transactionOperations.execute(
              status -> {
                enrollmentRepository.insert(
                    identityId, currentDigest, version, UfuEnrollment.STATUS_ATIVO);
                UfuEnrollment created =
                    enrollmentRepository
                        .findActiveByIdentityId(identityId)
                        .orElseThrow(
                            () ->
                                new IllegalStateException(
                                    "Enrollment insert completed without a readable row"));
                recordAudit(identityId, AuditEntry.OUTCOME_PERMITTED, null, created);
                return new EnrollmentStatusResponse(created.getStatus(), created.getCreatedAt());
              });
    } catch (DataIntegrityViolationException ex) {
      String details = extractConstraintViolationDetails(ex);
      if (details.contains("uq_ufu_enrollment_active_identity")) {
        UfuEnrollment existing =
            enrollmentRepository.findActiveByIdentityId(identityId).orElse(null);
        auditRejection(identityId, "Conta já possui matrícula ativa", existing);
        throw new ApiProblemException(HttpStatus.CONFLICT, ApiErrorCode.ACCOUNT_ALREADY_ENROLLED);
      } else if (details.contains("uq_ufu_enrollment_active_hmac")) {
        List<UfuEnrollment> duplicateList =
            candidateDigests.isEmpty()
                ? List.of()
                : enrollmentRepository.findActiveByMatriculaHmacIn(candidateDigests);
        UfuEnrollment existing = duplicateList.isEmpty() ? null : duplicateList.getFirst();
        auditRejection(identityId, "Matrícula já registrada em outra conta", existing);
        throw new ApiProblemException(
            HttpStatus.CONFLICT, ApiErrorCode.MATRICULA_ALREADY_REGISTERED);
      } else {
        throw ex;
      }
    }

    if (eventPublisher != null) {
      eventPublisher.publishEvent(new EnrollmentVerified(identityId));
    }

    return response;
  }

  public void auditRejection(UUID identityId, String reason) {
    auditRejection(identityId, reason, null);
  }

  public void auditRejection(UUID identityId, String reason, UfuEnrollment existingEnrollment) {
    if (identityId == null) {
      return;
    }
    recordAudit(identityId, AuditEntry.OUTCOME_BLOCKED, reason, existingEnrollment);
  }

  private void recordAudit(
      UUID identityId, String outcome, String reason, UfuEnrollment enrollment) {
    if (auditRecorder == null) {
      return;
    }
    short currentKeyVersion =
        (matriculaHmac != null && matriculaHmac.currentVersion() != null)
            ? parseHmacVersion(matriculaHmac.currentVersion())
            : 1;
    String safeMetadata =
        enrollment != null
            ? "enrollmentId="
                + enrollment.getEnrollmentId()
                + ";keyVersion="
                + enrollment.getHmacVersion()
            : "keyVersion=" + currentKeyVersion;

    auditRecorder.record(
        AuditEntry.builder()
            .actorIdentityId(identityId)
            .targetIdentityId(identityId)
            .action(AuditEntry.ACTION_REGISTRO_MATRICULA)
            .outcome(outcome)
            .reason(reason)
            .newValue(safeMetadata)
            .correlationId(MDC.get("correlationId"))
            .build());
  }

  public static short parseHmacVersion(String version) {
    if (version == null) {
      throw new IllegalStateException("HMAC version must not be null");
    }
    Matcher matcher = VERSION_PATTERN.matcher(version);
    if (!matcher.matches()) {
      throw new IllegalStateException("HMAC version must match pattern 'v[1-9][0-9]*': " + version);
    }
    try {
      int num = Integer.parseInt(matcher.group(1));
      if (num > Short.MAX_VALUE) {
        throw new IllegalStateException("HMAC version exceeds positive SMALLINT range: " + version);
      }
      return (short) num;
    } catch (NumberFormatException e) {
      throw new IllegalStateException("HMAC version numeric overflow: " + version, e);
    }
  }

  private static String extractConstraintViolationDetails(Throwable throwable) {
    StringBuilder sb = new StringBuilder();
    Throwable current = throwable;
    while (current != null) {
      if (current.getMessage() != null) {
        sb.append(current.getMessage()).append(" ");
      }
      current = current.getCause();
    }
    return sb.toString();
  }
}
