package br.com.betufu.odds;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class OddsCalibrationFixtureTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Path GOLDEN_ROOT = Path.of("src/test/resources/odds/golden");
  private static final String SCHEMA = "04-12.golden.v1";
  private static final String MANIFEST = "04-01.calibration-manifest.v2";

  @Test
  void loadsExactlySixIndependentGoldenVectors() {
    assertThat(
            List.of(
                "elo-1500.json",
                "elo-k-threshold.json",
                "edition-regression-50.json",
                "davidson-draw.json",
                "confidence-bands.json",
                "margin-rounding.json"))
        .allSatisfy(file -> assertCommon(load(file), file.substring(0, file.length() - 5)));
  }

  @Test
  void everyVectorHasStableDigestAndRejectsLaufuAuthority() {
    for (String file : goldenFiles()) {
      JsonNode root = load(file);
      String input = root.path("oracleDigestInput").asText();
      String expected = root.path("oracleDigestSha256").asText();
      assertThat(sha256(input)).isEqualTo(expected);
      assertThat(sha256(input + "|tampered")).isNotEqualTo(expected);
      assertThat(root.path("usesLaufu").asBoolean()).isFalse();
      assertThat(root.path("sourceAuthority").asText().toLowerCase())
          .doesNotContain("laufu", "exploratory", "snapshot");
    }
  }

  @Test
  void initialEloIs1500PerSportAndCategory() {
    JsonNode payload = load("elo-1500.json").path("payload");
    assertThat(payload.path("sport").asText()).isNotBlank();
    assertThat(payload.path("category").asText()).isNotBlank();
    assertThat(payload.path("homeInitialRating").asDouble()).isEqualTo(1500.0);
    assertThat(payload.path("awayInitialRating").asDouble()).isEqualTo(1500.0);
    assertThat(payload.path("expectedEqualProbability").asDouble()).isEqualTo(0.5);
  }

  @Test
  void eloKVectorKeepsProvisionalAndMatureModesExplicit() {
    JsonNode modes = load("elo-k-threshold.json").path("payload").path("modes");
    assertThat(modes).hasSize(2);
    assertThat(modes.get(0).path("name").asText()).isEqualTo("PROVISIONAL");
    assertThat(modes.get(0).path("kFactor").asDouble()).isEqualTo(32.0);
    assertThat(modes.get(1).path("name").asText()).isEqualTo("MATURE");
    assertThat(modes.get(1).path("kFactor").asDouble()).isEqualTo(16.0);
    assertThat(modes.get(0).path("thresholdSelectedHere").asBoolean()).isFalse();
    assertThat(modes.get(1).path("thresholdSelectedHere").asBoolean()).isFalse();
  }

  @Test
  void editionRegressionMovesHalfwayToward1500() {
    JsonNode payload = load("edition-regression-50.json").path("payload");
    double previous = payload.path("previousRating").asDouble();
    double initial = payload.path("initialRating").asDouble();
    double fraction = payload.path("regressionFraction").asDouble();
    double expected = initial + (previous - initial) * (1.0 - fraction);
    assertThat(expected).isEqualTo(payload.path("expectedRating").asDouble());
    assertThat(fraction).isEqualTo(0.5);
  }

  @Test
  void davidsonVectorBranchesOnlyWhenRegulationAllowsDraw() {
    JsonNode payload = load("davidson-draw.json").path("payload");
    JsonNode withoutDraw = payload.path("withoutDraw");
    JsonNode withDraw = payload.path("withDraw");
    assertThat(withoutDraw.path("regulationAllowsDraw").asBoolean()).isFalse();
    assertThat(withoutDraw.path("outcomes")).hasSize(2);
    assertThat(withoutDraw.path("probabilityCount").asInt()).isEqualTo(2);
    assertThat(withDraw.path("regulationAllowsDraw").asBoolean()).isTrue();
    assertThat(withDraw.path("outcomes")).hasSize(3);
    assertThat(withDraw.path("outcomes").get(1).asText()).isEqualTo("DRAW");
    assertThat(withDraw.path("probabilityCount").asInt()).isEqualTo(3);
    assertThat(payload.path("drawPriorStatus").asText()).isEqualTo("open_pending_calibration");
  }

  @Test
  void confidenceVectorKeepsLowSampleNeutralAndDoesNotChangeMargin() {
    JsonNode payload = load("confidence-bands.json").path("payload");
    JsonNode low = payload.path("lowSample");
    JsonNode mature = payload.path("matureSample");
    assertThat(low.path("band").asText()).isEqualTo("LOW");
    assertThat(low.path("neutralEstimate").asBoolean()).isTrue();
    assertThat(low.path("marginMultiplier").asDouble()).isEqualTo(1.0);
    assertThat(mature.path("band").asText()).isEqualTo("HIGH");
    assertThat(mature.path("neutralEstimate").asBoolean()).isFalse();
    assertThat(mature.path("marginMultiplier").asDouble()).isEqualTo(1.0);
    assertThat(payload.path("thresholdsStatus").asText()).isEqualTo("open_pending_calibration");
    assertThat(payload.path("numericConfidenceValuesSelectedHere").asBoolean()).isFalse();
  }

  @Test
  void marginVectorSeparatesFairProbabilityAndPublishedRoundedPrice() {
    JsonNode payload = load("margin-rounding.json").path("payload");
    BigDecimal margin = BigDecimal.valueOf(payload.path("margin").asDouble());
    BigDecimal multiplier = BigDecimal.ONE.add(margin);
    JsonNode fair = payload.path("fairProbabilities");
    JsonNode unroundedOdds = payload.path("unroundedPublishedOdds");
    assertThat(payload.path("expectedOverround").asDouble()).isEqualTo(1.05);
    assertThat(payload.path("decimalPlaces").asInt()).isEqualTo(2);
    assertThat(payload.path("roundingModeStatus").asText()).isEqualTo("open_pending_calibration");
    assertThat(payload.path("roundingModeSelectedHere").asBoolean()).isFalse();
    assertThat(BigDecimal.valueOf(sum(fair)).multiply(multiplier).doubleValue()).isEqualTo(1.05);
    for (int i = 0; i < fair.size(); i++) {
      BigDecimal fairProbability = BigDecimal.valueOf(fair.get(i).asDouble());
      double odds =
          BigDecimal.ONE
              .divide(fairProbability.multiply(multiplier), 16, java.math.RoundingMode.HALF_EVEN)
              .doubleValue();
      assertThat(odds)
          .isCloseTo(unroundedOdds.get(i).asDouble(), org.assertj.core.data.Offset.offset(1e-12));
    }
  }

  private static List<String> goldenFiles() {
    return List.of(
        "elo-1500.json",
        "elo-k-threshold.json",
        "edition-regression-50.json",
        "davidson-draw.json",
        "confidence-bands.json",
        "margin-rounding.json");
  }

  private static JsonNode load(String file) {
    try {
      return JSON.readTree(Files.readString(GOLDEN_ROOT.resolve(file), StandardCharsets.UTF_8));
    } catch (IOException exception) {
      throw new AssertionError("golden fixture could not be read: " + file, exception);
    }
  }

  private static void assertCommon(JsonNode root, String fixtureId) {
    assertThat(root.path("schemaVersion").asText()).isEqualTo(SCHEMA);
    assertThat(root.path("fixtureId").asText()).isEqualTo(fixtureId);
    assertThat(root.path("oracle").asText()).isNotBlank();
    assertThat(root.path("sourceManifest").asText()).isEqualTo(MANIFEST);
    assertThat(root.path("usesLaufu").asBoolean()).isFalse();
    assertThat(root.path("payload").isObject()).isTrue();
  }

  private static double sum(JsonNode values) {
    double total = 0.0;
    for (JsonNode value : values) {
      total += value.asDouble();
    }
    return total;
  }

  private static String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte item : digest) {
        hex.append(String.format("%02X", item));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new AssertionError("SHA-256 is required by the approved toolchain", exception);
    }
  }
}
