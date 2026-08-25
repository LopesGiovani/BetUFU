package br.com.betufu.verification.internal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import br.com.betufu.platform.api.FieldViolation;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Entrada da correção administrativa de matrícula (D-32).
 *
 * <p>A pessoa alvo é endereçada pelo <strong>identificador público</strong>, nunca por apelido
 * (que não é único, D-42) e nunca por e-mail (que é credencial de acesso, D-41).
 *
 * <p>A nova matrícula viaja <strong>apenas no corpo</strong>. Caminho, parâmetro de consulta,
 * fragmento e cabeçalho estão proibidos por {@code PRIV-01}, e o detector automático dessa
 * proibição é o {@code PrivacyArchitectureTest}.
 *
 * <p>O motivo é obrigatório porque ele se torna permanente na trilha append-only: exigi-lo antes
 * de qualquer escrita é o que impede uma correção sem justificativa registrável.
 */
public record EnrollmentCorrectionRequest(
        @NotNull(message = "Este identificador não tem o formato esperado. Copie-o do perfil da pessoa.")
        UUID targetIdentityId,

        @NotBlank(message = "Informe o número da matrícula.")
        @EnrollmentRegistrationRequest.ValidMatriculaFormat
        String matricula,

        @ValidCorrectionReason
        String reason) {

    /** Mensagem literal do {@code 02-UI-SPEC.md}, seção *Erros de campo*. */
    public static final String TARGET_REQUIRED_MESSAGE =
            "Este identificador não tem o formato esperado. Copie-o do perfil da pessoa.";

    /** Mensagem literal do {@code 02-UI-SPEC.md} para *Motivo da correção vazio*. */
    public static final String REASON_REQUIRED_MESSAGE = "Informe o motivo da correção.";

    /** Mensagem literal do {@code 02-UI-SPEC.md} para *Motivo curto demais*. */
    public static final String REASON_TOO_SHORT_MESSAGE =
            "Descreva o motivo com mais detalhe. Ele precisa fazer sentido para quem ler a auditoria depois.";

    /**
     * Recusa por excesso de tamanho. Não consta do contrato de cópia aprovado: a alternativa seria
     * truncar o motivo — proibido, porque a auditoria depende de lê-lo inteiro — ou deixar a
     * gravação estourar {@code ck_audit_event_reason_octets} e virar erro interno.
     */
    public static final String REASON_TOO_LONG_MESSAGE =
            "O motivo é longo demais para ser registrado na auditoria. Reduza o texto e tente novamente.";

    /** Nome do campo usado nas violações, alinhado ao componente do record. */
    public static final String REASON_FIELD = "reason";

    /** Tamanho mínimo, em caracteres do texto já sem espaços nas pontas. */
    public static final int MINIMUM_REASON_LENGTH = 10;

    /**
     * Teto em octetos UTF-8, idêntico ao {@code ck_audit_event_reason_octets} da migration V4.
     * Medir em octetos, e não em caracteres, é o que casa com a restrição real do PostgreSQL.
     */
    public static final int MAXIMUM_REASON_OCTETS = 1024;

    public EnrollmentCorrectionRequest {
        matricula = (matricula == null) ? null : matricula.trim();
        reason = (reason == null) ? null : reason.trim();
    }

    /**
     * Avalia o motivo e devolve a violação correspondente, se houver.
     *
     * <p>Fonte única para as duas mensagens literais: o validador de Bean Validation deste record e
     * o {@link EnrollmentCorrectionService} consultam exatamente esta regra, de modo que a recusa
     * do formulário e a recusa do serviço nunca divergem.
     *
     * @param reason motivo informado, cru ou já aparado
     * @return violação com a mensagem literal aplicável, ou vazio quando o motivo é aceitável
     */
    public static Optional<FieldViolation> validateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return Optional.of(new FieldViolation(REASON_FIELD, REASON_REQUIRED_MESSAGE));
        }
        String trimmed = reason.trim();
        if (trimmed.length() < MINIMUM_REASON_LENGTH) {
            return Optional.of(new FieldViolation(REASON_FIELD, REASON_TOO_SHORT_MESSAGE));
        }
        if (trimmed.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_REASON_OCTETS) {
            return Optional.of(new FieldViolation(REASON_FIELD, REASON_TOO_LONG_MESSAGE));
        }
        return Optional.empty();
    }

    /**
     * Os alvos excluem {@code METHOD} de propósito, no mesmo padrão de
     * {@code EnrollmentRegistrationRequest.ValidMatriculaFormat}: uma anotação em componente de
     * record que também alcança o acessador é declarada duas vezes pelo Hibernate Validator e
     * produz duas violações para o mesmo campo, enquanto o {@code 02-UI-SPEC.md} exige exatamente
     * uma mensagem por campo inválido.
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = CorrectionReasonValidator.class)
    @Documented
    public @interface ValidCorrectionReason {
        String message() default "Informe o motivo da correção.";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    /**
     * Emite a mensagem literal correta para cada caso, em vez de encadear {@code @NotBlank} com
     * {@code @Size}: encadear produziria duas violações simultâneas para o motivo vazio, e o
     * {@code 02-UI-SPEC.md} exige exatamente uma mensagem por campo inválido.
     */
    public static class CorrectionReasonValidator
            implements ConstraintValidator<ValidCorrectionReason, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            Optional<FieldViolation> violation = validateReason(value);
            if (violation.isEmpty()) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(violation.get().message())
                    .addConstraintViolation();
            return false;
        }
    }
}
