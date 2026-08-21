package br.com.betufu.verification.internal;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração e inicialização fail-closed do serviço de HMAC da matrícula.
 *
 * <p>Valida rigorosamente na inicialização do contexto (bootstrap) que:
 * <ul>
 *   <li>A propriedade {@code current-version} está presente e não vazia;</li>
 *   <li>O mapa de chaves contém uma entrada para a versão corrente;</li>
 *   <li>Todas as chaves configuradas são Base64 válido;</li>
 *   <li>Todas as chaves decodificadas possuem ao menos 32 bytes (256 bits).</li>
 * </ul>
 * Nenhuma mensagem de erro inclui material de chave (D-33, PRIV-01).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MatriculaHmacKeyProperties.class)
public class MatriculaHmacConfiguration {

    private static final int MINIMUM_KEY_LENGTH_BYTES = 32;

    @Bean
    public MatriculaHmac matriculaHmac(MatriculaHmacKeyProperties properties) {
        String currentVersion = properties.getCurrentVersion();
        if (currentVersion == null || currentVersion.isBlank()) {
            throw new IllegalStateException(
                    "betufu.verification.matricula-hmac.current-version property is required and must not be blank");
        }

        Map<String, String> rawKeys = properties.getKeys();
        if (rawKeys == null || rawKeys.isEmpty()) {
            throw new IllegalStateException(
                    "betufu.verification.matricula-hmac.keys must contain at least the key for current-version '"
                            + currentVersion + "'");
        }

        if (!rawKeys.containsKey(currentVersion) || rawKeys.get(currentVersion) == null
                || rawKeys.get(currentVersion).isBlank()) {
            throw new IllegalStateException(
                    "Matricula HMAC key for current-version '" + currentVersion + "' is missing in betufu.verification.matricula-hmac.keys");
        }

        Map<String, byte[]> decodedKeys = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : rawKeys.entrySet()) {
            String version = entry.getKey();
            String base64Value = entry.getValue();

            if (base64Value == null || base64Value.isBlank()) {
                throw new IllegalStateException(
                        "Matricula HMAC key for version '" + version + "' is missing or blank");
            }

            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(base64Value.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "Matricula HMAC key for version '" + version + "' is not valid Base64", e);
            }

            if (decoded.length < MINIMUM_KEY_LENGTH_BYTES) {
                throw new IllegalStateException(
                        "Matricula HMAC key for version '" + version + "' must be at least "
                                + MINIMUM_KEY_LENGTH_BYTES + " bytes, but found " + decoded.length + " bytes");
            }

            decodedKeys.put(version, decoded);
        }

        return new MatriculaHmac(currentVersion, decodedKeys);
    }
}
