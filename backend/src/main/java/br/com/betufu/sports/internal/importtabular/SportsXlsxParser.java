package br.com.betufu.sports.internal.importtabular;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SportsXlsxParser {

  public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
  public static final int MAX_DATA_ROWS = 500;

  public static final List<String> EXPECTED_HEADERS =
      List.of(
          "organizacao_slug",
          "modalidade_slug",
          "categoria_slug",
          "nome_atleta",
          "matricula",
          "numero_camisa",
          "posicao");

  public List<ImportedRosterRow> parse(byte[] fileBytes) {
    if (fileBytes == null || fileBytes.length == 0) {
      throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
    }
    if (fileBytes.length > MAX_FILE_BYTES) {
      throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
    }

    List<ImportedRosterRow> rows = new ArrayList<>();
    try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(fileBytes))) {
      if (workbook.getNumberOfSheets() == 0) {
        throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
      }

      Sheet sheet = workbook.getSheetAt(0);
      if (sheet.getNumMergedRegions() > 0) {
        throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
      }

      Row headerRow = sheet.getRow(0);
      if (headerRow == null) {
        throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
      }
      validateHeader(headerRow);

      int dataRowCount = 0;
      int lastRowNum = sheet.getLastRowNum();
      for (int r = 1; r <= lastRowNum; r++) {
        Row row = sheet.getRow(r);
        if (row == null || isRowEmpty(row)) {
          continue;
        }

        dataRowCount++;
        if (dataRowCount > MAX_DATA_ROWS) {
          throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
        }

        assertNoFormulas(row);

        String orgSlug = readStringCell(row.getCell(0));
        String sportSlug = readStringCell(row.getCell(1));
        String catSlug = readStringCell(row.getCell(2));
        String athleteName = readStringCell(row.getCell(3));
        String matriculaCode = readMatriculaCell(row.getCell(4));
        Integer jerseyNumber = readJerseyCell(row.getCell(5));
        String position = readStringCell(row.getCell(6));
        String positionName = position.isBlank() ? null : position;
        int lineNumber = row.getRowNum() + 1;

        rows.add(
            new ImportedRosterRow(
                lineNumber,
                orgSlug,
                sportSlug,
                catSlug,
                athleteName,
                matriculaCode,
                jerseyNumber,
                positionName));
      }
    } catch (ApiProblemException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
    }

    return rows;
  }

  private void validateHeader(Row headerRow) {
    for (int i = 0; i < EXPECTED_HEADERS.size(); i++) {
      Cell cell = headerRow.getCell(i);
      if (cell == null) {
        throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
      }
      String actual = readStringCell(cell).toLowerCase(Locale.ROOT);
      String expected = EXPECTED_HEADERS.get(i);
      if (!actual.equalsIgnoreCase(expected)) {
        throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
      }
    }
  }

  private void assertNoFormulas(Row row) {
    for (int c = 0; c < EXPECTED_HEADERS.size(); c++) {
      Cell cell = row.getCell(c);
      if (cell != null && cell.getCellType() == CellType.FORMULA) {
        throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
      }
    }
  }

  private boolean isRowEmpty(Row row) {
    for (int c = 0; c < EXPECTED_HEADERS.size(); c++) {
      Cell cell = row.getCell(c);
      if (cell != null && cell.getCellType() != CellType.BLANK) {
        String val = readStringCell(cell);
        if (!val.isBlank()) {
          return false;
        }
      }
    }
    return true;
  }

  private String readStringCell(Cell cell) {
    if (cell == null) {
      return "";
    }
    if (cell.getCellType() == CellType.STRING) {
      return cell.getStringCellValue().trim();
    }
    if (cell.getCellType() == CellType.NUMERIC) {
      double val = cell.getNumericCellValue();
      if (val == Math.floor(val)) {
        return String.valueOf((long) val);
      }
      return String.valueOf(val);
    }
    if (cell.getCellType() == CellType.BOOLEAN) {
      return String.valueOf(cell.getBooleanCellValue());
    }
    return "";
  }

  private String readMatriculaCell(Cell cell) {
    if (cell == null) {
      return "";
    }
    if (cell.getCellType() == CellType.NUMERIC) {
      return String.valueOf((long) cell.getNumericCellValue());
    }
    return readStringCell(cell);
  }

  private Integer readJerseyCell(Cell cell) {
    if (cell == null) {
      return null;
    }
    if (cell.getCellType() == CellType.NUMERIC) {
      return (int) cell.getNumericCellValue();
    }
    String text = readStringCell(cell);
    if (text.isBlank()) {
      return null;
    }
    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException e) {
      return -1;
    }
  }
}
