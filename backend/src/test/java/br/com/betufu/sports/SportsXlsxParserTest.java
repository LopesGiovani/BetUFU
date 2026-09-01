package br.com.betufu.sports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.sports.internal.importtabular.ImportedRosterRow;
import br.com.betufu.sports.internal.importtabular.SportsXlsxParser;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SportsXlsxParserTest {

  private SportsXlsxParser parser;

  @BeforeEach
  void setUp() {
    parser = new SportsXlsxParser();
  }

  @Test
  @DisplayName("Deve fazer parse de XLSX válido com sucesso")
  void parsesValidXlsxSuccessfully() throws Exception {
    byte[] bytes =
        createWorkbookBytes(
            sheet -> {
              Row header = sheet.createRow(0);
              for (int i = 0; i < SportsXlsxParser.EXPECTED_HEADERS.size(); i++) {
                header.createCell(i).setCellValue(SportsXlsxParser.EXPECTED_HEADERS.get(i));
              }
              Row row1 = sheet.createRow(1);
              row1.createCell(0).setCellValue("atletica-engenharia");
              row1.createCell(1).setCellValue("futsal");
              row1.createCell(2).setCellValue("masculino");
              row1.createCell(3).setCellValue("Carlos Silva");
              row1.createCell(4).setCellValue("11821BCC001");
              row1.createCell(5).setCellValue(10);
              row1.createCell(6).setCellValue("Ala");
            });

    List<ImportedRosterRow> rows = parser.parse(bytes);
    assertThat(rows).hasSize(1);
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
  @DisplayName("Deve ler matrícula numérica preservando dígitos sem casas decimais")
  void readsNumericMatriculaPreservingDigits() throws Exception {
    byte[] bytes =
        createWorkbookBytes(
            sheet -> {
              Row header = sheet.createRow(0);
              for (int i = 0; i < SportsXlsxParser.EXPECTED_HEADERS.size(); i++) {
                header.createCell(i).setCellValue(SportsXlsxParser.EXPECTED_HEADERS.get(i));
              }
              Row row1 = sheet.createRow(1);
              row1.createCell(0).setCellValue("atletica-engenharia");
              row1.createCell(1).setCellValue("futsal");
              row1.createCell(2).setCellValue("masculino");
              row1.createCell(3).setCellValue("Carlos Silva");
              row1.createCell(4).setCellValue(123456789.0);
              row1.createCell(5).setCellValue(7);
              row1.createCell(6).setCellValue("Pivô");
            });

    List<ImportedRosterRow> rows = parser.parse(bytes);
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).matriculaCode()).isEqualTo("123456789");
  }

  @Test
  @DisplayName("Deve rejeitar fórmulas no arquivo XLSX")
  void rejectsFormulasInXlsx() throws Exception {
    byte[] bytes =
        createWorkbookBytes(
            sheet -> {
              Row header = sheet.createRow(0);
              for (int i = 0; i < SportsXlsxParser.EXPECTED_HEADERS.size(); i++) {
                header.createCell(i).setCellValue(SportsXlsxParser.EXPECTED_HEADERS.get(i));
              }
              Row row1 = sheet.createRow(1);
              row1.createCell(0).setCellValue("atletica-engenharia");
              row1.createCell(1).setCellValue("futsal");
              row1.createCell(2).setCellValue("masculino");
              row1.createCell(3).setCellValue("Carlos Silva");
              row1.createCell(4).setCellValue("11821BCC001");
              Cell formulaCell = row1.createCell(5);
              formulaCell.setCellFormula("SUM(1, 2)");
              row1.createCell(6).setCellValue("Ala");
            });

    assertThatThrownBy(() -> parser.parse(bytes))
        .isInstanceOf(ApiProblemException.class)
        .hasMessage("BETUFU-400-VALIDATION");
  }

  @Test
  @DisplayName("Deve rejeitar células mescladas no template XLSX")
  void rejectsMergedCellsInXlsx() throws Exception {
    byte[] bytes =
        createWorkbookBytes(
            sheet -> {
              Row header = sheet.createRow(0);
              for (int i = 0; i < SportsXlsxParser.EXPECTED_HEADERS.size(); i++) {
                header.createCell(i).setCellValue(SportsXlsxParser.EXPECTED_HEADERS.get(i));
              }
              sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));
            });

    assertThatThrownBy(() -> parser.parse(bytes))
        .isInstanceOf(ApiProblemException.class)
        .hasMessage("BETUFU-400-VALIDATION");
  }

  @Test
  @DisplayName("Deve rejeitar cabeçalhos divergentes no XLSX")
  void rejectsWrongHeaderInXlsx() throws Exception {
    byte[] bytes =
        createWorkbookBytes(
            sheet -> {
              Row header = sheet.createRow(0);
              header.createCell(0).setCellValue("coluna_invalida");
              header.createCell(1).setCellValue("modalidade");
            });

    assertThatThrownBy(() -> parser.parse(bytes))
        .isInstanceOf(ApiProblemException.class)
        .hasMessage("BETUFU-400-VALIDATION");
  }

  @Test
  @DisplayName("Deve rejeitar arquivo XLSX malformado / corrompido")
  void rejectsMalformedXlsx() {
    byte[] corrupted = "corrupted-binary-content".getBytes();
    assertThatThrownBy(() -> parser.parse(corrupted))
        .isInstanceOf(ApiProblemException.class)
        .hasMessage("BETUFU-400-VALIDATION");
  }

  @Test
  @DisplayName("Deve rejeitar XLSX que excede limite de linhas")
  void rejectsOverLimitsXlsx() throws Exception {
    byte[] bytes =
        createWorkbookBytes(
            sheet -> {
              Row header = sheet.createRow(0);
              for (int i = 0; i < SportsXlsxParser.EXPECTED_HEADERS.size(); i++) {
                header.createCell(i).setCellValue(SportsXlsxParser.EXPECTED_HEADERS.get(i));
              }
              for (int r = 1; r <= 501; r++) {
                Row row = sheet.createRow(r);
                row.createCell(0).setCellValue("org");
                row.createCell(1).setCellValue("sport");
                row.createCell(2).setCellValue("cat");
                row.createCell(3).setCellValue("Atleta " + r);
                row.createCell(4).setCellValue("11821BCC001");
                row.createCell(5).setCellValue(10);
                row.createCell(6).setCellValue("Ala");
              }
            });

    assertThatThrownBy(() -> parser.parse(bytes))
        .isInstanceOf(ApiProblemException.class)
        .hasMessage("BETUFU-400-VALIDATION");
  }

  @FunctionalInterface
  private interface SheetCustomizer {
    void customize(Sheet sheet);
  }

  private byte[] createWorkbookBytes(SheetCustomizer customizer) throws Exception {
    try (Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("Template");
      customizer.customize(sheet);
      workbook.write(out);
      return out.toByteArray();
    }
  }
}
