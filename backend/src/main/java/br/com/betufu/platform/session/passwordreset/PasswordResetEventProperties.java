package br.com.betufu.platform.session.passwordreset;

import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de configuração dedicadas ao namespace {@code betufu.session.password-reset}.
 *
 * <p>Mapeia chaves HMAC (current e previous) e limites de payload sem valores padrão de segredos
 * nem fallback em arquivo. Segredos nunca recebem default, log ou valor em arquivo (PRIV-01, D-40).
 *
 * <p>Conforme D-40, aceita estritamente duas versões de chave: a versão corrente ({@code
 * currentVersion}) e a versão anterior ({@code previousVersion}). Qualquer outra chave configurada
 * no mapa é ignorada/rejeitada.
 */
@ConfigurationProperties(prefix = "betufu.session.password-reset")
public class PasswordResetEventProperties {

  public static final int MIN_KEY_BYTES = 32;
  public static final int DEFAULT_MAX_BODY_SIZE_BYTES = 65536; // 64 KB

  private String currentVersion = "v1";
  private String previousVersion;
  private int maxBodySizeBytes = DEFAULT_MAX_BODY_SIZE_BYTES;
  private Map<String, String> keys = new LinkedHashMap<>();

  public String getCurrentVersion() {
    return currentVersion;
  }

  public void setCurrentVersion(String currentVersion) {
    this.currentVersion = currentVersion;
  }

  public String getPreviousVersion() {
    return previousVersion;
  }

  public void setPreviousVersion(String previousVersion) {
    this.previousVersion = previousVersion;
  }

  public int getMaxBodySizeBytes() {
    return maxBodySizeBytes;
  }

  public void setMaxBodySizeBytes(int maxBodySizeBytes) {
    this.maxBodySizeBytes = maxBodySizeBytes;
  }

  public Map<String, String> getKeys() {
    return Collections.unmodifiableMap(keys);
  }

  public void setKeys(Map<String, String> keys) {
    this.keys = keys != null ? new LinkedHashMap<>(keys) : new LinkedHashMap<>();
  }

  /** Verifica se o {@code keyId} informado corresponde à versão corrente ou à anterior (D-40). */
  public boolean isAllowedKeyId(String keyId) {
    if (keyId == null || keyId.isBlank()) {
      return false;
    }
    // Comparacao exata, nunca equalsIgnoreCase: key-id identifica material criptografico, e
    // aceitar "V1" no lugar de "v1" cria um alias que ninguem aprovou. E o mesmo criterio do
    // OidcTechnicalAuthorityMapper, cujo contrato proibe explicitamente ignorar caixa.
    String normalized = keyId.trim();
    if (currentVersion != null
        && !currentVersion.isBlank()
        && normalized.equals(currentVersion.trim())) {
      return true;
    }
    if (previousVersion != null
        && !previousVersion.isBlank()
        && normalized.equals(previousVersion.trim())) {
      return true;
    }
    return false;
  }

  /**
   * Resolve e decodifica a chave secreta HMAC para o {@code keyId} informado.
   *
   * <p>Apenas a chave corrente ({@code currentVersion}) e a anterior ({@code previousVersion}) são
   * aceitas (D-40). A codificação da chave deve ser Base64 obrigatória, sem fallback para bytes
   * UTF-8 crus. Retorna {@code null} se o {@code keyId} não for permitido, for desconhecido, vazio
   * ou se a chave decodificada possuir menos que {@value #MIN_KEY_BYTES} bytes.
   */
  public byte[] resolveKey(String keyId) {
    if (keyId == null || keyId.isBlank() || keys == null || keys.isEmpty()) {
      return null;
    }
    if (!isAllowedKeyId(keyId)) {
      return null;
    }
    String normalized = keyId.trim();
    String rawSecret = keys.get(normalized);
    if (rawSecret == null || rawSecret.isBlank()) {
      rawSecret = keys.get(normalized.toLowerCase(Locale.ROOT));
    }
    if (rawSecret == null || rawSecret.isBlank()) {
      return null;
    }
    try {
      byte[] decoded = Base64.getDecoder().decode(rawSecret.trim());
      if (decoded.length >= MIN_KEY_BYTES) {
        return decoded;
      }
    } catch (IllegalArgumentException ignored) {
      // Chave deve ser Base64 estrito e obrigatório; sem fallback para bytes UTF-8 crus
    }
    return null;
  }

  @Override
  public String toString() {
    // Redação estrita: segredos nunca são incluídos na representação textual (PRIV-01)
    return "PasswordResetEventProperties{"
        + "currentVersion='"
        + currentVersion
        + '\''
        + ", previousVersion='"
        + previousVersion
        + '\''
        + ", configuredKeyIds="
        + keys.keySet()
        + ", maxBodySizeBytes="
        + maxBodySizeBytes
        + '}';
  }
}
