package br.com.betufu.platform.session.passwordreset;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Canonicalizes and verifies HMAC-signed password reset event envelopes in constant time, and
 * enforces a ±5 minute timestamp replay window against an injectable clock.
 *
 * <p>Canonical envelope format:
 *
 * <pre>
 * keyId\n
 * timestamp\n
 * method\n
 * path\n
 * sourceEventId\n
 * bodySha256
 * </pre>
 */
@Component
public class PasswordResetSignedEnvelopeVerifier {

  public static final String HMAC_ALGORITHM = "HmacSHA256";
  public static final String HASH_ALGORITHM = "SHA-256";
  public static final Duration TIMESTAMP_WINDOW = Duration.ofMinutes(5);

  private final Clock clock;

  @Autowired
  public PasswordResetSignedEnvelopeVerifier(@Autowired(required = false) Clock clock) {
    this.clock = clock != null ? clock : Clock.systemUTC();
  }

  public PasswordResetSignedEnvelopeVerifier() {
    this(Clock.systemUTC());
  }

  public Clock getClock() {
    return clock;
  }

  /** Builds the canonical string representation of the signed envelope. */
  public String canonicalize(
      String keyId,
      String timestamp,
      String method,
      String path,
      String sourceEventId,
      String bodySha256) {
    Objects.requireNonNull(keyId, "keyId must not be null");
    Objects.requireNonNull(timestamp, "timestamp must not be null");
    Objects.requireNonNull(method, "method must not be null");
    Objects.requireNonNull(path, "path must not be null");
    Objects.requireNonNull(sourceEventId, "sourceEventId must not be null");
    Objects.requireNonNull(bodySha256, "bodySha256 must not be null");

    if (keyId.isBlank()
        || timestamp.isBlank()
        || method.isBlank()
        || path.isBlank()
        || sourceEventId.isBlank()
        || bodySha256.isBlank()) {
      throw new IllegalArgumentException("Canonical envelope components must not be blank");
    }

    return String.join("\n", keyId, timestamp, method, path, sourceEventId, bodySha256);
  }

  /** Computes the SHA-256 hex digest of the raw request body. */
  public String computeBodySha256(byte[] bodyBytes) {
    Objects.requireNonNull(bodyBytes, "bodyBytes must not be null");
    try {
      MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
      byte[] hash = digest.digest(bodyBytes);
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Algorithm unavailable: " + HASH_ALGORITHM, e);
    }
  }

  /** Signs the canonical envelope with the provided secret key using HmacSHA256. */
  public String sign(
      byte[] secretKey,
      String keyId,
      String timestamp,
      String method,
      String path,
      String sourceEventId,
      String bodySha256) {
    Objects.requireNonNull(secretKey, "secretKey must not be null");
    if (secretKey.length < PasswordResetEventProperties.MIN_KEY_BYTES) {
      throw new IllegalArgumentException(
          "Secret key must have at least " + PasswordResetEventProperties.MIN_KEY_BYTES + " bytes");
    }
    String canonical = canonicalize(keyId, timestamp, method, path, sourceEventId, bodySha256);
    return computeHmacHex(secretKey, canonical);
  }

  /** Computes the hex-encoded HMAC-SHA256 of the given string data. */
  public String computeHmacHex(byte[] secretKey, String data) {
    Objects.requireNonNull(secretKey, "secretKey must not be null");
    Objects.requireNonNull(data, "data must not be null");
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secretKey, HMAC_ALGORITHM));
      byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(rawHmac);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("Failed to compute HMAC", e);
    }
  }

  /** Verifies the provided HMAC signature in constant time. */
  public boolean verifySignature(
      byte[] secretKey,
      String expectedSignatureHex,
      String keyId,
      String timestamp,
      String method,
      String path,
      String sourceEventId,
      String bodySha256) {
    if (secretKey == null
        || secretKey.length == 0
        || expectedSignatureHex == null
        || expectedSignatureHex.isBlank()) {
      return false;
    }
    try {
      String computed = sign(secretKey, keyId, timestamp, method, path, sourceEventId, bodySha256);
      byte[] expectedBytes =
          HexFormat.of().parseHex(expectedSignatureHex.trim().toLowerCase(Locale.ROOT));
      byte[] computedBytes = HexFormat.of().parseHex(computed);
      return MessageDigest.isEqual(expectedBytes, computedBytes);
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Parses a timestamp string into an Instant. Supports epoch milliseconds (numeric with 12+ digits
   * or >= 100_000_000_000L), epoch seconds (numeric with <= 10 digits), and ISO-8601 string
   * representations.
   */
  public Optional<Instant> parseTimestamp(String timestampStr) {
    if (timestampStr == null || timestampStr.isBlank()) {
      return Optional.empty();
    }
    String trimmed = timestampStr.trim();
    try {
      long epochVal = Long.parseLong(trimmed);
      String digits =
          trimmed.startsWith("-") || trimmed.startsWith("+") ? trimmed.substring(1) : trimmed;
      if (digits.length() >= 12) {
        return Optional.of(Instant.ofEpochMilli(epochVal));
      } else {
        return Optional.of(Instant.ofEpochSecond(epochVal));
      }
    } catch (NumberFormatException | java.time.DateTimeException ignored) {
      try {
        return Optional.of(Instant.parse(trimmed));
      } catch (DateTimeParseException ignoredIso) {
        return Optional.empty();
      }
    }
  }

  /** Verifies that the given timestamp string is within ±5 minutes of the current clock instant. */
  public boolean isTimestampValid(String timestampStr) {
    return isTimestampValid(timestampStr, clock.instant(), TIMESTAMP_WINDOW);
  }

  /**
   * Verifies that the given timestamp string is within the specified window of the reference
   * instant.
   */
  public boolean isTimestampValid(String timestampStr, Instant referenceInstant, Duration window) {
    Optional<Instant> parsed = parseTimestamp(timestampStr);
    if (parsed.isEmpty()) {
      return false;
    }
    Instant eventInstant = parsed.get();
    Instant now = (referenceInstant != null) ? referenceInstant : clock.instant();
    Duration tolerance = (window != null) ? window : TIMESTAMP_WINDOW;

    try {
      Duration diff = Duration.between(eventInstant, now).abs();
      return diff.compareTo(tolerance) <= 0;
    } catch (ArithmeticException | java.time.DateTimeException ignored) {
      return false;
    }
  }
}
