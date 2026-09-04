package br.com.betufu.sports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.sports.internal.importtabular.ImportedRosterRow;
import br.com.betufu.sports.internal.importtabular.SportsCsvParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SportsCsvParserTest {

  private SportsCsvParser parser;

  @BeforeEach
  void setUp() {
    parser = new SportsCsvParser();
  }

  @Test
  @DisplayName("Parses a valid CSV and its affiliation period")
  void parsesValidCsvSuccessfully() throws Exception {
    List<ImportedRosterRow> rows = parser.parse(readResource("/sports/csv/valid-roster.csv"));

    assertThat(rows).hasSize(3);
    ImportedRosterRow first = rows.get(0);
    assertThat(first.lineNumber()).isEqualTo(2);
    assertThat(first.organizationSlug()).isEqualTo("atletica-engenharia");
    assertThat(first.sportSlug()).isEqualTo("futsal");
    assertThat(first.categorySlug()).isEqualTo("masculino");
    assertThat(first.athleteName()).isEqualTo("Carlos Silva");
    assertThat(first.matriculaCode()).isEqualTo("11821BCC001");
    assertThat(first.jerseyNumber()).isEqualTo(10);
    assertThat(first.positionName()).isEqualTo("Ala");
    assertThat(first.affiliationStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(first.affiliationEndDate()).isEqualTo(LocalDate.of(2026, 9, 30));
    assertThat(rows.get(2).affiliationStartDate()).isNull();
    assertThat(rows.get(2).affiliationEndDate()).isNull();
  }

  @Test
  @DisplayName("Handles a UTF-8 BOM without changing the contract")
  void handlesUtf8BomCorrectly() throws Exception {
    List<ImportedRosterRow> rows =
        parser.parse(withAffiliationPeriod(readResource("/sports/csv/utf8-bom-roster.csv")));

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).organizationSlug()).isEqualTo("atletica-engenharia");
    assertThat(rows.get(0).affiliationStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
  }

  @Test
  @DisplayName("Preserves quoted fields and embedded newlines")
  void handlesQuotedFieldsAndNewlines() {
    String csv =
        String.join(
            "\n",
            String.join(",", SportsCsvParser.EXPECTED_HEADERS),
            "\"atletica-engenharia\",\"futsal\",\"masculino\",\"Silva, Carlos\",\"11821BCC001\",10,\"Ala \"\"Esquerda\"\"\",2026-08-01,2026-09-30",
            "\"atletica-medicina\",\"futsal\",\"masculino\",\"Oliveira,\nMarcos\",\"11821MED001\",1,\"Goleiro\",,");

    List<ImportedRosterRow> rows = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).athleteName()).isEqualTo("Silva, Carlos");
    assertThat(rows.get(0).positionName()).isEqualTo("Ala \"Esquerda\"");
    assertThat(rows.get(1).athleteName()).isEqualTo("Oliveira,\nMarcos");
  }

  @Test
  @DisplayName("Rejects invalid UTF-8")
  void rejectsInvalidEncoding() {
    assertThatThrownBy(() -> parser.parse(new byte[] {(byte) 0xC3, (byte) 0x28}))
        .isInstanceOf(ApiProblemException.class)
        .hasMessage("BETUFU-400-VALIDATION");
  }

  @Test
  @DisplayName("Rejects an empty CSV")
  void rejectsEmptyCsv() {
    assertThatThrownBy(() -> parser.parse(new byte[0]))
        .isInstanceOf(ApiProblemException.class)
        .hasMessage("BETUFU-400-VALIDATION");
  }

  @Test
  @DisplayName("Rejects headers outside the fixed template")
  void rejectsWrongHeaders() {
    String csv =
        String.join(
                ",",
                "coluna_errada",
                "modalidade",
                "categoria",
                "nome",
                "mat",
                "num",
                "pos",
                "inicio",
                "fim")
            + "\n1,2,3,4,5,6,7,2026-08-01,2026-09-30";
    assertThatThrownBy(() -> parser.parse(csv.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(ApiProblemException.class)
        .hasMessage("BETUFU-400-VALIDATION");
  }

  @Test
  @DisplayName("Rejects a non-integer jersey number")
  void rejectsInvalidJerseyType() {
    String csv =
        String.join(",", SportsCsvParser.EXPECTED_HEADERS)
            + "\norg,sport,cat,Athlete,11821BCC001,not-a-number,Ala,2026-08-01,2026-09-30";
    assertThatThrownBy(() -> parser.parse(csv.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(ApiProblemException.class)
        .hasMessage("BETUFU-400-VALIDATION");
  }

  @Test
  @DisplayName("Rejects a date outside ISO yyyy-MM-dd")
  void rejectsInvalidDate() {
    String csv =
        String.join(",", SportsCsvParser.EXPECTED_HEADERS)
            + "\norg,sport,cat,Athlete,11821BCC001,10,Ala,01/08/2026,2026-09-30";
    assertThatThrownBy(() -> parser.parse(csv.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(ApiProblemException.class)
        .hasMessage("BETUFU-400-VALIDATION");
  }

  @Test
  @DisplayName("Rejects a CSV that exceeds the row limit")
  void rejectsOverLimits() {
    StringBuilder sb =
        new StringBuilder(String.join(",", SportsCsvParser.EXPECTED_HEADERS)).append('\n');
    for (int i = 1; i <= 501; i++) {
      sb.append("org,sport,cat,Atleta ")
          .append(i)
          .append(",11821BCC001,10,Ala,2026-08-01,2026-09-30\n");
    }

    assertThatThrownBy(() -> parser.parse(sb.toString().getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(ApiProblemException.class)
        .hasMessage("BETUFU-400-VALIDATION");
  }

  @Test
  @DisplayName("Neutralizes formula injection in CSV text fields")
  void neutralizesFormulaInjection() throws Exception {
    List<ImportedRosterRow> rows = parser.parse(readResource("/sports/csv/formula-injection.csv"));

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).athleteName()).startsWith("'=");
    assertThat(rows.get(0).positionName()).startsWith("'+");
    assertThat(rows.get(1).athleteName()).startsWith("'@");
  }

  private byte[] readResource(String path) throws Exception {
    try (InputStream is = getClass().getResourceAsStream(path)) {
      if (is == null) {
        throw new IllegalArgumentException("Resource not found: " + path);
      }
      return is.readAllBytes();
    }
  }

  private byte[] withAffiliationPeriod(byte[] legacyBytes) {
    String[] lines = new String(legacyBytes, StandardCharsets.UTF_8).split("\\R", -1);
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) {
        result.append('\n');
      }
      if (i == 0) {
        result.append(lines[i]).append(",inicio_vinculo,fim_vinculo");
      } else if (!lines[i].isBlank()) {
        result.append(lines[i]).append(",2026-08-01,2026-09-30");
      } else {
        result.append(lines[i]);
      }
    }
    return result.toString().getBytes(StandardCharsets.UTF_8);
  }
}
