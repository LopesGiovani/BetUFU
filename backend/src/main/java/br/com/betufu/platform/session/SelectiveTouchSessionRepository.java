package br.com.betufu.platform.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisSessionRepository;

/**
 * Preserves Spring Session's default Redis representation while making idle renewal explicit.
 */
public final class SelectiveTouchSessionRepository
        implements SessionRepository<SelectiveTouchSessionRepository.SelectiveSession> {

    static final String APPROVED_ACTIVITY_ATTRIBUTE =
            SelectiveTouchSessionRepository.class.getName() + ".approvedActivity";
    static final String LAST_VALID_EPOCH_SECOND_ATTRIBUTE =
            "betufu.session.lastValidActivityAt.epochSecond";
    static final String LAST_VALID_NANO_ATTRIBUTE =
            "betufu.session.lastValidActivityAt.nano";
    static final String AUTHENTICATION_ACR_ATTRIBUTE = "betufu.session.authentication.acr";
    static final String AUTHENTICATION_AMR_COUNT_ATTRIBUTE =
            "betufu.session.authentication.amr.count";
    static final String AUTHENTICATION_AMR_PREFIX = "betufu.session.authentication.amr.";

    private static final String SESSIONS_SUFFIX = "sessions:";
    private static final String CREATION_TIME_FIELD = "creationTime";
    private static final String LAST_ACCESSED_TIME_FIELD = "lastAccessedTime";
    private static final String MAX_INACTIVE_INTERVAL_FIELD = "maxInactiveInterval";
    private static final String SESSION_ATTRIBUTE_PREFIX = "sessionAttr:";
    private static final int MAX_TRANSACTION_ATTEMPTS = 4;

    private final SessionRepository<? extends Session> delegateRepository;
    private final RedisOperations<String, Object> redisOperations;
    private final Duration idleTimeout;
    private final int idleTimeoutSeconds;
    private final String sessionKeyPrefix;
    private final ConcurrentMap<String, String> pendingOriginalIds = new ConcurrentHashMap<>();

    public SelectiveTouchSessionRepository(
            RedisSessionRepository delegate, String namespace, Duration idleTimeout) {
        RedisSessionRepository requiredDelegate =
                Objects.requireNonNull(delegate, "delegate must not be null");
        this.delegateRepository = requiredDelegate;
        this.redisOperations = requiredDelegate.getSessionRedisOperations();
        this.idleTimeout = Objects.requireNonNull(idleTimeout, "idleTimeout must not be null");
        if (!SessionPolicy.IDLE_TIMEOUT.equals(idleTimeout)) {
            throw new IllegalArgumentException("idleTimeout must be exactly PT8H");
        }
        this.idleTimeoutSeconds = Math.toIntExact(idleTimeout.toSeconds());
        this.sessionKeyPrefix = normalizeNamespace(namespace) + SESSIONS_SUFFIX;
    }

    @Override
    public SelectiveSession createSession() {
        return new SelectiveSession(delegateRepository.createSession(), true);
    }

    @Override
    public void save(SelectiveSession session) {
        Objects.requireNonNull(session, "session must not be null");
        synchronized (session) {
            PendingSave pending = session.pendingSave();
            if (pending.isNoOp()) {
                session.saved(pending, null);
                return;
            }
            validateTemporalDelta(pending);

            for (int attempt = 1; attempt <= MAX_TRANSACTION_ATTEMPTS; attempt++) {
                TransactionResult result = redisOperations.execute(
                        new SessionCallback<TransactionResult>() {
                            @Override
                            public <K, V> TransactionResult execute(RedisOperations<K, V> operations)
                                    throws DataAccessException {
                                @SuppressWarnings("unchecked")
                                RedisOperations<String, Object> typedOperations =
                                        (RedisOperations<String, Object>)
                                                (RedisOperations<?, ?>) operations;
                                return saveOnce(typedOperations, pending);
                            }
                        });
                if (result != null && result.committed()) {
                    session.saved(pending, result.effectiveLastAccessedTime());
                    return;
                }
            }
            throw new IllegalStateException(
                    "Redis session update conflicted after bounded retries");
        }
    }

    @Override
    public SelectiveSession findById(String id) {
        Session found = delegateRepository.findById(id);
        return found == null ? null : new SelectiveSession(found, false);
    }

    @Override
    public void deleteById(String id) {
        Objects.requireNonNull(id, "id must not be null");
        String originalId = pendingOriginalIds.remove(id);
        if (originalId == null || originalId.equals(id)) {
            redisOperations.delete(sessionKey(id));
            return;
        }
        redisOperations.delete(List.of(sessionKey(originalId), sessionKey(id)));
    }

    private TransactionResult saveOnce(
            RedisOperations<String, Object> operations, PendingSave pending) {
        String oldKey = sessionKey(pending.originalId());
        String currentKey = sessionKey(pending.currentId());
        Collection<String> watchedKeys = oldKey.equals(currentKey)
                ? List.of(oldKey)
                : List.of(oldKey, currentKey);
        boolean transactionStarted = false;

        try {
            operations.watch(watchedKeys);
            HashOperations<String, Object, Object> hashes = operations.opsForHash();

            if (pending.newSession()) {
                if (Boolean.TRUE.equals(operations.hasKey(currentKey))) {
                    throw new IllegalStateException("Generated Redis session id already exists");
                }
                Instant effective = maximum(
                        pending.initialLastAccessedTime(), pending.approvedActivity());
                Map<Object, Object> fields = new LinkedHashMap<>();
                fields.put(CREATION_TIME_FIELD, pending.creationTime().toEpochMilli());
                fields.put(MAX_INACTIVE_INTERVAL_FIELD, idleTimeoutSeconds);
                fields.put(LAST_ACCESSED_TIME_FIELD, effective.toEpochMilli());
                putAllAttributes(fields, pending.completeAttributes());
                normalizeTemporalFields(fields, effective, pending.hasLifecycleState());

                operations.multi();
                transactionStarted = true;
                hashes.putAll(currentKey, fields);
                operations.expireAt(currentKey, expiryFor(effective));
                hashes.hasKey(currentKey, CREATION_TIME_FIELD);
                List<Object> executed = operations.exec();
                transactionStarted = false;
                return transactionResult(executed, effective);
            }

            if (!Boolean.TRUE.equals(operations.hasKey(oldKey))) {
                throw new IllegalStateException("Redis session was invalidated");
            }
            if (!oldKey.equals(currentKey)
                    && Boolean.TRUE.equals(operations.hasKey(currentKey))) {
                throw new IllegalStateException("Replacement Redis session id already exists");
            }

            Map<Object, Object> persisted = hashes.entries(oldKey);
            Instant persistedLastAccessed = requiredMillisInstant(
                    persisted.get(LAST_ACCESSED_TIME_FIELD), LAST_ACCESSED_TIME_FIELD);
            int persistedTimeout = requiredNumber(
                            persisted.get(MAX_INACTIVE_INTERVAL_FIELD),
                            MAX_INACTIVE_INTERVAL_FIELD)
                    .intValue();
            if (persistedTimeout != idleTimeoutSeconds) {
                throw new IllegalStateException("Redis session idle timeout drifted from PT8H");
            }
            Instant persistedLastValid = readLastValidActivity(persisted);
            Instant baseline = maximum(persistedLastAccessed, persistedLastValid);
            Instant effective = maximum(baseline, pending.approvedActivity());
            if (pending.approvedActivity() != null
                    && !pending.approvedActivity().isBefore(baseline.plus(idleTimeout))) {
                throw new IllegalStateException("Expired Redis session cannot be renewed");
            }

            Map<Object, Object> updates = attributeUpdates(pending.attributeUpdates());
            Set<Object> removals = attributeFields(pending.removedAttributes());
            removeTemporalFields(updates, removals);
            if (pending.approvedActivity() != null) {
                updates.put(LAST_ACCESSED_TIME_FIELD, effective.toEpochMilli());
                updates.put(
                        sessionAttributeField(LAST_VALID_EPOCH_SECOND_ATTRIBUTE),
                        effective.getEpochSecond());
                updates.put(
                        sessionAttributeField(LAST_VALID_NANO_ATTRIBUTE), effective.getNano());
            }

            operations.multi();
            transactionStarted = true;
            if (!oldKey.equals(currentKey)) {
                operations.rename(oldKey, currentKey);
            }
            if (!removals.isEmpty()) {
                hashes.delete(currentKey, removals.toArray());
            }
            if (!updates.isEmpty()) {
                hashes.putAll(currentKey, updates);
            }
            if (pending.approvedActivity() != null) {
                operations.expireAt(currentKey, expiryFor(effective));
            }
            hashes.hasKey(currentKey, CREATION_TIME_FIELD);
            List<Object> executed = operations.exec();
            transactionStarted = false;
            return transactionResult(executed, effective);
        } catch (RuntimeException failure) {
            cleanupTransaction(operations, transactionStarted, failure);
            throw failure;
        }
    }

    private static TransactionResult transactionResult(
            List<Object> executed, Instant effectiveLastAccessedTime) {
        if (executed == null || executed.isEmpty()) {
            return TransactionResult.conflict();
        }
        for (Object result : executed) {
            if (result instanceof Throwable throwable) {
                throw new IllegalStateException("Redis session transaction failed", throwable);
            }
        }
        if (!Boolean.TRUE.equals(executed.getLast())) {
            throw new IllegalStateException(
                    "Redis session transaction did not materialize the expected key");
        }
        return TransactionResult.committed(effectiveLastAccessedTime);
    }

    private static void cleanupTransaction(
            RedisOperations<String, Object> operations,
            boolean transactionStarted,
            RuntimeException failure) {
        try {
            if (transactionStarted) {
                operations.discard();
            } else {
                operations.unwatch();
            }
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private void validateTemporalDelta(PendingSave pending) {
        boolean changesTemporalState = pending.attributeUpdates().containsKey(
                        LAST_VALID_EPOCH_SECOND_ATTRIBUTE)
                || pending.attributeUpdates().containsKey(LAST_VALID_NANO_ATTRIBUTE)
                || pending.removedAttributes().contains(LAST_VALID_EPOCH_SECOND_ATTRIBUTE)
                || pending.removedAttributes().contains(LAST_VALID_NANO_ATTRIBUTE);
        if (changesTemporalState && pending.approvedActivity() == null) {
            throw new IllegalStateException(
                    "Temporal session state requires an approved activity");
        }
        if (pending.approvedActivity() != null && !pending.hasLifecycleState()) {
            throw new IllegalStateException("Approved activity requires complete lifecycle state");
        }
    }

    private static void normalizeTemporalFields(
            Map<Object, Object> fields, Instant effective, boolean lifecycleStatePresent) {
        fields.remove(sessionAttributeField(LAST_VALID_EPOCH_SECOND_ATTRIBUTE));
        fields.remove(sessionAttributeField(LAST_VALID_NANO_ATTRIBUTE));
        if (lifecycleStatePresent) {
            fields.put(
                    sessionAttributeField(LAST_VALID_EPOCH_SECOND_ATTRIBUTE),
                    effective.getEpochSecond());
            fields.put(sessionAttributeField(LAST_VALID_NANO_ATTRIBUTE), effective.getNano());
        }
    }

    private static void removeTemporalFields(Map<Object, Object> updates, Set<Object> removals) {
        Object epochSecondField = sessionAttributeField(LAST_VALID_EPOCH_SECOND_ATTRIBUTE);
        Object nanoField = sessionAttributeField(LAST_VALID_NANO_ATTRIBUTE);
        updates.remove(epochSecondField);
        updates.remove(nanoField);
        removals.remove(epochSecondField);
        removals.remove(nanoField);
    }

    private static Map<Object, Object> attributeUpdates(Map<String, Object> updates) {
        Map<Object, Object> fields = new LinkedHashMap<>();
        updates.forEach((name, value) -> fields.put(sessionAttributeField(name), value));
        return fields;
    }

    private static Set<Object> attributeFields(Collection<String> names) {
        Set<Object> fields = new LinkedHashSet<>();
        names.forEach(name -> fields.add(sessionAttributeField(name)));
        return fields;
    }

    private static void putAllAttributes(
            Map<Object, Object> fields, Map<String, Object> attributes) {
        attributes.forEach((name, value) -> fields.put(sessionAttributeField(name), value));
    }

    private static Instant readLastValidActivity(Map<Object, Object> persisted) {
        Object epochSecond =
                persisted.get(sessionAttributeField(LAST_VALID_EPOCH_SECOND_ATTRIBUTE));
        Object nano = persisted.get(sessionAttributeField(LAST_VALID_NANO_ATTRIBUTE));
        if (epochSecond == null && nano == null) {
            return null;
        }
        if (epochSecond == null || nano == null) {
            throw new IllegalStateException("Redis session lifecycle timestamp is partial");
        }
        long seconds = requiredNumber(epochSecond, LAST_VALID_EPOCH_SECOND_ATTRIBUTE).longValue();
        int nanos = requiredNumber(nano, LAST_VALID_NANO_ATTRIBUTE).intValue();
        if (nanos < 0 || nanos > 999_999_999) {
            throw new IllegalStateException("Redis session lifecycle nanoseconds are invalid");
        }
        return Instant.ofEpochSecond(seconds, nanos);
    }

    private static Instant requiredMillisInstant(Object value, String fieldName) {
        return Instant.ofEpochMilli(requiredNumber(value, fieldName).longValue());
    }

    private static Number requiredNumber(Object value, String fieldName) {
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalStateException("Redis session field is invalid: " + fieldName);
    }

    private static Instant maximum(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null || !right.isAfter(left)) {
            return left;
        }
        return right;
    }

    private Instant expiryFor(Instant effectiveLastActivity) {
        Instant flooredToMillis = Instant.ofEpochMilli(effectiveLastActivity.toEpochMilli());
        return flooredToMillis.plus(idleTimeout);
    }

    private String sessionKey(String id) {
        return sessionKeyPrefix + Objects.requireNonNull(id, "session id must not be null");
    }

    private static String sessionAttributeField(String attributeName) {
        return SESSION_ATTRIBUTE_PREFIX + attributeName;
    }

    private static String normalizeNamespace(String namespace) {
        String required = Objects.requireNonNull(namespace, "namespace must not be null").trim();
        if (required.isEmpty()) {
            throw new IllegalArgumentException("namespace must not be blank");
        }
        return required.endsWith(":") ? required : required + ":";
    }

    public final class SelectiveSession implements Session {

        private final Session delegate;
        private final Instant creationTime;
        private final Map<String, Object> attributeUpdates = new LinkedHashMap<>();
        private final Set<String> removedAttributes = new LinkedHashSet<>();
        private String originalId;
        private Instant persistedLastAccessedTime;
        private Instant pendingFrameworkTouch;
        private Instant approvedActivity;
        private boolean newSession;

        private SelectiveSession(Session delegate, boolean newSession) {
            this.delegate = Objects.requireNonNull(delegate, "session delegate must not be null");
            this.originalId = delegate.getId();
            this.creationTime = delegate.getCreationTime();
            this.persistedLastAccessedTime = delegate.getLastAccessedTime();
            this.newSession = newSession;
            if (!idleTimeout.equals(delegate.getMaxInactiveInterval())) {
                throw new IllegalStateException("Spring Session delegate is not configured for PT8H");
            }
        }

        @Override
        public String getId() {
            return delegate.getId();
        }

        @Override
        public String changeSessionId() {
            String previousId = delegate.getId();
            String changedId = delegate.changeSessionId();
            pendingOriginalIds.remove(previousId);
            pendingOriginalIds.put(changedId, originalId);
            return changedId;
        }

        @Override
        public <T> T getAttribute(String attributeName) {
            if (APPROVED_ACTIVITY_ATTRIBUTE.equals(attributeName)) {
                return null;
            }
            return delegate.getAttribute(attributeName);
        }

        @Override
        public Set<String> getAttributeNames() {
            Set<String> names = new LinkedHashSet<>(delegate.getAttributeNames());
            names.remove(APPROVED_ACTIVITY_ATTRIBUTE);
            return Set.copyOf(names);
        }

        @Override
        public void setAttribute(String attributeName, Object attributeValue) {
            Objects.requireNonNull(attributeName, "attributeName must not be null");
            if (APPROVED_ACTIVITY_ATTRIBUTE.equals(attributeName)) {
                if (!(attributeValue instanceof Instant instant)) {
                    throw new IllegalArgumentException(
                            "approved activity marker must contain an Instant");
                }
                approvedActivity = maximum(approvedActivity, instant);
                return;
            }
            if (attributeValue == null) {
                removeAttribute(attributeName);
                return;
            }
            delegate.setAttribute(attributeName, attributeValue);
            attributeUpdates.put(attributeName, attributeValue);
            removedAttributes.remove(attributeName);
        }

        @Override
        public void removeAttribute(String attributeName) {
            Objects.requireNonNull(attributeName, "attributeName must not be null");
            if (APPROVED_ACTIVITY_ATTRIBUTE.equals(attributeName)) {
                approvedActivity = null;
                return;
            }
            delegate.removeAttribute(attributeName);
            attributeUpdates.remove(attributeName);
            removedAttributes.add(attributeName);
        }

        @Override
        public Instant getCreationTime() {
            return creationTime;
        }

        @Override
        public void setLastAccessedTime(Instant lastAccessedTime) {
            pendingFrameworkTouch =
                    Objects.requireNonNull(lastAccessedTime, "lastAccessedTime must not be null");
        }

        @Override
        public Instant getLastAccessedTime() {
            return pendingFrameworkTouch == null
                    ? persistedLastAccessedTime
                    : pendingFrameworkTouch;
        }

        @Override
        public void setMaxInactiveInterval(Duration interval) {
            if (!idleTimeout.equals(interval)) {
                throw new IllegalArgumentException("session idle timeout must remain PT8H");
            }
        }

        @Override
        public Duration getMaxInactiveInterval() {
            return idleTimeout;
        }

        @Override
        public boolean isExpired() {
            return delegate.isExpired();
        }

        private PendingSave pendingSave() {
            Map<String, Object> completeAttributes = new LinkedHashMap<>();
            for (String name : delegate.getAttributeNames()) {
                if (!APPROVED_ACTIVITY_ATTRIBUTE.equals(name)) {
                    completeAttributes.put(name, delegate.getAttribute(name));
                }
            }
            return new PendingSave(
                    newSession,
                    originalId,
                    delegate.getId(),
                    creationTime,
                    persistedLastAccessedTime,
                    Map.copyOf(completeAttributes),
                    Map.copyOf(attributeUpdates),
                    Set.copyOf(removedAttributes),
                    approvedActivity);
        }

        private void saved(PendingSave pending, Instant effectiveLastAccessedTime) {
            pendingOriginalIds.remove(pending.currentId());
            originalId = pending.currentId();
            newSession = false;
            if (effectiveLastAccessedTime != null) {
                persistedLastAccessedTime = effectiveLastAccessedTime;
                if (pending.approvedActivity() != null) {
                    delegate.setAttribute(
                            LAST_VALID_EPOCH_SECOND_ATTRIBUTE,
                            effectiveLastAccessedTime.getEpochSecond());
                    delegate.setAttribute(
                            LAST_VALID_NANO_ATTRIBUTE, effectiveLastAccessedTime.getNano());
                }
            }
            pendingFrameworkTouch = null;
            approvedActivity = null;
            attributeUpdates.clear();
            removedAttributes.clear();
        }
    }

    private record PendingSave(
            boolean newSession,
            String originalId,
            String currentId,
            Instant creationTime,
            Instant initialLastAccessedTime,
            Map<String, Object> completeAttributes,
            Map<String, Object> attributeUpdates,
            Set<String> removedAttributes,
            Instant approvedActivity) {

        private boolean isNoOp() {
            return !newSession
                    && originalId.equals(currentId)
                    && attributeUpdates.isEmpty()
                    && removedAttributes.isEmpty()
                    && approvedActivity == null;
        }

        private boolean hasLifecycleState() {
            return completeAttributes.containsKey(LAST_VALID_EPOCH_SECOND_ATTRIBUTE)
                    && completeAttributes.containsKey(LAST_VALID_NANO_ATTRIBUTE);
        }
    }

    private record TransactionResult(boolean committed, Instant effectiveLastAccessedTime) {

        private static TransactionResult conflict() {
            return new TransactionResult(false, null);
        }

        private static TransactionResult committed(Instant effectiveLastAccessedTime) {
            return new TransactionResult(true, effectiveLastAccessedTime);
        }
    }
}
