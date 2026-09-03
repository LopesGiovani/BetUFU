package br.com.betufu.platform.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DiagnosticStatusJsonTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  @DisplayName("mantem exatamente os tres estados publicos do diagnostico")
  void exposesOnlyCanonicalStatuses() {
    assertThat(Arrays.asList(DiagnosticStatus.values()))
        .containsExactly(DiagnosticStatus.UP, DiagnosticStatus.DEGRADED, DiagnosticStatus.DOWN);
  }

  @Test
  @DisplayName("serializa e desserializa os estados canonicos com strings JSON exatas")
  void roundTripsCanonicalJsonValues() throws Exception {
    List<DiagnosticStatus> statuses =
        List.of(DiagnosticStatus.UP, DiagnosticStatus.DEGRADED, DiagnosticStatus.DOWN);
    List<String> jsonValues = List.of("\"UP\"", "\"DEGRADED\"", "\"DOWN\"");

    for (int i = 0; i < statuses.size(); i++) {
      DiagnosticStatus status = statuses.get(i);
      String json = JSON.writeValueAsString(status);

      assertThat(json).isEqualTo(jsonValues.get(i));
      assertThat(JSON.readValue(json, DiagnosticStatus.class)).isEqualTo(status);
    }
  }

  @Test
  @DisplayName("rejeita os nomes legados sem alias ou fallback")
  void rejectsLegacyJsonValues() {
    assertThatThrownBy(() -> JSON.readValue("\"AVAILABLE\"", DiagnosticStatus.class))
        .isInstanceOf(Exception.class);
    assertThatThrownBy(() -> JSON.readValue("\"UNAVAILABLE\"", DiagnosticStatus.class))
        .isInstanceOf(Exception.class);
  }
}
