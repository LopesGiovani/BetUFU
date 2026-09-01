package br.com.betufu.platform.session.passwordreset;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectMapper;

/**
 * Internal endpoint for receiving HMAC-signed password reset events from the Keycloak outbox.
 *
 * <p>Security properties:
 *
 * <ul>
 *   <li>Stateless, exact HMAC verification and constant-time comparison;
 *   <li>Payload allowlist: accepts strictly sourceEventId, subject, realmId, eventType, timestamp;
 *   <li>Rejects duplicate fields, unknown fields, passwords, tokens or profile data;
 *   <li>Enforces body size limitation to prevent DoS;
 *   <li>Persists durably into the inbox before triggering security session termination.
 * </ul>
 */
@RestController
@Profile("runtime | integrated")
public class PasswordResetEventEndpoint {

  public static final String ENDPOINT_PATH = "/api/v1/internal/events/password-reset";

  public static final String HEADER_KEY_ID = "X-Key-Id";
  public static final String HEADER_TIMESTAMP = "X-Timestamp";
  public static final String HEADER_SIGNATURE = "X-Signature";
  public static final String HEADER_SOURCE_EVENT_ID = "X-Source-Event-Id";

  private static final Set<String> ALLOWED_JSON_FIELDS =
      Set.of("sourceEventId", "subject", "realmId", "eventType", "timestamp");

  private final PasswordResetSignedEnvelopeVerifier envelopeVerifier;
  private final PasswordResetEventInboxService inboxService;
  private final PasswordResetEventProperties properties;
  private final ObjectMapper objectMapper;

  @Autowired
  public PasswordResetEventEndpoint(
      PasswordResetSignedEnvelopeVerifier envelopeVerifier,
      PasswordResetEventInboxService inboxService,
      ObjectProvider<PasswordResetEventProperties> propertiesProvider) {
    this.envelopeVerifier =
        Objects.requireNonNull(envelopeVerifier, "envelopeVerifier must not be null");
    this.inboxService = Objects.requireNonNull(inboxService, "inboxService must not be null");
    this.properties = propertiesProvider != null ? propertiesProvider.getIfAvailable() : null;
    this.objectMapper = new ObjectMapper();
  }

  public PasswordResetEventEndpoint(
      PasswordResetSignedEnvelopeVerifier envelopeVerifier,
      PasswordResetEventInboxService inboxService) {
    this(envelopeVerifier, inboxService, null);
  }

  public record EventResponse(String status, String sourceEventId, boolean effectExecuted) {}

  @PostMapping(
      path = ENDPOINT_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<EventResponse> handlePasswordResetEvent(
      @RequestHeader(value = HEADER_KEY_ID, required = false) String keyId,
      @RequestHeader(value = HEADER_TIMESTAMP, required = false) String timestamp,
      @RequestHeader(value = HEADER_SIGNATURE, required = false) String signature,
      @RequestHeader(value = HEADER_SOURCE_EVENT_ID, required = false) String headerSourceEventId,
      @RequestBody(required = false) byte[] bodyBytes,
      HttpServletRequest request) {

    // 1. Fail closed if required headers or body are missing
    if (keyId == null
        || keyId.isBlank()
        || timestamp == null
        || timestamp.isBlank()
        || signature == null
        || signature.isBlank()
        || bodyBytes == null
        || bodyBytes.length == 0) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    // 2. Enforce max body size limitation (DoS mitigation)
    int maxBodySize =
        properties != null
            ? properties.getMaxBodySizeBytes()
            : PasswordResetEventProperties.DEFAULT_MAX_BODY_SIZE_BYTES;
    if (bodyBytes.length > maxBodySize) {
      return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
    }

    // 3. Resolve secret key for keyId (fail-closed if unknown keyId, missing key, or invalid
    // Base64)
    byte[] secretKey = resolveSecretKey(keyId);
    if (secretKey == null || secretKey.length == 0) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    // 4. Parse and validate JSON allowlist: exact single spelling per field, no duplicates, no
    // unknown fields
    String sourceEventId = null;
    String subject = null;
    String realmId = null;
    String eventType = null;
    String payloadTimestamp = null;
    Set<String> seenFields = new HashSet<>();

    try (JsonParser parser = objectMapper.createParser(bodyBytes)) {
      if (parser.nextToken() != JsonToken.START_OBJECT) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
      }
      while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
        String fieldName = parser.currentName();
        if (!ALLOWED_JSON_FIELDS.contains(fieldName) || !seenFields.add(fieldName)) {
          return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        JsonToken valueToken = parser.nextToken();
        if (valueToken != JsonToken.VALUE_STRING) {
          return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String text = parser.getText();
        if (text == null || text.isBlank()) {
          return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        switch (fieldName) {
          case "sourceEventId" -> sourceEventId = text;
          case "subject" -> subject = text;
          case "realmId" -> realmId = text;
          case "eventType" -> eventType = text;
          case "timestamp" -> payloadTimestamp = text;
        }
      }
      if (parser.currentToken() != JsonToken.END_OBJECT || parser.nextToken() != null) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
      }
    } catch (Exception ex) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    if (sourceEventId == null || subject == null || realmId == null || payloadTimestamp == null) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    if (headerSourceEventId != null
        && !headerSourceEventId.isBlank()
        && !headerSourceEventId.equals(sourceEventId)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    // 5. Enforce strict ±5 minute timestamp replay window (D-40)
    if (!envelopeVerifier.isTimestampValid(timestamp)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    // 6. Verify envelope HMAC signature in constant time
    String method = request.getMethod();
    String path = request.getRequestURI();
    String bodySha256 = envelopeVerifier.computeBodySha256(bodyBytes);

    boolean validSignature =
        envelopeVerifier.verifySignature(
            secretKey, signature, keyId, timestamp, method, path, sourceEventId, bodySha256);

    if (!validSignature) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    // 7. Persist and apply security effect
    PasswordResetEventInboxService.InboxProcessingResult result;
    try {
      result = inboxService.processEvent(sourceEventId, subject, realmId, eventType);
    } catch (RuntimeException processingFailure) {
      // The inbox service persists a stable FAILED marker before rethrowing effect failures.
      // Do not acknowledge those failures with 2xx and do not expose exception details to
      // the origin. A sanitized 503 keeps the source outbox eligible for retry.
      EventResponse retryableFailure =
          new EventResponse(PasswordResetEventInboxService.STATUS_FAILED, sourceEventId, false);
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(retryableFailure);
    }

    EventResponse response =
        new EventResponse(result.status(), result.sourceEventId(), result.effectExecuted());

    if (isTerminallyDelivered(result.status())) {
      return ResponseEntity.ok(response);
    }
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
  }

  private static boolean isTerminallyDelivered(String status) {
    return PasswordResetEventInboxService.STATUS_DELIVERED.equals(status)
        || PasswordResetEventInboxService.STATUS_PROCESSED.equals(status);
  }

  /**
   * Resolve a chave HMAC exclusivamente pelas propriedades, que aceitam apenas a versao corrente e
   * a anterior (D-40).
   *
   * <p>Nao existe segundo caminho. Havia aqui um fallback que aceitava os apelidos literais {@code
   * "v1"} e {@code "current"} sobre um campo proprio; ele ficou inalcancavel quando a resolucao por
   * propriedades passou a falhar fechada, e foi removido pelo mesmo motivo que o fallback de Bearer
   * do Plano 02-28: codigo morto que se le como caminho de autenticacao e uma armadilha para quem
   * for auditar depois.
   */
  private byte[] resolveSecretKey(String keyId) {
    if (keyId == null || keyId.isBlank() || properties == null) {
      return null;
    }
    byte[] resolved = properties.resolveKey(keyId);
    if (resolved != null && resolved.length >= PasswordResetEventProperties.MIN_KEY_BYTES) {
      return resolved;
    }
    return null;
  }
}
