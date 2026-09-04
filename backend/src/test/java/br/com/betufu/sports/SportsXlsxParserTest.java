package br.com.betufu.sports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.sports.internal.importtabular.ImportedRosterRow;
import br.com.betufu.sports.internal.importtabular.SportsXlsxParser;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
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
  @DisplayName("Parses a valid XLSX and its affiliation period")
  void parsesValidXlsxSuccessfully() throws Exception {
    byte[] bytes =
        createWorkbookBytes(
            sheet -> {
              createHeader(sheet);
              createValidRow(sheet, 1, "11821BCC001", 10, "2026-08-01", "2026-09-30");
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
    assertThat(first.affiliationStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(first.affiliationEndDate()).isEqualTo(LocalDate.of(2026, 9, 30));
  }

  @Test
  @DisplayName("Opens the real OOXML valid fixture")
  void parsesRealXlsxFixture() throws Exception {
    List<ImportedRosterRow> rows = parser.parse(readResource("/sports/xlsx/valid-roster.xlsx"));

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).affiliationStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(rows.get(1).matriculaCode()).isEqualTo("11821BCC002");
  }

  @Test
  @DisplayName("Preserves blank affiliation dates for legacy rows")
  void preservesBlankAffiliationDates() throws Exception {
    byte[] bytes =
        createWorkbookBytes(
            sheet -> {
              createHeader(sheet);
              createValidRow(sheet, 1, "11821BCC001", 10, "", "");
            });

    ImportedRosterRow row = parser.parse(bytes).get(0);

    assertThat(row.affiliationStartDate()).isNull();
    assertThat(row.affiliationEndDate()).isNull();
  }

  @Test
  @DisplayName("Rejects a numeric matricula cell")
  void rejectsNumericMatricula() throws Exception {
    byte[] bytes =
        createWorkbookBytes(
            sheet -> {
              createHeader(sheet);
              Row row = createBaseRow(sheet, 1);
              row.createCell(4).setCellValue(123456789.0);
              row.createCell(5).setCellValue(7);
              row.createCell(6).setCellValue("Pivo");
              row.createCell(7).setCellValue("2026-08-01");
              row.createCell(8).setCellValue("2026-09-30");
            });

    assertThatThrownBy(() -> parser.parse(bytes))
        .isInstanceOf(ApiProblemException.class)
        .hasMessage("BETUFU-400-VALIDATION");
  }

  @Test
  @DisplayName("Rejects formulas in XLSX")
  void rejectsFormulasInXlsx() throws Exception {
    byte[] bytes =
        createWorkbookBytes(
            sheet -> {
              createHeader(sheet);
              Row row = createBaseRow(sheet, 1);
              row.createCell(4).setCellValue("11821BCC001");
              Cell formulaCell = row.createCell(5);
              formulaCell.setCellFormula("SUM(1, 2)");
              row.createCell(6).setCellValue("Ala");
              row.createCell(7).setCellValue("2026-08-01");
              row.createCell(8).setCellValue("2026-09-30");
            });

    assertThatThrownBy(() -> parser.parse(bytes))
        .isInstanceOf(ApiProblemException.class)
        .hasMessage("BETUFU-400-VALIDATION");
  }

  @Test
  @DisplayName("Rejects merged cells")
  void rejectsMergedCellsInXlsx() throws Exception {
    byte[] bytes =
        createWorkbookBytes(
            sheet -> {
              createHeader(sheet);
              sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));
            });

    assertThatThrownBy(() -> parser.parse(bytes))
        .isInstanceOf(ApiProblemException.class)
        .hasMessage("BETUFU-400-VALIDATION");
  }

  @Test
  @DisplayName("Rejects a sheet name outside Template")
  void rejectsWrongSheetName() throws Exception {
    byte[] bytes =
        createWorkbookBytes(
            sheet -> {
              sheet.getWorkbook().setSheetName(0, "Dados");
              createHeader(sheet);
            });

    assertThatThrownBy(() -> parser.parse(bytes))
        .isInstanceOf(ApiProblemException.class)
        .hasMessage("BETUFU-400-VALIDATION");
  }

  @Test
  @DisplayName("Rejects additional sheets")
  void rejectsAdditionalSheet() throws Exception {
    byte[] bytes =
        createWorkbookBytes(
            sheet -> {
              createHeader(sheet);
              sheet.getWorkbook().createSheet("Extra");
            });

    assertThatThrownBy(() -> parser.parse(bytes))
        .isInstanceOf(ApiProblemException.class)
        .hasMessage("BETUFU-400-VALIDATION");
  }

  @Test
  @DisplayName("Rejects macro-enabled workbooks")
  void rejectsMacroEnabledWorkbook() throws Exception {
    byte[] bytes =
        createWorkbookBytes(
            sheet -> {
              createHeader(sheet);
              ((XSSFWorkbook) sheet.getWorkbook())
                  .setWorkbookType(org.apache.poi.xssf.usermodel.XSSFWorkbookType.XLSM);
            });

    assertThatThrownBy(() -> parser.parse(bytes))
        .isInstanceOf(ApiProblemException.class)
        .hasMessage("BETUFU-400-VALIDATION");
  }

  @Test
  @DisplayName("Rejects an extra column")
  void rejectsExtraColumn() throws Exception {
    byte[] bytes =
        createWorkbookBytes(
            sheet -> {
              createHeader(sheet);
              sheet
                  .getRow(0)
                  .createCell(SportsXlsxParser.EXPECTED_HEADERS.size())
                  .setCellValue("extra");
            });

    assertThatThrownBy(() -> parser.parse(bytes))
        .isInstanceOf(ApiProblemException.class)
        .hasMessage("BETUFU-400-VALIDATION");
  }

  @Test
  @DisplayName("Rejects unexpected cell types")
  void rejectsUnexpectedCellType() throws Exception {
    byte[] bytes =
        createWorkbookBytes(
            sheet -> {
              createHeader(sheet);
              Row row = createBaseRow(sheet, 1);
              row.createCell(0).setCellValue(true);
              row.createCell(4).setCellValue("11821BCC001");
              row.createCell(5).setCellValue(7);
              row.createCell(6).setCellValue("Pivo");
              row.createCell(7).setCellValue("2026-08-01");
              row.createCell(8).setCellValue("2026-09-30");
            });

    assertThatThrownBy(() -> parser.parse(bytes))
        .isInstanceOf(ApiProblemException.class)
        .hasMessage("BETUFU-400-VALIDATION");
  }

  @Test
  @DisplayName("Rejects invalid dates")
  void rejectsInvalidDate() throws Exception {
    byte[] bytes =
        createWorkbookBytes(
            sheet -> {
              createHeader(sheet);
              createValidRow(sheet, 1, "11821BCC001", 10, "01/08/2026", "2026-09-30");
            });

    assertThatThrownBy(() -> parser.parse(bytes))
        .isInstanceOf(ApiProblemException.class)
        .hasMessage("BETUFU-400-VALIDATION");
  }

  @Test
  @DisplayName("Rejects malformed XLSX")
  void rejectsMalformedXlsx() {
    assertThatThrownBy(() -> parser.parse("corrupted-binary-content".getBytes()))
        .isInstanceOf(ApiProblemException.class)
        .hasMessage("BETUFU-400-VALIDATION");
  }

  @Test
  @DisplayName("Rejects XLSX that exceeds the row limit")
  void rejectsOverLimitsXlsx() throws Exception {
    byte[] bytes =
        createWorkbookBytes(
            sheet -> {
              createHeader(sheet);
              for (int r = 1; r <= 501; r++) {
                createValidRow(sheet, r, "11821BCC001", 10, "2026-08-01", "2026-09-30");
              }
            });

    assertThatThrownBy(() -> parser.parse(bytes))
        .isInstanceOf(ApiProblemException.class)
        .hasMessage("BETUFU-400-VALIDATION");
  }

  private void createHeader(Sheet sheet) {
    Row header = sheet.createRow(0);
    for (int i = 0; i < SportsXlsxParser.EXPECTED_HEADERS.size(); i++) {
      header.createCell(i).setCellValue(SportsXlsxParser.EXPECTED_HEADERS.get(i));
    }
  }

  private Row createBaseRow(Sheet sheet, int rowNumber) {
    Row row = sheet.createRow(rowNumber);
    row.createCell(0).setCellValue("atletica-engenharia");
    row.createCell(1).setCellValue("futsal");
    row.createCell(2).setCellValue("masculino");
    row.createCell(3).setCellValue("Carlos Silva");
    return row;
  }

  private void createValidRow(
      Sheet sheet, int rowNumber, String matricula, int jersey, String startDate, String endDate) {
    Row row = createBaseRow(sheet, rowNumber);
    row.createCell(4).setCellValue(matricula);
    row.createCell(5).setCellValue(jersey);
    row.createCell(6).setCellValue("Ala");
    row.createCell(7).setCellValue(startDate);
    row.createCell(8).setCellValue(endDate);
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

  private byte[] readResource(String path) throws Exception {
    try (InputStream is = getClass().getResourceAsStream(path)) {
      if (is == null) {
        throw new IllegalArgumentException("Resource not found: " + path);
      }
      return is.readAllBytes();
    }
  }
}
