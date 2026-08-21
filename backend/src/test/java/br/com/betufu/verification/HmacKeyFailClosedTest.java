package br.com.betufu.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import br.com.betufu.verification.internal.MatriculaHmac;
import br.com.betufu.verification.internal.MatriculaHmacConfiguration;

class HmacKeyFailClosedTest {

    private static final String VALID_KEY_32_BYTES_BASE64 =
            Base64.getEncoder().encodeToString("test-only-fail-closed-key-000001".getBytes(StandardCharsets.UTF_8));
    private static final String VALID_KEY_V2_32_BYTES_BASE64 =
            Base64.getEncoder().encodeToString("test-only-fail-closed-key-000002".getBytes(StandardCharsets.UTF_8));

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MatriculaHmacConfiguration.class);

    @Test
    @DisplayName("Contexto sobe com sucesso quando versão corrente e chave válida de 32 bytes estão presentes")
    void startsSuccessfullyWithValidCurrentVersionAndKey() {
        contextRunner
                .withPropertyValues(
                        "betufu.verification.matricula-hmac.current-version=v1",
                        "betufu.verification.matricula-hmac.keys.v1=" + VALID_KEY_32_BYTES_BASE64)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MatriculaHmac.class);
                    MatriculaHmac hmac = context.getBean(MatriculaHmac.class);
                    assertThat(hmac.currentVersion()).isEqualTo("v1");
                    assertThat(hmac.calculateCurrent("12345ABC")).hasSize(32);
                });
    }

    @Test
    @DisplayName("Contexto falha no bootstrap quando a variável da versão corrente está ausente")
    void failsBootstrapWhenCurrentVersionIsMissing() {
        contextRunner
                .withPropertyValues(
                        "betufu.verification.matricula-hmac.keys.v1=" + VALID_KEY_32_BYTES_BASE64)
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable failure = context.getStartupFailure();
                    assertThat(failure).isNotNull();
                    assertThat(failure.getMessage())
                            .doesNotContain(VALID_KEY_32_BYTES_BASE64)
                            .containsIgnoringCase("current-version");
                });
    }

    @Test
    @DisplayName("Contexto falha no bootstrap quando a variável da chave daquela versão está ausente")
    void failsBootstrapWhenCurrentVersionKeyIsMissing() {
        contextRunner
                .withPropertyValues(
                        "betufu.verification.matricula-hmac.current-version=v1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable failure = context.getStartupFailure();
                    assertThat(failure).isNotNull();
                    assertThat(failure.getMessage())
                            .containsIgnoringCase("v1");
                });
    }

    @Test
    @DisplayName("Contexto falha no bootstrap quando o valor da chave não é Base64 válido")
    void failsBootstrapWhenKeyIsNotValidBase64() {
        contextRunner
                .withPropertyValues(
                        "betufu.verification.matricula-hmac.current-version=v1",
                        "betufu.verification.matricula-hmac.keys.v1=not-valid-base64-content!@#$")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable failure = context.getStartupFailure();
                    assertThat(failure).isNotNull();
                    assertThat(failure.getMessage())
                            .doesNotContain("not-valid-base64-content");
                });
    }

    @Test
    @DisplayName("Contexto falha no bootstrap quando o valor decodificado tem menos de 32 bytes")
    void failsBootstrapWhenDecodedKeyIsLessThan32Bytes() {
        String shortKeyBase64 = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));

        contextRunner
                .withPropertyValues(
                        "betufu.verification.matricula-hmac.current-version=v1",
                        "betufu.verification.matricula-hmac.keys.v1=" + shortKeyBase64)
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable failure = context.getStartupFailure();
                    assertThat(failure).isNotNull();
                    assertThat(failure.getMessage())
                            .doesNotContain(shortKeyBase64)
                            .containsIgnoringCase("32");
                });
    }

    @Test
    @DisplayName("Contexto sobe com suporte a múltiplas versões ativas de chave")
    void startsSuccessfullyWithMultipleActiveKeyVersions() {
        contextRunner
                .withPropertyValues(
                        "betufu.verification.matricula-hmac.current-version=v2",
                        "betufu.verification.matricula-hmac.keys.v1=" + VALID_KEY_32_BYTES_BASE64,
                        "betufu.verification.matricula-hmac.keys.v2=" + VALID_KEY_V2_32_BYTES_BASE64)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MatriculaHmac hmac = context.getBean(MatriculaHmac.class);
                    assertThat(hmac.currentVersion()).isEqualTo("v2");
                    assertThat(hmac.calculate("12345ABC", "v1")).hasSize(32);
                    assertThat(hmac.calculate("12345ABC", "v2")).hasSize(32);
                    assertThat(hmac.calculate("12345ABC", "v1"))
                            .isNotEqualTo(hmac.calculate("12345ABC", "v2"));
                });
    }
}
