package br.com.betufu.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.verification.internal.MatriculaNormalizer;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MatriculaNormalizerTest {

  @Test
  @DisplayName(
      "Normaliza entrada com espaços nas pontas, hífen interno e letras minúsculas para maiúsculas limpas")
  void normalizesInputWithSurroundingSpacesHyphensAndLowerCaseToCleanUppercase() {
    String raw = "  12345-abc  ";
    String normalized = MatriculaNormalizer.normalize(raw);

    assertThat(normalized).isEqualTo("12345ABC");
    assertThat(MatriculaNormalizer.isValid(normalized)).isTrue();
  }

  @Test
  @DisplayName(
      "A conversão para maiúscula é independente de localidade da máquina, inclusive turco")
  void uppercaseConversionIsLocaleIndependentIncludingTurkish() {
    Locale originalDefault = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      String normalizedTurkish = MatriculaNormalizer.normalize("12345-i-abc");
      assertThat(normalizedTurkish).isEqualTo("12345IABC");
      assertThat(normalizedTurkish).doesNotContain("\u0130");
      assertThat(MatriculaNormalizer.isValid(normalizedTurkish)).isTrue();

      Locale.setDefault(Locale.forLanguageTag("az-AZ"));
      String normalizedAzeri = MatriculaNormalizer.normalize("12345-i-xyz");
      assertThat(normalizedAzeri).isEqualTo("12345IXYZ");
      assertThat(normalizedAzeri).doesNotContain("\u0130");
      assertThat(MatriculaNormalizer.isValid(normalizedAzeri)).isTrue();
    } finally {
      Locale.setDefault(originalDefault);
    }
  }

  @Test
  @DisplayName("Preserva letras e dígitos sem descartar caracteres não numéricos (D-28)")
  void preservesLettersAndDigitsWithoutDiscardingNonDigits() {
    String raw = "2024-1-BSI-999";
    String normalized = MatriculaNormalizer.normalize(raw);

    assertThat(normalized).isEqualTo("20241BSI999");
    assertThat(MatriculaNormalizer.isValid(normalized)).isTrue();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t\n", "---", "...", "///", "-.-/ /-"})
  @DisplayName(
      "Recusa entrada nula, vazia, apenas espaços ou apenas separadores antes de qualquer HMAC")
  void rejectsNullEmptyOnlySpacesOrOnlySeparators(String input) {
    String normalized = MatriculaNormalizer.normalize(input);
    assertThat(MatriculaNormalizer.isValid(normalized)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"ABCD", "1234", "123456789012345678901", "12345_ABC", "12345@ABC"})
  @DisplayName("Recusa texto normalizado que não casa com o padrão aprovado ^[A-Z0-9]{5,20}$")
  void rejectsNormalizedTextOutsideApprovedPattern(String invalidNormalized) {
    assertThat(MatriculaNormalizer.isValid(invalidNormalized)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"12345", "ABCDE", "12345ABCDE", "12345678901234567890"})
  @DisplayName("Aceita texto normalizado dentro dos limites de 5 a 20 caracteres alfanuméricos")
  void acceptsNormalizedTextWithinApprovedPattern(String validNormalized) {
    assertThat(MatriculaNormalizer.isValid(validNormalized)).isTrue();
  }

  @Test
  @DisplayName("Descarta separadores internos variados como hífen, ponto, barra e espaços")
  void discardsVariousInternalSeparators() {
    String raw = " 123 45.67/89-A ";
    String normalized = MatriculaNormalizer.normalize(raw);

    assertThat(normalized).isEqualTo("123456789A");
    assertThat(MatriculaNormalizer.isValid(normalized)).isTrue();
  }

  @Test
  @DisplayName(
      "normalizeAndValidate lança exceção para entrada inválida e retorna normalizado para válida")
  void normalizeAndValidateEnforcesOrderingAndValidation() {
    assertThat(MatriculaNormalizer.normalizeAndValidate(" 12345-abc ")).isEqualTo("12345ABC");

    assertThatThrownBy(() -> MatriculaNormalizer.normalizeAndValidate("abc"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MatriculaNormalizer.normalizeAndValidate("   "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
