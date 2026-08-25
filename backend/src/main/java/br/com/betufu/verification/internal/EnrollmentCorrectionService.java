package br.com.betufu.verification.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import br.com.betufu.audit.AuditEntry;
import br.com.betufu.audit.AuditRecorder;
import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.api.FieldViolation;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Correção administrativa da matrícula UFU (D-32).
 *
 * <p>Para a pessoa a matrícula é imutável; o único caminho de reparo — erro de digitação, matrícula
 * reemitida e registro indevido por terceiro (D-27) — é esta operação, restrita ao administrador
 * geral pela fronteira de autorização.
 *
 * <p>A ordem dos passos é contrato de segurança, não estilo:
 *
 * <ol>
 *   <li><strong>Motivo</strong> — recusado antes de <em>qualquer</em> escrita, inclusive a da
 *       própria trilha. O motivo passa a ser permanente e não pode ser apagado depois; exigi-lo
 *       primeiro é o que garante que nenhuma correção exista sem justificativa registrável.</li>
 *   <li><strong>Conta alvo</strong> — precisa existir antes que a trilha possa referenciá-la, já
 *       que {@code audit_event.target_identity_id} tem chave estrangeira para a identidade.</li>
 *   <li><strong>Normalizar e validar</strong> a nova matrícula exatamente como no registro comum,
 *       antes de qualquer cálculo criptográfico (D-29).</li>
 *   <li><strong>Verificar sob todas as versões de chave ativas</strong> (D-35). Pertencer a outra
 *       conta recusa e <em>não altera nada</em>. Liberar primeiro e verificar depois deixaria a
 *       conta sem matrícula quando a correção falhasse, enquanto o {@code 02-UI-SPEC.md} promete ao
 *       operador que “a correção não foi aplicada”.</li>
 *   <li><strong>Na mesma transação</strong>, liberar o vínculo antigo e inserir o novo sob a versão
 *       corrente. O índice único parcial só vale para {@code ATIVO}, então a liberação devolve o
 *       digest antigo ao conjunto registrável — o reparo que a D-32 pretende.</li>
 *   <li><strong>Traduzir violação de índice pelo nome da constraint</strong>. Corrida perdida vira
 *       recusa de negócio publicada, nunca erro interno cru, e reverte a operação inteira.</li>
 *   <li><strong>Auditar</strong> ator, alvo, ação, resultado, motivo por extenso e as referências
 *       dos vínculos antigo e novo com a versão de chave de cada um. <strong>Nunca o digest e nunca
 *       o número</strong> — decisão de OQ-3, e o que permite ao expurgo da D-37 operar só sobre a
 *       tabela de vínculo sem colidir com a imutabilidade da trilha.</li>
 * </ol>
 *
 * <p><strong>Não existe remoção sem substituição.</strong> A liberação e o registro vivem na mesma
 * transação e não há método público que apenas libere: uma conta nunca fica sem matrícula por ação
 * administrativa.
 */
@Service
@Profile("runtime | integrated")
public class EnrollmentCorrectionService {

    static final String REASON_INVALID_FORMAT = "Formato de matrícula inválido";
    static final String REASON_ALREADY_REGISTERED = "Nova matrícula já registrada em outra conta";
    static final String REASON_TARGET_RACE =
            "Conta alvo recebeu outro vínculo ativo durante a correção";

    private final UfuEnrollmentRepository enrollmentRepository;
    private final MatriculaHmac matriculaHmac;
    private final MatriculaHmacKeyProperties keyProperties;
    private final AuditRecorder auditRecorder;
    private final TransactionOperations transactionOperations;

    /**
     * O gerenciador de transação é obrigatório e não tem variante sem transação. Liberar o vínculo
     * antigo e registrar o novo precisam cair juntos; um caminho sem transação transformaria uma
     * falha parcial em remoção sem substituição, que nenhuma decisão autorizou.
     */
    public EnrollmentCorrectionService(
            UfuEnrollmentRepository enrollmentRepository,
            MatriculaHmac matriculaHmac,
            MatriculaHmacKeyProperties keyProperties,
            AuditRecorder auditRecorder,
            PlatformTransactionManager transactionManager) {
        this.enrollmentRepository = Objects.requireNonNull(
                enrollmentRepository, "enrollmentRepository must not be null");
        this.matriculaHmac = Objects.requireNonNull(matriculaHmac, "matriculaHmac must not be null");
        this.keyProperties = keyProperties;
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder must not be null");
        this.transactionOperations = new TransactionTemplate(Objects.requireNonNull(
                transactionManager, "transactionManager must not be null"));
    }

    /**
     * Corrige a matrícula da conta alvo em uma única transação.
     *
     * @param actorIdentityId identificador público do administrador geral que comanda a correção
     * @param request identificador público do alvo, nova matrícula e motivo
     * @return confirmação com estado resultante, sem número, digest ou versão de chave
     */
    public EnrollmentCorrectionResponse correct(
            UUID actorIdentityId, EnrollmentCorrectionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return correct(
                actorIdentityId,
                request.targetIdentityId(),
                request.matricula(),
                request.reason());
    }

    public EnrollmentCorrectionResponse correct(
            UUID actorIdentityId,
            UUID targetIdentityId,
            String rawMatricula,
            String rawReason) {
        Objects.requireNonNull(actorIdentityId, "actorIdentityId must not be null");

        // 1. Motivo obrigatório e integral — antes de qualquer escrita, inclusive de auditoria.
        Optional<FieldViolation> reasonViolation = EnrollmentCorrectionRequest.validateReason(rawReason);
        if (reasonViolation.isPresent()) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
        }
        String reason = rawReason.trim();

        // 2. Conta alvo precisa existir antes que a trilha possa referenciá-la.
        if (targetIdentityId == null || !enrollmentRepository.existsIdentityById(targetIdentityId)) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
        }

        // 3. Normalizar e validar antes de qualquer cálculo criptográfico (D-29).
        String normalized = MatriculaNormalizer.normalize(rawMatricula);
        if (!MatriculaNormalizer.isValid(normalized)) {
            recordAudit(
                    actorIdentityId,
                    targetIdentityId,
                    AuditEntry.OUTCOME_BLOCKED,
                    null,
                    null,
                    REASON_INVALID_FORMAT);
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
        }

        UfuEnrollment currentActive =
                enrollmentRepository.findActiveByIdentityId(targetIdentityId).orElse(null);
        String currentReference = reference(currentActive);

        // 4. Verificar a nova matrícula sob TODAS as versões de chave ativas (D-35),
        //    antes de liberar qualquer coisa.
        List<byte[]> candidateDigests = candidateDigests(normalized);
        List<UfuEnrollment> matches = candidateDigests.isEmpty()
                ? List.of()
                : enrollmentRepository.findActiveByMatriculaHmacIn(candidateDigests);

        boolean belongsToAnotherAccount = matches.stream()
                .anyMatch(match -> !targetIdentityId.equals(match.getIdentityId()));
        if (belongsToAnotherAccount) {
            // Nada é alterado: o vínculo antigo permanece ativo.
            // A referência do vínculo alheio não entra na trilha — o evento é sobre esta conta.
            recordAudit(
                    actorIdentityId,
                    targetIdentityId,
                    AuditEntry.OUTCOME_BLOCKED,
                    currentReference,
                    null,
                    REASON_ALREADY_REGISTERED);
            throw new ApiProblemException(
                    HttpStatus.CONFLICT, ApiErrorCode.CORRECTION_MATRICULA_ALREADY_REGISTERED);
        }

        // 4b. Operação inócua: a matrícula normalizada já é a que está ativa nesta conta.
        //     Liberar e reinserir criaria histórico sem mudança alguma de vínculo.
        UfuEnrollment ownMatch = matches.stream()
                .filter(match -> targetIdentityId.equals(match.getIdentityId()))
                .findFirst()
                .orElse(null);
        if (ownMatch != null) {
            String ownReference = reference(ownMatch);
            recordAudit(
                    actorIdentityId,
                    targetIdentityId,
                    AuditEntry.OUTCOME_PERMITTED,
                    ownReference,
                    ownReference,
                    reason);
            return new EnrollmentCorrectionResponse(
                    targetIdentityId, ownMatch.getStatus(), ownMatch.getCreatedAt(), false);
        }

        short version = EnrollmentService.parseHmacVersion(matriculaHmac.currentVersion());
        byte[] currentDigest = matriculaHmac.calculateCurrent(normalized);

        try {
            // 5. Liberação e registro na mesma transação.
            return transactionOperations.execute(status -> {
                UfuEnrollment previous = enrollmentRepository
                        .findActiveByIdentityIdForUpdate(targetIdentityId)
                        .orElse(null);
                String previousReference = reference(previous);

                boolean released = previous != null
                        && enrollmentRepository.releaseActiveByIdentityId(targetIdentityId) > 0;

                enrollmentRepository.insert(
                        targetIdentityId, currentDigest, version, UfuEnrollment.STATUS_ATIVO);
                UfuEnrollment created = enrollmentRepository.findActiveByIdentityId(targetIdentityId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Enrollment insert completed without a readable row"));

                recordAudit(
                        actorIdentityId,
                        targetIdentityId,
                        AuditEntry.OUTCOME_PERMITTED,
                        previousReference,
                        reference(created),
                        reason);

                return new EnrollmentCorrectionResponse(
                        targetIdentityId, created.getStatus(), created.getCreatedAt(), released);
            });
        } catch (DataIntegrityViolationException ex) {
            // 6. Corrida perdida: a transação inteira já foi revertida e o vínculo antigo
            //    continua ativo. A trilha do bloqueio é gravada fora dela, para sobreviver.
            String details = extractConstraintViolationDetails(ex);
            if (details.contains("uq_ufu_enrollment_active_hmac")) {
                recordAudit(
                        actorIdentityId,
                        targetIdentityId,
                        AuditEntry.OUTCOME_BLOCKED,
                        currentReference,
                        null,
                        REASON_ALREADY_REGISTERED);
                throw new ApiProblemException(
                        HttpStatus.CONFLICT, ApiErrorCode.CORRECTION_MATRICULA_ALREADY_REGISTERED);
            }
            if (details.contains("uq_ufu_enrollment_active_identity")) {
                recordAudit(
                        actorIdentityId,
                        targetIdentityId,
                        AuditEntry.OUTCOME_BLOCKED,
                        currentReference,
                        null,
                        REASON_TARGET_RACE);
                throw new ApiProblemException(
                        HttpStatus.CONFLICT, ApiErrorCode.CORRECTION_MATRICULA_ALREADY_REGISTERED);
            }
            // Outra violação não é recusa de negócio conhecida e permanece erro interno.
            throw ex;
        }
    }

    /**
     * Calcula o candidato sob cada versão de chave ativa, replicando o predicado do registro comum.
     * Uma versão ausente do conjunto ativo produziria falso negativo e permitiria dois vínculos
     * ativos para a mesma matrícula sob chaves diferentes.
     */
    private List<byte[]> candidateDigests(String normalized) {
        Set<String> activeVersions =
                (keyProperties != null && keyProperties.getKeys() != null && !keyProperties.getKeys().isEmpty())
                        ? keyProperties.getKeys().keySet()
                        : Set.of(matriculaHmac.currentVersion());

        List<byte[]> digests = new ArrayList<>();
        for (String version : activeVersions) {
            digests.add(matriculaHmac.calculate(normalized, version));
        }
        return digests;
    }

    /**
     * Referência auditável do vínculo: identificador da linha e versão da chave, jamais o digest e
     * jamais o número. É este formato que permite ao expurgo da D-37 operar sobre a tabela de
     * vínculo sem tocar na trilha imutável.
     */
    private static String reference(UfuEnrollment enrollment) {
        if (enrollment == null) {
            return null;
        }
        return "enrollmentId=" + enrollment.getEnrollmentId()
                + ";keyVersion=" + enrollment.getHmacVersion();
    }

    private void recordAudit(
            UUID actorIdentityId,
            UUID targetIdentityId,
            String outcome,
            String previousValue,
            String newValue,
            String reason) {
        auditRecorder.record(AuditEntry.builder()
                .actorIdentityId(actorIdentityId)
                .targetIdentityId(targetIdentityId)
                .action(AuditEntry.ACTION_CORRECAO_MATRICULA)
                .outcome(outcome)
                .previousValue(previousValue)
                .newValue(newValue)
                .reason(reason)
                .correlationId(MDC.get("correlationId"))
                .build());
    }

    private static String extractConstraintViolationDetails(Throwable throwable) {
        StringBuilder details = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                details.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }
        return details.toString();
    }
}
