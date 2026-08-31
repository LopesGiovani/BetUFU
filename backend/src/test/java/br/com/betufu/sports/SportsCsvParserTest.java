package br.com.betufu.sports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.sports.internal.importtabular.ImportedRosterRow;
import br.com.betufu.sports.internal.importtabular.SportsCsvParser;
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
    @DisplayName("Deve fazer parse de CSV válido com sucesso")
    void parsesValidCsvSuccessfully() throws Exception {
        byte[] bytes = readResource("/sports/csv/valid-roster.csv");
        List<ImportedRosterRow> rows = parser.parse(bytes);

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
    }

    @Test
    @DisplayName("Deve tratar BOM UTF-8 transparentemente")
    void handlesUtf8BomCorrectly() throws Exception {
        byte[] bytes = readResource("/sports/csv/utf8-bom-roster.csv");
        List<ImportedRosterRow> rows = parser.parse(bytes);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).organizationSlug()).isEqualTo("atletica-engenharia");
    }

    @Test
    @DisplayName("Deve processar campos com aspas e quebras de linha")
    void handlesQuotedFieldsAndNewlines() throws Exception {
        byte[] bytes = readResource("/sports/csv/quoted-fields-roster.csv");
        List<ImportedRosterRow> rows = parser.parse(bytes);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).athleteName()).isEqualTo("Silva, Carlos");
        assertThat(rows.get(0).positionName()).isEqualTo("Ala \"Esquerda\"");
    }

    @Test
    @DisplayName("Deve rejeitar arquivo com encoding UTF-8 inválido")
    void rejectsInvalidEncoding() {
        byte[] invalidUtf8 = new byte[] {(byte) 0xC3, (byte) 0x28};
        assertThatThrownBy(() -> parser.parse(invalidUtf8))
                .isInstanceOf(ApiProblemException.class)
                .hasMessage("BETUFU-400-VALIDATION");
    }

    @Test
    @DisplayName("Deve rejeitar CSV vazio")
    void rejectsEmptyCsv() {
        assertThatThrownBy(() -> parser.parse(new byte[0]))
                .isInstanceOf(ApiProblemException.class)
                .hasMessage("BETUFU-400-VALIDATION");
    }

    @Test
    @DisplayName("Deve rejeitar cabeçalhos divergentes do template")
    void rejectsWrongHeaders() {
        String csv = "coluna_errada,modalidade,categoria,nome,mat,num,pos\n1,2,3,4,5,6,7";
        assertThatThrownBy(() -> parser.parse(csv.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ApiProblemException.class)
                .hasMessage("BETUFU-400-VALIDATION");
    }

    @Test
    @DisplayName("Deve rejeitar arquivo que excede limite de linhas")
    void rejectsOverLimits() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", SportsCsvParser.EXPECTED_HEADERS)).append("\n");
        for (int i = 1; i <= 501; i++) {
            sb.append("org,sport,cat,Atleta ").append(i).append(",11821BCC001,10,Ala\n");
        }

        assertThatThrownBy(() -> parser.parse(sb.toString().getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ApiProblemException.class)
                .hasMessage("BETUFU-400-VALIDATION");
    }

    @Test
    @DisplayName("Deve neutralizar formula injection em campos de texto")
    void neutralizesFormulaInjection() throws Exception {
        byte[] bytes = readResource("/sports/csv/formula-injection.csv");
        List<ImportedRosterRow> rows = parser.parse(bytes);

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
}
