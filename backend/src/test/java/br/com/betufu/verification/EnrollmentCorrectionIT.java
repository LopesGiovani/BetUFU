package br.com.betufu.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import br.com.betufu.audit.AuditEntry;
import br.com.betufu.audit.AuditRecorder;
import br.com.betufu.audit.internal.AuditEvent;
import br.com.betufu.audit.internal.AuditEventRepository;
import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.api.FieldViolation;
import br.com.betufu.verification.internal.EnrollmentCorrectionRequest;
import br.com.betufu.verification.internal.EnrollmentCorrectionResponse;
import br.com.betufu.verification.internal.EnrollmentCorrectionService;
import br.com.betufu.verification.internal.MatriculaHmac;
import br.com.betufu.verification.internal.MatriculaHmacKeyProperties;
import br.com.betufu.verification.internal.MatriculaNormalizer;
import br.com.betufu.verification.internal.UfuEnrollment;
import br.com.betufu.verification.internal.UfuEnrollmentRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.lang.reflect.RecordComponent;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Prova de integração da correção administrativa de matrícula sobre PostgreSQL real (D-32, {@code
 * VERI-07}, {@code AUDT-01}).
 *
 * <p>Duas versões de chave ficam ativas — {@code v1} herdada e {@code v2} corrente — porque a
 * verificação da nova matrícula precisa cobrir <strong>todas</strong> as versões (D-35). Um teste
 * que só exercitasse a versão corrente passaria mesmo com a verificação multi-versão quebrada.
 *
 * <p>Cobre: correção bem-sucedida; ordem verificar-antes-de-liberar; colisão sem alterar nada;
 * corrida contra o índice único parcial com reversão integral; conta sem vínculo prévio; correção
 * inócua; preservação do histórico; e a trilha com motivo por extenso e sem matrícula ou digest.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.docker.compose.enabled=false",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration,"
          + "org.springframework.boot.session.autoconfigure.SessionAutoConfiguration,"
          + "org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=betufu-enrollment-correction-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
      "betufu.verification.matricula-hmac.current-version=v2",
      "betufu.verification.matricula-hmac.keys.v1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE=",
      "betufu.verification.matricula-hmac.keys.v2=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDI=",
      "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v2",
      "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE=",
      "BETUFU_MATRICULA_HMAC_KEY_V2=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDI="
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(EnrollmentCorrectionIT.TestOidcConfiguration.class)
class EnrollmentCorrectionIT {

  private static final String ISSUER = "https://idp.invalid";

  private static final String LEGITIMATE_REASON =
      "Registro indevido por terceiro confirmado com a coordenação do curso.";

  private static final String POSTGRES_IMAGE_REFERENCE =
      "docker.io/library/postgres:18.4-trixie@sha256:"
          + "a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636";

  private static final DockerImageName POSTGRES_IMAGE =
      DockerImageName.parse(POSTGRES_IMAGE_REFERENCE).asCompatibleSubstituteFor("postgres");

  @Container
  @ServiceConnection(name = "postgres")
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(POSTGRES_IMAGE)
          .withDatabaseName("betufu_app")
          .withUsername("betufu_app")
          .withPassword("betufu-test-only")
          .withInitScript("postgres-audit-runtime-role.sql");

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private UfuEnrollmentRepository enrollmentRepository;

  @Autowired private AuditEventRepository auditEventRepository;

  @Autowired private AuditRecorder auditRecorder;

  @Autowired private MatriculaHmac matriculaHmac;

  @Autowired private MatriculaHmacKeyProperties keyProperties;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private EnrollmentCorrectionService correctionService;

  @BeforeEach
  void cleanTables() {
    jdbcTemplate.execute(
        "TRUNCATE TABLE public.ufu_enrollment, public.audit_event, public.betufu_profile, public.local_identity CASCADE");
  }

  // ── 1. Correção bem-sucedida ──────────────────────────────────────────────

  @Test
  @DisplayName(
      "D-32: correção libera o vínculo antigo, ativa o novo sob a versão corrente e não remove nenhuma linha")
  void successfulCorrectionReleasesPreviousBindingAndActivatesTheNewOne() {
    UUID actor = createIdentity("admin-geral-sucesso");
    UUID target = createIdentity("alvo-sucesso");

    UUID previousBinding = insertActiveBinding(target, "20241BCC101", "v1");
    Instant previousUpdatedAt = updatedAtOf(previousBinding);

    EnrollmentCorrectionResponse response =
        correctionService.correct(actor, target, "2024-2.BCC/202", LEGITIMATE_REASON);

    // Resposta: apenas referência opaca e estado.
    assertThat(response.targetIdentityId()).isEqualTo(target);
    assertThat(response.status()).isEqualTo(UfuEnrollment.STATUS_ATIVO);
    assertThat(response.correctedAt()).isNotNull();
    assertThat(response.previousBindingReleased()).isTrue();

    // O vínculo antigo continua na base, agora liberado. Nenhuma linha foi removida.
    assertThat(statusOf(previousBinding)).isEqualTo(UfuEnrollment.STATUS_LIBERADO);
    assertThat(updatedAtOf(previousBinding)).isAfterOrEqualTo(previousUpdatedAt);
    assertThat(countRows(target)).isEqualTo(2L);

    // O novo vínculo é o único ativo e usa a versão CORRENTE da chave, não a herdada.
    Optional<UfuEnrollment> active = enrollmentRepository.findActiveByIdentityId(target);
    assertThat(active).isPresent();
    assertThat(active.get().getEnrollmentId()).isNotEqualTo(previousBinding);
    assertThat(active.get().getHmacVersion()).isEqualTo((short) 2);
    assertThat(active.get().getMatriculaHmac())
        .isEqualTo(matriculaHmac.calculateCurrent("20242BCC202"));

    // A antiga voltou ao conjunto registrável, com o digest idêntico ao liberado: o índice
    // único parcial só vale para ATIVO. É exatamente o reparo que a D-32 pretende.
    UUID other = createIdentity("terceiro-reaproveita");
    UUID reusedBinding = insertActiveBinding(other, "20241BCC101", "v1");
    assertThat(enrollmentRepository.findActiveByIdentityId(other).orElseThrow().getMatriculaHmac())
        .isEqualTo(matriculaHmac.calculate("20241BCC101", "v1"));
    assertThat(statusOf(reusedBinding)).isEqualTo(UfuEnrollment.STATUS_ATIVO);
  }

  // ── 2. Ordem: verificar antes de liberar ──────────────────────────────────

  @Test
  @DisplayName(
      "VERI-07: nova matrícula de outra conta, encontrada sob versão de chave não corrente, recusa sem alterar coisa alguma")
  void collisionUnderNonCurrentKeyVersionIsRejectedWithoutTouchingAnything() {
    UUID actor = createIdentity("admin-geral-colisao");
    UUID target = createIdentity("alvo-colisao");
    UUID owner = createIdentity("dona-da-matricula");

    // A dona registrou sob a versão herdada v1; a versão corrente é v2.
    UUID ownerBinding = insertActiveBinding(owner, "20241BCC777", "v1");
    UUID targetBinding = insertActiveBinding(target, "20241BCC111", "v2");
    Instant targetUpdatedAt = updatedAtOf(targetBinding);
    long rowsBefore = countRows();

    ApiProblemException failure =
        catchThrowableOfType(
            () -> correctionService.correct(actor, target, "20241BCC777", LEGITIMATE_REASON),
            ApiProblemException.class);

    assertThat(failure).isNotNull();
    assertThat(failure.status()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(failure.errorCode()).isEqualTo(ApiErrorCode.CORRECTION_MATRICULA_ALREADY_REGISTERED);

    // "A correção não foi aplicada": o vínculo antigo do alvo permanece ATIVO e intocado.
    assertThat(statusOf(targetBinding)).isEqualTo(UfuEnrollment.STATUS_ATIVO);
    assertThat(updatedAtOf(targetBinding)).isEqualTo(targetUpdatedAt);
    assertThat(enrollmentRepository.findActiveByIdentityId(target).orElseThrow().getEnrollmentId())
        .isEqualTo(targetBinding);

    // O vínculo da dona também não foi tocado, e nenhuma linha nasceu.
    assertThat(statusOf(ownerBinding)).isEqualTo(UfuEnrollment.STATUS_ATIVO);
    assertThat(countRows()).isEqualTo(rowsBefore);

    // Trilha do bloqueio: sem referência ao vínculo alheio.
    AuditEvent blocked = singleAuditEvent();
    assertThat(blocked.getOutcome()).isEqualTo(AuditEntry.OUTCOME_BLOCKED);
    assertThat(blocked.getAction()).isEqualTo(AuditEntry.ACTION_CORRECAO_MATRICULA);
    assertThat(blocked.getActorIdentityId()).isEqualTo(actor);
    assertThat(blocked.getTargetIdentityId()).isEqualTo(target);
    assertThat(blocked.getPreviousValue()).contains(targetBinding.toString());
    assertThat(blocked.getNewValue()).isNull();
    assertThat(blocked.getReason()).isEqualTo("Nova matrícula já registrada em outra conta");
  }

  // ── 3. Corrida contra o índice único parcial ──────────────────────────────

  @Test
  @DisplayName(
      "VERI-07: violação do índice único parcial após a verificação vira recusa de negócio e reverte a operação inteira")
  void lostRaceAgainstPartialUniqueIndexRollsBackTheWholeCorrection() {
    UUID actor = createIdentity("admin-geral-corrida");
    UUID target = createIdentity("alvo-corrida");
    UUID intruder = createIdentity("invasor-corrida");

    UUID targetBinding = insertActiveBinding(target, "20241BCC301", "v2");
    Instant targetUpdatedAt = updatedAtOf(targetBinding);
    long rowsBefore = countRows();

    String contestedMatricula = "20242BCC302";
    byte[] contestedDigest =
        matriculaHmac.calculateCurrent(MatriculaNormalizer.normalize(contestedMatricula));

    // Repositório decorado: no instante exato entre a verificação e a inserção, outra conta
    // ganha o vínculo ativo com o mesmo digest. Só o PostgreSQL pode arbitrar daqui em diante.
    RaceInjectingRepository racingRepository =
        new RaceInjectingRepository(enrollmentRepository, intruder, contestedDigest);

    EnrollmentCorrectionService racingService =
        new EnrollmentCorrectionService(
            racingRepository, matriculaHmac, keyProperties, auditRecorder, transactionManager);

    ApiProblemException failure =
        catchThrowableOfType(
            () -> racingService.correct(actor, target, contestedMatricula, LEGITIMATE_REASON),
            ApiProblemException.class);

    // Recusa de negócio publicada, nunca erro interno cru.
    assertThat(failure).isNotNull();
    assertThat(failure.status()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(failure.errorCode()).isEqualTo(ApiErrorCode.CORRECTION_MATRICULA_ALREADY_REGISTERED);
    assertThat(racingRepository.injected()).isTrue();

    // Reversão integral: o vínculo antigo continua ativo e nem a linha do invasor sobreviveu.
    assertThat(statusOf(targetBinding)).isEqualTo(UfuEnrollment.STATUS_ATIVO);
    assertThat(updatedAtOf(targetBinding)).isEqualTo(targetUpdatedAt);
    assertThat(countRows()).isEqualTo(rowsBefore);
    assertThat(enrollmentRepository.findActiveByIdentityId(intruder)).isEmpty();

    // A auditoria do bloqueio sobrevive ao rollback: ela é gravada fora da transação revertida.
    AuditEvent blocked = singleAuditEvent();
    assertThat(blocked.getOutcome()).isEqualTo(AuditEntry.OUTCOME_BLOCKED);
    assertThat(blocked.getAction()).isEqualTo(AuditEntry.ACTION_CORRECAO_MATRICULA);
    assertThat(blocked.getReason()).isEqualTo("Nova matrícula já registrada em outra conta");
  }

  @Test
  @DisplayName(
      "VERI-07: duas correções simultâneas para a mesma nova matrícula deixam exatamente um vencedor e preservam o vínculo antigo da perdedora")
  void concurrentCorrectionsToTheSameMatriculaLeaveExactlyOneWinner() throws Exception {
    UUID actor = createIdentity("admin-geral-concorrente");
    UUID accountA = createIdentity("alvo-concorrente-a");
    UUID accountB = createIdentity("alvo-concorrente-b");

    UUID bindingA = insertActiveBinding(accountA, "20241BCC401", "v2");
    UUID bindingB = insertActiveBinding(accountB, "20241BCC402", "v2");
    String contestedMatricula = "20242BCC403";

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(2);
    List<EnrollmentCorrectionResponse> successes = Collections.synchronizedList(new ArrayList<>());
    List<Exception> failures = Collections.synchronizedList(new ArrayList<>());

    for (UUID account : List.of(accountA, accountB)) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
              successes.add(
                  correctionService.correct(actor, account, contestedMatricula, LEGITIMATE_REASON));
            } catch (Exception e) {
              failures.add(e);
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    boolean finished = doneLatch.await(20, TimeUnit.SECONDS);
    executor.shutdown();

    assertThat(finished).isTrue();
    assertThat(successes).hasSize(1);
    assertThat(failures).hasSize(1);

    assertThat(failures.getFirst()).isInstanceOf(ApiProblemException.class);
    ApiProblemException problem = (ApiProblemException) failures.getFirst();
    assertThat(problem.status()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(problem.errorCode()).isEqualTo(ApiErrorCode.CORRECTION_MATRICULA_ALREADY_REGISTERED);

    // Exatamente uma conta ficou com a matrícula disputada; a outra manteve a sua, ativa.
    UUID winner = successes.getFirst().targetIdentityId();
    UUID loser = winner.equals(accountA) ? accountB : accountA;
    UUID loserBinding = winner.equals(accountA) ? bindingB : bindingA;

    assertThat(statusOf(loserBinding)).isEqualTo(UfuEnrollment.STATUS_ATIVO);
    assertThat(enrollmentRepository.findActiveByIdentityId(loser).orElseThrow().getEnrollmentId())
        .isEqualTo(loserBinding);

    Long activeWithContestedDigest =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.ufu_enrollment WHERE matricula_hmac = ? AND status = 'ATIVO'",
            Long.class,
            matriculaHmac.calculateCurrent(contestedMatricula));
    assertThat(activeWithContestedDigest).isEqualTo(1L);
  }

  // ── 4. Conta sem vínculo ativo ────────────────────────────────────────────

  @Test
  @DisplayName("D-32: conta sem vínculo ativo recebe registro administrativo, sem nada a liberar")
  void accountWithoutActiveBindingReceivesAdministrativeRegistration() {
    UUID actor = createIdentity("admin-geral-sem-vinculo");
    UUID target = createIdentity("alvo-sem-vinculo");

    assertThat(enrollmentRepository.findActiveByIdentityId(target)).isEmpty();

    EnrollmentCorrectionResponse response =
        correctionService.correct(actor, target, "20242BCC505", LEGITIMATE_REASON);

    assertThat(response.status()).isEqualTo(UfuEnrollment.STATUS_ATIVO);
    assertThat(response.previousBindingReleased()).isFalse();
    assertThat(countRows(target)).isEqualTo(1L);

    AuditEvent permitted = singleAuditEvent();
    assertThat(permitted.getOutcome()).isEqualTo(AuditEntry.OUTCOME_PERMITTED);
    assertThat(permitted.getPreviousValue()).isNull();
    assertThat(permitted.getNewValue()).contains("enrollmentId=").contains("keyVersion=2");
    assertThat(permitted.getReason()).isEqualTo(LEGITIMATE_REASON);
  }

  // ── 5. Correção inócua ────────────────────────────────────────────────────

  @Test
  @DisplayName(
      "VERI-07: matrícula normalizada igual à ativa é operação inócua — nenhuma linha nova, nenhum vínculo liberado")
  void normalizedMatriculaEqualToTheCurrentOneIsInocuous() {
    UUID actor = createIdentity("admin-geral-inocuo");
    UUID target = createIdentity("alvo-inocuo");

    UUID binding = insertActiveBinding(target, "20241BCC333", "v2");
    Instant updatedAtBefore = updatedAtOf(binding);

    // Mesma matrícula, escrita com separadores e minúsculas: normaliza para o mesmo valor.
    EnrollmentCorrectionResponse response =
        correctionService.correct(actor, target, " 2024-1.bcc/333 ", LEGITIMATE_REASON);

    assertThat(response.status()).isEqualTo(UfuEnrollment.STATUS_ATIVO);
    assertThat(response.previousBindingReleased()).isFalse();

    assertThat(countRows(target)).isEqualTo(1L);
    assertThat(statusOf(binding)).isEqualTo(UfuEnrollment.STATUS_ATIVO);
    assertThat(updatedAtOf(binding)).isEqualTo(updatedAtBefore);

    AuditEvent permitted = singleAuditEvent();
    assertThat(permitted.getOutcome()).isEqualTo(AuditEntry.OUTCOME_PERMITTED);
    assertThat(permitted.getReason()).isEqualTo(LEGITIMATE_REASON);
    assertThat(permitted.getPreviousValue()).isEqualTo(permitted.getNewValue());
    assertThat(permitted.getPreviousValue()).contains(binding.toString());
  }

  // ── 6. Motivo obrigatório, antes de qualquer escrita ──────────────────────

  @Test
  @DisplayName(
      "AUDT-01: motivo ausente ou curto demais é recusado antes de qualquer escrita, inclusive da própria trilha")
  void missingOrTooShortReasonIsRejectedBeforeAnyWrite() {
    UUID actor = createIdentity("admin-geral-motivo");
    UUID target = createIdentity("alvo-motivo");
    UUID binding = insertActiveBinding(target, "20241BCC606", "v2");

    for (String invalidReason : new String[] {null, "", "   ", "erro"}) {
      ApiProblemException failure =
          catchThrowableOfType(
              () -> correctionService.correct(actor, target, "20242BCC607", invalidReason),
              ApiProblemException.class);

      assertThat(failure).isNotNull();
      assertThat(failure.status()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(failure.errorCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
    }

    // Nenhuma escrita: nem vínculo, nem trilha.
    assertThat(countRows(target)).isEqualTo(1L);
    assertThat(statusOf(binding)).isEqualTo(UfuEnrollment.STATUS_ATIVO);
    assertThat(auditEventRepository.findAll()).isEmpty();
  }

  @Test
  @DisplayName(
      "D-14: motivo vazio e motivo curto demais usam as duas mensagens literais distintas do contrato de cópia")
  void reasonRejectionsCarryTheTwoDistinctLiteralMessages() {
    assertThat(EnrollmentCorrectionRequest.validateReason(null))
        .contains(new FieldViolation("reason", "Informe o motivo da correção."));
    assertThat(EnrollmentCorrectionRequest.validateReason("   "))
        .contains(new FieldViolation("reason", "Informe o motivo da correção."));
    assertThat(EnrollmentCorrectionRequest.validateReason("erro"))
        .contains(
            new FieldViolation(
                "reason",
                "Descreva o motivo com mais detalhe. Ele precisa fazer sentido para quem ler a auditoria depois."));
    assertThat(EnrollmentCorrectionRequest.validateReason(LEGITIMATE_REASON)).isEmpty();

    // As mesmas mensagens chegam ao formulário pela validação declarativa do DTO.
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();

      assertThat(
              messagesFor(
                  validator,
                  new EnrollmentCorrectionRequest(UUID.randomUUID(), "20242BCC700", "  ")))
          .containsExactly("Informe o motivo da correção.");

      assertThat(
              messagesFor(
                  validator,
                  new EnrollmentCorrectionRequest(UUID.randomUUID(), "20242BCC700", "erro")))
          .containsExactly(
              "Descreva o motivo com mais detalhe. Ele precisa fazer sentido para quem ler a auditoria depois.");

      assertThat(
              messagesFor(
                  validator,
                  new EnrollmentCorrectionRequest(
                      UUID.randomUUID(), "20242BCC700", LEGITIMATE_REASON)))
          .isEmpty();
    }
  }

  // ── 7. Histórico preservado ───────────────────────────────────────────────

  @Test
  @DisplayName(
      "D-32: correções sucessivas acumulam histórico; nenhuma linha é removida e sempre há exatamente um vínculo ativo")
  void successiveCorrectionsPreserveHistoryAndNeverLeaveTheAccountWithoutMatricula() {
    UUID actor = createIdentity("admin-geral-historico");
    UUID target = createIdentity("alvo-historico");

    insertActiveBinding(target, "20241BCC801", "v1");

    correctionService.correct(actor, target, "20242BCC802", LEGITIMATE_REASON);
    correctionService.correct(actor, target, "20242BCC803", LEGITIMATE_REASON);

    assertThat(countRows(target)).isEqualTo(3L);

    Long released =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.ufu_enrollment WHERE identity_id = ? AND status = 'LIBERADO'",
            Long.class,
            target);
    assertThat(released).isEqualTo(2L);

    Long active =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.ufu_enrollment WHERE identity_id = ? AND status = 'ATIVO'",
            Long.class,
            target);
    assertThat(active).isEqualTo(1L);

    assertThat(enrollmentRepository.findAllByIdentityId(target)).hasSize(3);
  }

  // ── 8. Trilha: motivo por extenso, sem matrícula e sem digest ─────────────

  @Test
  @DisplayName(
      "AUDT-01: a trilha guarda o motivo por extenso e as referências dos dois vínculos, nunca a matrícula nem o digest")
  void auditKeepsTheIntegralReasonAndNeverTheMatriculaOrDigest() {
    UUID actor = createIdentity("admin-geral-trilha");
    UUID target = createIdentity("alvo-trilha");

    UUID previousBinding = insertActiveBinding(target, "20241BCC901", "v1");
    byte[] previousDigest = matriculaHmac.calculate("20241BCC901", "v1");

    String longReason =
        "Matrícula reemitida pela DIRAC após incorporação do curso. "
            + "O número anterior deixou de existir no registro acadêmico e a pessoa "
            + "apresentou o comunicado oficial na secretaria, conferido presencialmente "
            + "pela coordenação antes desta correção administrativa.";
    assertThat(longReason.length()).isGreaterThan(120);

    String newMatricula = "20242BCC902";
    correctionService.correct(actor, target, newMatricula, longReason);

    UfuEnrollment created = enrollmentRepository.findActiveByIdentityId(target).orElseThrow();
    byte[] newDigest = created.getMatriculaHmac();

    AuditEvent permitted = singleAuditEvent();

    // Ator, alvo, ação e resultado.
    assertThat(permitted.getActorIdentityId()).isEqualTo(actor);
    assertThat(permitted.getTargetIdentityId()).isEqualTo(target);
    assertThat(permitted.getAction()).isEqualTo(AuditEntry.ACTION_CORRECAO_MATRICULA);
    assertThat(permitted.getOutcome()).isEqualTo(AuditEntry.OUTCOME_PERMITTED);

    // Motivo por extenso, byte a byte, sem truncamento.
    assertThat(permitted.getReason()).isEqualTo(longReason);

    // Referências e versões dos vínculos antigo e novo.
    assertThat(permitted.getPreviousValue())
        .isEqualTo("enrollmentId=" + previousBinding + ";keyVersion=1");
    assertThat(permitted.getNewValue())
        .isEqualTo("enrollmentId=" + created.getEnrollmentId() + ";keyVersion=2");

    // Nem número, nem digest, em nenhuma coluna textual do evento.
    String recorded =
        String.join(
            "|",
            nullSafe(permitted.getActorRole()),
            nullSafe(permitted.getPreviousValue()),
            nullSafe(permitted.getNewValue()),
            nullSafe(permitted.getReason()),
            nullSafe(permitted.getCorrelationId()),
            nullSafe(permitted.getIdempotencyKey()));

    assertThat(recorded)
        .doesNotContain(newMatricula)
        .doesNotContain("20241BCC901")
        .doesNotContain(HexFormat.of().formatHex(newDigest))
        .doesNotContain(HexFormat.of().formatHex(previousDigest))
        .doesNotContain(Base64.getEncoder().encodeToString(newDigest))
        .doesNotContain(Base64.getEncoder().encodeToString(previousDigest));

    assertThat(recorded.toLowerCase(Locale.ROOT)).doesNotContain("hmac=").doesNotContain("digest");
  }

  // ── 9. Allowlist do DTO de resposta ───────────────────────────────────────

  @Test
  @DisplayName(
      "PRIV-01: o DTO de resposta expõe exatamente quatro campos e nenhum deles é matrícula, digest ou versão de chave")
  void responseRecordExposesOnlyTheApprovedAllowlist() {
    RecordComponent[] components = EnrollmentCorrectionResponse.class.getRecordComponents();

    List<String> names = Arrays.stream(components).map(RecordComponent::getName).toList();
    List<String> types =
        Arrays.stream(components).map(component -> component.getType().getName()).toList();

    assertThat(names)
        .containsExactly("targetIdentityId", "status", "correctedAt", "previousBindingReleased");
    assertThat(types)
        .containsExactly("java.util.UUID", "java.lang.String", "java.time.Instant", "boolean");

    for (RecordComponent component : components) {
      String name = component.getName().toLowerCase(Locale.ROOT);
      assertThat(name)
          .doesNotContain("matricula")
          .doesNotContain("digest")
          .doesNotContain("hmac")
          .doesNotContain("version");
      assertThat(component.getType().isArray()).isFalse();
    }
  }

  // ── Auxiliares ────────────────────────────────────────────────────────────

  private static List<String> messagesFor(
      Validator validator, EnrollmentCorrectionRequest request) {
    return validator.validate(request).stream()
        .map(ConstraintViolation::getMessage)
        .sorted()
        .toList();
  }

  private static String nullSafe(String value) {
    return value == null ? "" : value;
  }

  private UUID createIdentity(String subject) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.local_identity (identity_id, issuer, subject) VALUES (?, ?, ?)",
        id,
        ISSUER,
        subject);
    return id;
  }

  private UUID insertActiveBinding(UUID identityId, String matricula, String keyVersion) {
    byte[] digest = matriculaHmac.calculate(MatriculaNormalizer.normalize(matricula), keyVersion);
    short version = Short.parseShort(keyVersion.substring(1));
    UUID bindingId = UUID.randomUUID();
    jdbcTemplate.update(
        """
                INSERT INTO public.ufu_enrollment (
                    enrollment_id,
                    identity_id,
                    matricula_hmac,
                    hmac_version,
                    status
                ) VALUES (?, ?, ?, ?, 'ATIVO')
                """,
        bindingId,
        identityId,
        digest,
        version);
    return bindingId;
  }

  private String statusOf(UUID enrollmentId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM public.ufu_enrollment WHERE enrollment_id = ?",
        String.class,
        enrollmentId);
  }

  private Instant updatedAtOf(UUID enrollmentId) {
    Timestamp timestamp =
        jdbcTemplate.queryForObject(
            "SELECT updated_at FROM public.ufu_enrollment WHERE enrollment_id = ?",
            Timestamp.class,
            enrollmentId);
    return timestamp != null ? timestamp.toInstant() : null;
  }

  private long countRows() {
    Long count =
        jdbcTemplate.queryForObject("SELECT count(*) FROM public.ufu_enrollment", Long.class);
    return count != null ? count : -1L;
  }

  private long countRows(UUID identityId) {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.ufu_enrollment WHERE identity_id = ?",
            Long.class,
            identityId);
    return count != null ? count : -1L;
  }

  private AuditEvent singleAuditEvent() {
    List<AuditEvent> events = auditEventRepository.findAll();
    assertThat(events).hasSize(1);
    return events.getFirst();
  }

  /**
   * Decorador que injeta um vínculo ativo concorrente com o mesmo digest imediatamente antes da
   * inserção do serviço, dentro da mesma transação.
   *
   * <p>É a única forma determinística de exercitar o ramo de violação do índice único parcial: uma
   * corrida real por relógio seria intermitente, e um teste intermitente não prova regressão.
   */
  private static final class RaceInjectingRepository implements UfuEnrollmentRepository {

    private final UfuEnrollmentRepository delegate;
    private final UUID competingIdentityId;
    private final byte[] competingDigest;
    private boolean armed = true;
    private boolean injected;

    private RaceInjectingRepository(
        UfuEnrollmentRepository delegate, UUID competingIdentityId, byte[] competingDigest) {
      this.delegate = delegate;
      this.competingIdentityId = competingIdentityId;
      this.competingDigest = competingDigest.clone();
    }

    private boolean injected() {
      return injected;
    }

    @Override
    public int insert(UUID identityId, byte[] matriculaHmac, short hmacVersion, String status) {
      if (armed) {
        armed = false;
        injected = true;
        delegate.insert(
            competingIdentityId, competingDigest, hmacVersion, UfuEnrollment.STATUS_ATIVO);
      }
      return delegate.insert(identityId, matriculaHmac, hmacVersion, status);
    }

    @Override
    public Optional<UfuEnrollment> findById(UUID enrollmentId) {
      return delegate.findById(enrollmentId);
    }

    @Override
    public Optional<UfuEnrollment> findActiveByIdentityId(UUID identityId) {
      return delegate.findActiveByIdentityId(identityId);
    }

    @Override
    public boolean existsActiveByMatriculaHmacIn(Collection<byte[]> digests) {
      return delegate.existsActiveByMatriculaHmacIn(digests);
    }

    @Override
    public List<UfuEnrollment> findActiveByMatriculaHmacIn(Collection<byte[]> digests) {
      return delegate.findActiveByMatriculaHmacIn(digests);
    }

    @Override
    public List<UfuEnrollment> findAllByIdentityId(UUID identityId) {
      return delegate.findAllByIdentityId(identityId);
    }

    @Override
    public Optional<UfuEnrollment> findActiveByIdentityIdForUpdate(UUID identityId) {
      return delegate.findActiveByIdentityIdForUpdate(identityId);
    }

    @Override
    public int releaseActiveByIdentityId(UUID identityId) {
      return delegate.releaseActiveByIdentityId(identityId);
    }

    @Override
    public boolean existsIdentityById(UUID identityId) {
      return delegate.existsIdentityById(identityId);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestOidcConfiguration {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-enrollment-correction-it")
              .clientSecret("unused")
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("openid")
              .authorizationUri("https://idp.invalid/authorize")
              .tokenUri("https://idp.invalid/token")
              .jwkSetUri("https://idp.invalid/jwks")
              .issuerUri("https://idp.invalid")
              .userNameAttributeName(IdTokenClaimNames.SUB)
              .clientName("BETUFU Enrollment Correction IT")
              .build();

      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
