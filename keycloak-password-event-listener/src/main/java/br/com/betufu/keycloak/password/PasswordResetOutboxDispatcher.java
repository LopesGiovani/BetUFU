package br.com.betufu.keycloak.password;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.utils.KeycloakModelUtils;

public class PasswordResetOutboxDispatcher implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(PasswordResetOutboxDispatcher.class.getName());

    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration DEFAULT_MAX_RETRY_DELAY = Duration.ofSeconds(30);
    public static final Duration DEFAULT_LEASE_DURATION = Duration.ofSeconds(60);
    public static final int DEFAULT_MAX_ATTEMPTS = 5;

    public static final String DEFAULT_PATH = "/api/v1/internal/events/password-reset";
    public static final String HEADER_KEY_ID = "X-Key-Id";
    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    public static final String HEADER_SIGNATURE = "X-Signature";
    public static final String HEADER_SOURCE_EVENT_ID = "X-Source-Event-Id";

    private final String instanceId;
    private final String endpointUri;
    private final byte[] hmacKey;
    private final String keyId;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration maxRetryDelay;
    private final Duration leaseDuration;
    private final int maxAttempts;
    private final HttpClient httpClient;
    private final Clock clock;
    private final Random random;
    private final AlertListener alertListener;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;

    public PasswordResetOutboxDispatcher(
            String instanceId,
            String endpointUri,
            byte[] hmacKey,
            String keyId,
            Duration connectTimeout,
            Duration readTimeout,
            Duration maxRetryDelay,
            Duration leaseDuration,
            int maxAttempts,
            HttpClient httpClient,
            Clock clock,
            Random random,
            AlertListener alertListener) {
        this.instanceId = (instanceId != null && !instanceId.isBlank()) ? instanceId : UUID.randomUUID().toString();
        this.endpointUri = endpointUri;
        this.hmacKey = decodeBase64Key(hmacKey);
        this.keyId = (keyId != null && !keyId.isBlank()) ? keyId : "v1";
        this.connectTimeout = connectTimeout != null ? connectTimeout : DEFAULT_CONNECT_TIMEOUT;
        this.readTimeout = readTimeout != null ? readTimeout : DEFAULT_READ_TIMEOUT;
        this.maxRetryDelay = maxRetryDelay != null ? maxRetryDelay : DEFAULT_MAX_RETRY_DELAY;
        this.leaseDuration = leaseDuration != null ? leaseDuration : DEFAULT_LEASE_DURATION;
        this.maxAttempts = maxAttempts > 0 ? maxAttempts : DEFAULT_MAX_ATTEMPTS;
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.random = random != null ? random : new Random();
        this.alertListener = alertListener;

        if (httpClient != null) {
            this.httpClient = httpClient;
        } else {
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(this.connectTimeout)
                    .build();
        }
    }

    public static byte[] decodeBase64Key(byte[] hmacKeyBytes) {
        if (hmacKeyBytes == null || hmacKeyBytes.length == 0) {
            return new byte[0];
        }
        String keyStr = new String(hmacKeyBytes, StandardCharsets.UTF_8).trim();
        if (keyStr.isEmpty()) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(keyStr);
    }

    public static byte[] decodeBase64Key(String hmacKeyBase64) {
        if (hmacKeyBase64 == null || hmacKeyBase64.isBlank()) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(hmacKeyBase64.trim());
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getEndpointUri() {
        return endpointUri;
    }

    public String getKeyId() {
        return keyId;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public Duration getMaxRetryDelay() {
        return maxRetryDelay;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Clock getClock() {
        return clock;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public boolean isRunning() {
        return running.get();
    }

    public synchronized void start(KeycloakSessionFactory sessionFactory, long pollIntervalMillis) {
        if (running.compareAndSet(false, true)) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "betufu-outbox-dispatcher-" + instanceId);
                t.setDaemon(true);
                return t;
            });

            long interval = pollIntervalMillis > 0 ? pollIntervalMillis : 1000L;
            scheduler.scheduleWithFixedDelay(() -> {
                try {
                    KeycloakModelUtils.runJobInTransaction(sessionFactory, this::dispatchPending);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error in outbox dispatch cycle: " + e.getMessage(), e);
                }
            }, interval, interval, TimeUnit.MILLISECONDS);
        }
    }

    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            if (scheduler != null) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                scheduler = null;
            }
        }
    }

    @Override
    public void close() {
        stop();
    }

    public int dispatchPending(KeycloakSession session) {
        Objects.requireNonNull(session, "session must not be null");
        PasswordResetOutboxStore store = new PasswordResetOutboxStore(session);
        return dispatchEligible(store, 20);
    }

    public int dispatchEligible(PasswordResetOutboxStore store, int batchSize) {
        Objects.requireNonNull(store, "store must not be null");
        Instant now = clock.instant();
        List<PasswordResetOutboxEntity> eligible = store.findEligibleForClaim(now, leaseDuration, batchSize);
        int dispatchedCount = 0;

        for (PasswordResetOutboxEntity entity : eligible) {
            boolean claimed = store.claim(entity.getId(), instanceId, now, leaseDuration);
            if (claimed) {
                dispatchOne(entity, store);
                dispatchedCount++;
            }
        }
        return dispatchedCount;
    }

    public boolean dispatchOne(PasswordResetOutboxEntity entity, PasswordResetOutboxStore store) {
        Objects.requireNonNull(entity, "entity must not be null");
        Objects.requireNonNull(store, "store must not be null");

        if (endpointUri == null || endpointUri.isBlank()) {
            handleFailure(entity, store, "Endpoint URI is not configured");
            return false;
        }

        try {
            String payloadJson = buildPayloadJson(entity);
            String bodySha256 = sha256Hex(payloadJson);
            String timestamp = String.valueOf(clock.instant().toEpochMilli());

            URI uri = URI.create(endpointUri);
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                path = DEFAULT_PATH;
            }

            String canonicalString = buildCanonicalString(
                    keyId,
                    timestamp,
                    "POST",
                    path,
                    entity.getSourceEventId(),
                    bodySha256);

            String signature = calculateHmacSha256(hmacKey, canonicalString);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(readTimeout)
                    .header("Content-Type", "application/json")
                    .header(HEADER_KEY_ID, keyId)
                    .header(HEADER_TIMESTAMP, timestamp)
                    .header(HEADER_SIGNATURE, signature)
                    .header(HEADER_SOURCE_EVENT_ID, entity.getSourceEventId())
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int statusCode = response.statusCode();

            if (statusCode >= 200 && statusCode < 300) {
                store.markDelivered(entity.getId(), clock.instant());
                return true;
            } else {
                handleFailure(entity, store, "HTTP error status: " + statusCode);
                return false;
            }
        } catch (HttpTimeoutException e) {
            handleFailure(entity, store, "Response timeout: " + e.getMessage());
            return false;
        } catch (IOException e) {
            handleFailure(entity, store, "Transport error: " + e.getMessage());
            return false;
        } catch (Exception e) {
            handleFailure(entity, store, "Unexpected failure: " + e.getMessage());
            return false;
        }
    }

    public void handleFailure(PasswordResetOutboxEntity entity, PasswordResetOutboxStore store, String errorMessage) {
        Instant now = clock.instant();
        int nextAttemptCount = entity.getAttemptCount() + 1;
        Duration backoff = computeBackoffWithJitter(nextAttemptCount, this.random);
        Instant nextAttemptAt = now.plus(backoff);

        store.recordFailure(entity.getId(), errorMessage, now, nextAttemptAt, maxAttempts);
        Optional<PasswordResetOutboxEntity> updatedOpt = store.findBySourceEventId(entity.getSourceEventId());
        int attempts = updatedOpt.map(PasswordResetOutboxEntity::getAttemptCount).orElse(nextAttemptCount);

        if (attempts == maxAttempts) {
            emitAlert(entity, errorMessage, now, attempts);
        }
    }

    private void emitAlert(PasswordResetOutboxEntity entity, String errorMessage, Instant timestamp, int attemptCount) {
        AlertEvent alert = new AlertEvent(
                "PASSWORD_RESET_DELIVERY_FAILED",
                entity.getSourceEventId(),
                PasswordResetOutboxEntity.STATUS_FAILED,
                timestamp);

        if (alertListener != null) {
            try {
                alertListener.onAlert(alert);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to invoke alert listener: " + e.getMessage(), e);
            }
        }
        LOGGER.log(Level.SEVERE, "Outbox delivery reached maximum attempts for sourceEventId: " + entity.getSourceEventId());
    }

    public Duration computeBackoffWithJitter(int attempt, Random customRandom) {
        if (attempt <= 0) {
            return Duration.ZERO;
        }
        long baseMillis = 1000L;
        int shift = Math.min(attempt - 1, 5);
        long exponentialMillis = baseMillis * (1L << shift);

        Random rnd = customRandom != null ? customRandom : this.random;
        double jitterMultiplier = 0.5 + (0.5 * rnd.nextDouble());
        long jitteredMillis = (long) (exponentialMillis * jitterMultiplier);

        long cappedMillis = Math.min(jitteredMillis, maxRetryDelay.toMillis());
        return Duration.ofMillis(Math.max(1L, cappedMillis));
    }

    /**
     * Canonicalização literal: nenhum campo é normalizado aqui. O destino canonicaliza
     * literalmente, e normalizar só de um lado faz origem e destino assinarem strings diferentes
     * sempre que o chamador variar a caixa do método ou do hash.
     */
    public static String buildCanonicalString(
            String keyId,
            String timestamp,
            String method,
            String path,
            String sourceEventId,
            String bodySha256) {
        return keyId + "\n" +
                timestamp + "\n" +
                method + "\n" +
                path + "\n" +
                sourceEventId + "\n" +
                bodySha256;
    }

    /**
     * O destino é autoritativo sobre o contrato e recusa qualquer valor que não seja
     * {@code VALUE_STRING}. O timestamp em milissegundos, portanto, viaja citado: emiti-lo como
     * número JSON fazia a cadeia real responder 400 sem que nenhum teste de origem percebesse.
     */
    public static String buildPayloadJson(PasswordResetOutboxEntity entity) {
        long timestamp = entity.getCreatedAt() != null ? entity.getCreatedAt().toEpochMilli() : 0L;
        return "{\"sourceEventId\":\"" + escapeJson(entity.getSourceEventId()) + "\"" +
                ",\"subject\":\"" + escapeJson(entity.getSubject()) + "\"" +
                ",\"realmId\":\"" + escapeJson(entity.getRealmId()) + "\"" +
                ",\"timestamp\":\"" + timestamp + "\"}";
    }

    public static String sha256Hex(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static String calculateHmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(key, "HmacSHA256");
            mac.init(keySpec);
            byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 computation failed", e);
        }
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @FunctionalInterface
    public interface AlertListener {
        void onAlert(AlertEvent alert);
    }

    /**
     * O alerta atravessa a fronteira operacional e por isso carrega o mínimo previsto no threat
     * model: a correlação ({@code sourceEventId}) e o estado resultante. Sujeito, realm, tipo do
     * evento Keycloak e a mensagem crua da exceção ficam de fora — são dado do titular ou detalhe
     * interno, e um canal de alerta não é lugar para nenhum dos dois.
     */
    public record AlertEvent(
            String alertType,
            String sourceEventId,
            String status,
            Instant timestamp) {
    }

    public static class Builder {
        private String instanceId;
        private String endpointUri;
        private byte[] hmacKey;
        private String keyId = "v1";
        private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private Duration readTimeout = DEFAULT_READ_TIMEOUT;
        private Duration maxRetryDelay = DEFAULT_MAX_RETRY_DELAY;
        private Duration leaseDuration = DEFAULT_LEASE_DURATION;
        private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
        private HttpClient httpClient;
        private Clock clock;
        private Random random;
        private AlertListener alertListener;

        public Builder instanceId(String instanceId) {
            this.instanceId = instanceId;
            return this;
        }

        public Builder endpointUri(String endpointUri) {
            this.endpointUri = endpointUri;
            return this;
        }

        public Builder hmacKey(byte[] hmacKey) {
            this.hmacKey = hmacKey;
            return this;
        }

        public Builder hmacKey(String base64Key) {
            this.hmacKey = base64Key != null ? base64Key.getBytes(StandardCharsets.UTF_8) : new byte[0];
            return this;
        }

        public Builder keyId(String keyId) {
            this.keyId = keyId;
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public Builder maxRetryDelay(Duration maxRetryDelay) {
            this.maxRetryDelay = maxRetryDelay;
            return this;
        }

        public Builder leaseDuration(Duration leaseDuration) {
            this.leaseDuration = leaseDuration;
            return this;
        }

        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder random(Random random) {
            this.random = random;
            return this;
        }

        public Builder alertListener(AlertListener alertListener) {
            this.alertListener = alertListener;
            return this;
        }

        public PasswordResetOutboxDispatcher build() {
            return new PasswordResetOutboxDispatcher(
                    instanceId,
                    endpointUri,
                    hmacKey,
                    keyId,
                    connectTimeout,
                    readTimeout,
                    maxRetryDelay,
                    leaseDuration,
                    maxAttempts,
                    httpClient,
                    clock,
                    random,
                    alertListener);
        }
    }
}
