package br.com.betufu.platform.session.passwordreset;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Canonicalizes and verifies HMAC-signed password reset event envelopes in constant time.
 *
 * <p>Canonical envelope format:
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

    /**
     * Builds the canonical string representation of the signed envelope.
     */
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

        if (keyId.isBlank() || timestamp.isBlank() || method.isBlank() || path.isBlank()
                || sourceEventId.isBlank() || bodySha256.isBlank()) {
            throw new IllegalArgumentException("Canonical envelope components must not be blank");
        }

        return String.join("\n",
                keyId,
                timestamp,
                method,
                path,
                sourceEventId,
                bodySha256);
    }

    /**
     * Computes the SHA-256 hex digest of the raw request body.
     */
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

    /**
     * Signs the canonical envelope with the provided secret key using HmacSHA256.
     */
    public String sign(
            byte[] secretKey,
            String keyId,
            String timestamp,
            String method,
            String path,
            String sourceEventId,
            String bodySha256) {
        Objects.requireNonNull(secretKey, "secretKey must not be null");
        if (secretKey.length < 16) {
            throw new IllegalArgumentException("Secret key must have at least 16 bytes");
        }
        String canonical = canonicalize(keyId, timestamp, method, path, sourceEventId, bodySha256);
        return computeHmacHex(secretKey, canonical);
    }

    /**
     * Computes the hex-encoded HMAC-SHA256 of the given string data.
     */
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

    /**
     * Verifies the provided HMAC signature in constant time.
     */
    public boolean verifySignature(
            byte[] secretKey,
            String expectedSignatureHex,
            String keyId,
            String timestamp,
            String method,
            String path,
            String sourceEventId,
            String bodySha256) {
        if (secretKey == null || secretKey.length == 0 || expectedSignatureHex == null || expectedSignatureHex.isBlank()) {
            return false;
        }
        try {
            String computed = sign(secretKey, keyId, timestamp, method, path, sourceEventId, bodySha256);
            byte[] expectedBytes = HexFormat.of().parseHex(expectedSignatureHex.trim().toLowerCase(Locale.ROOT));
            byte[] computedBytes = HexFormat.of().parseHex(computed);
            return MessageDigest.isEqual(expectedBytes, computedBytes);
        } catch (Exception e) {
            return false;
        }
    }
}
