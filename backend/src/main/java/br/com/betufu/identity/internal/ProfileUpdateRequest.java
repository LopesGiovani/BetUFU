package br.com.betufu.identity.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ProfileUpdateRequest(
        @NotBlank(message = "Informe um apelido para continuar.")
        @Pattern(
                regexp = "^[A-Za-z0-9_-]{3,20}$",
                message = "Use de 3 a 20 caracteres, apenas letras sem acento, números, hífen (-) ou sublinhado (_).")
        String nickname) {

    public ProfileUpdateRequest {
        nickname = nickname == null ? null : nickname.trim();
    }
}
