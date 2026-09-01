package br.com.betufu.verification.internal;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizador e validador determinístico de matrícula UFU.
 *
 * <p>A normalização e a validação ocorrem estritamente nesta ordem (D-29): 1. Remoção de espaços
 * nas pontas; 2. Conversão para maiúsculas em {@link Locale#ROOT} (independente do locale da
 * máquina); 3. Remoção de separadores internos (hífen, ponto, barra, espaços); 4. Preservação de
 * letras e dígitos (D-28); 5. Validação contra o padrão aprovado {@code ^[A-Z0-9]{5,20}$}.
 */
public final class MatriculaNormalizer {

  private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[\\s\\-./]+");
  private static final Pattern APPROVED_PATTERN = Pattern.compile("^[A-Z0-9]{5,20}$");

  private MatriculaNormalizer() {}

  /**
   * Normaliza a cadeia de entrada removendo separadores e convertendo para maiúsculas na localidade
   * raiz.
   *
   * @param raw entrada bruta da matrícula
   * @return texto normalizado (vazio se a entrada for nula ou contiver apenas separadores)
   */
  public static String normalize(String raw) {
    if (raw == null) {
      return "";
    }
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) {
      return "";
    }
    String upper = trimmed.toUpperCase(Locale.ROOT);
    return SEPARATOR_PATTERN.matcher(upper).replaceAll("");
  }

  /**
   * Valida se o texto já normalizado atende ao padrão aprovado (5 a 20 caracteres alfanuméricos).
   *
   * @param normalized texto normalizado
   * @return {@code true} se atender ao padrão aprovado; {@code false} caso contrário
   */
  public static boolean isValid(String normalized) {
    if (normalized == null || normalized.isEmpty()) {
      return false;
    }
    return APPROVED_PATTERN.matcher(normalized).matches();
  }

  /**
   * Executa normalização e validação, garantindo a ordem canônica e lançando exceção segura caso a
   * entrada não atenda aos critérios.
   *
   * @param raw entrada bruta da matrícula
   * @return texto normalizado e validado
   * @throws IllegalArgumentException se o texto normalizado for inválido
   */
  public static String normalizeAndValidate(String raw) {
    String normalized = normalize(raw);
    if (!isValid(normalized)) {
      throw new IllegalArgumentException("Matrícula inválida ou fora do padrão aprovado");
    }
    return normalized;
  }
}
