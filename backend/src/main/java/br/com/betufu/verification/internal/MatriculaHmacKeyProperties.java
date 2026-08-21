package br.com.betufu.verification.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de configuração das chaves de HMAC da matrícula.
 *
 * <p>Mapeia o prefixo {@code betufu.verification.matricula-hmac} a partir do ambiente
 * sem valores padrão/fallback, garantindo fail-closed no bootstrap (D-33).
 */
@ConfigurationProperties(prefix = "betufu.verification.matricula-hmac")
public class MatriculaHmacKeyProperties {

    private String currentVersion;
    private Map<String, String> keys = new LinkedHashMap<>();

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public Map<String, String> getKeys() {
        return keys;
    }

    public void setKeys(Map<String, String> keys) {
        this.keys = keys != null ? keys : new LinkedHashMap<>();
    }
}
