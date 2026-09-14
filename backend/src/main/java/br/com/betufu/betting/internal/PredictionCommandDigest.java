package br.com.betufu.betting.internal;

import br.com.betufu.betting.PredictionAcceptancePort.SubmitPrediction;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

final class PredictionCommandDigest {
  private PredictionCommandDigest() {}

  static String sha256(
      UUID identityId, SubmitPrediction command, String selectionKey, BigDecimal stake) {
    List<String> segments =
        List.of(
            "prediction-v1",
            "identityId=" + identityId,
            "marketId=" + command.marketId(),
            "marketVersionId=" + command.marketVersionId(),
            "publicationId=" + command.publicationId(),
            "ruleVersion=" + command.ruleVersion(),
            "selectionKey=" + selectionKey,
            "stake=" + stake.toPlainString());
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (int index = 0; index < segments.size(); index++) {
        if (index > 0) {
          digest.update((byte) 0);
        }
        digest.update(segments.get(index).getBytes(StandardCharsets.UTF_8));
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }
}
