package br.com.betufu.sports.internal.importtabular;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SportsCsvParser {

  public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
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

    String textContent = decodeUtf8Strict(fileBytes);
    if (textContent.startsWith("\uFEFF")) {
      textContent = textContent.substring(1);
    }

    CSVFormat csvFormat =
        CSVFormat.DEFAULT.builder().setIgnoreSurroundingSpaces(true).setTrim(true).build();

    List<ImportedRosterRow> rows = new ArrayList<>();
    try (CSVParser parser = csvFormat.parse(new StringReader(textContent))) {
      List<CSVRecord> records = parser.getRecords();
      if (records.isEmpty()) {
        throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
      }

      CSVRecord headerRecord = records.get(0);
      validateHeader(headerRecord);

      int dataRowCount = 0;
      for (int i = 1; i < records.size(); i++) {
        CSVRecord record = records.get(i);
        if (isRecordEmpty(record)) {
          continue;
        }
        dataRowCount++;
        if (dataRowCount > MAX_DATA_ROWS) {
          throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
        }

        if (record.size() != EXPECTED_HEADERS.size()) {
          throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
        }

        String orgSlug = record.get(0).trim();
        String sportSlug = record.get(1).trim();
        String catSlug = record.get(2).trim();
        String athleteName = sanitizeFormula(record.get(3).trim());
        String matriculaCode = record.get(4).trim();
        String jerseyStr = record.get(5).trim();
        String position = sanitizeFormula(record.get(6).trim());

        Integer jerseyNumber = null;
        if (!jerseyStr.isBlank()) {
          try {
            jerseyNumber = Integer.parseInt(jerseyStr);
          } catch (NumberFormatException e) {
            // Keep non-numeric jersey as invalid in row or throw
            jerseyNumber = -1;
          }
        }

        String positionName = position.isBlank() ? null : position;
        int lineNumber = (int) record.getRecordNumber();

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

  private String decodeUtf8Strict(byte[] bytes) {
    CharsetDecoder decoder =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    try {
      CharBuffer charBuffer = decoder.decode(ByteBuffer.wrap(bytes));
      return charBuffer.toString();
    } catch (CharacterCodingException e) {
      throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
    }
  }

  private void validateHeader(CSVRecord header) {
    if (header.size() != EXPECTED_HEADERS.size()) {
      throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
    }
    for (int i = 0; i < EXPECTED_HEADERS.size(); i++) {
      String actual = header.get(i).trim().toLowerCase(Locale.ROOT);
      String expected = EXPECTED_HEADERS.get(i);
      if (!actual.equalsIgnoreCase(expected)) {
        throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
      }
    }
  }

  private boolean isRecordEmpty(CSVRecord record) {
    for (int i = 0; i < record.size(); i++) {
      if (!record.get(i).isBlank()) {
        return false;
      }
    }
    return true;
  }

  private String sanitizeFormula(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    char first = value.charAt(0);
    if (first == '=' || first == '+' || first == '-' || first == '@') {
      return "'" + value;
    }
    return value;
  }
}
