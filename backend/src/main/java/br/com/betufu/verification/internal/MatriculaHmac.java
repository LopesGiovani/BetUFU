package br.com.betufu.verification.internal;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Serviço de cálculo de HMAC determinístico para matrículas normalizadas.
 *
 * <p>Gera digests de 32 octetos (HmacSHA256) com suporte a múltiplas versões ativas de chave para
 * rotação segura sem quebra de unicidade (D-34, D-35). O cálculo obtém uma nova instância de {@link
 * Mac} por chamada para garantir thread-safety.
 */
public final class MatriculaHmac {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final int MINIMUM_KEY_LENGTH_BYTES = 32;

  private final String currentVersion;
  private final Map<String, byte[]> keys;

  public MatriculaHmac(String currentVersion, Map<String, byte[]> keys) {
    this.currentVersion = Objects.requireNonNull(currentVersion, "currentVersion must not be null");
    Objects.requireNonNull(keys, "keys must not be null");
    if (!keys.containsKey(currentVersion)) {
      throw new IllegalArgumentException(
          "Current key version '" + currentVersion + "' is not present in keys map");
    }
    Map<String, byte[]> defensiveCopy = new HashMap<>();
    for (Map.Entry<String, byte[]> entry : keys.entrySet()) {
      String version = entry.getKey();
      byte[] keyBytes = entry.getValue();
      if (keyBytes == null || keyBytes.length < MINIMUM_KEY_LENGTH_BYTES) {
        throw new IllegalArgumentException(
            "Key for version '"
                + version
                + "' must be at least "
                + MINIMUM_KEY_LENGTH_BYTES
                + " bytes");
      }
      defensiveCopy.put(version, keyBytes.clone());
    }
    this.keys = Map.copyOf(defensiveCopy);
  }

  /**
   * Retorna a versão de chave configurada como corrente.
   *
   * @return identificador da versão corrente
   */
  public String currentVersion() {
    return currentVersion;
  }

  /**
   * Calcula o HMAC-SHA256 da matrícula normalizada usando a versão corrente da chave.
   *
   * @param normalizedMatricula matrícula já normalizada e validada
   * @return digest de 32 octetos
   */
  public byte[] calculateCurrent(String normalizedMatricula) {
    return calculate(normalizedMatricula, currentVersion);
  }

  /**
   * Calcula o HMAC-SHA256 da matrícula normalizada para uma versão específica de chave.
   *
   * @param normalizedMatricula matrícula já normalizada e validada
   * @param version versão da chave a ser utilizada
   * @return digest de 32 octetos
   */
  public byte[] calculate(String normalizedMatricula, String version) {
    if (!MatriculaNormalizer.isValid(normalizedMatricula)) {
      throw new IllegalArgumentException(
          "Normalized matricula must be valid under approved pattern");
    }
    if (version == null || !keys.containsKey(version)) {
      throw new IllegalArgumentException("Unknown key version: " + version);
    }

    byte[] keyBytes = keys.get(version);
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(keyBytes, HMAC_ALGORITHM));
      return mac.doFinal(normalizedMatricula.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("Failed to calculate HMAC for version: " + version, e);
    }
  }

  /**
   * Compara dois digests em tempo constante usando {@link MessageDigest#isEqual(byte[], byte[])}.
   *
   * @param a primeiro digest
   * @param b segundo digest
   * @return {@code true} se ambos os digests não forem nulos e forem idênticos byte a byte; {@code
   *     false} caso contrário
   */
  public static boolean isEqual(byte[] a, byte[] b) {
    if (a == null || b == null) {
      return false;
    }
    return MessageDigest.isEqual(a, b);
  }
}
