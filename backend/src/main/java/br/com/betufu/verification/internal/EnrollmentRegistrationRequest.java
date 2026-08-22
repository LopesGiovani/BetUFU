package br.com.betufu.verification.internal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;

public record EnrollmentRegistrationRequest(
        @NotBlank(message = "Informe o número da matrícula.")
        @ValidMatriculaFormat
        String matricula) {

    public EnrollmentRegistrationRequest {
        matricula = (matricula == null) ? null : matricula.trim();
    }

    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = MatriculaFormatValidator.class)
    @Documented
    public @interface ValidMatriculaFormat {
        String message() default "Este número não corresponde ao formato de matrícula da UFU. Confira e digite novamente.";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static class MatriculaFormatValidator implements ConstraintValidator<ValidMatriculaFormat, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.isBlank()) {
                return true;
            }
            String normalized = MatriculaNormalizer.normalize(value);
            return MatriculaNormalizer.isValid(normalized);
        }
    }
}
