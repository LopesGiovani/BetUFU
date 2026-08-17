package br.com.betufu.platform.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.encoder.JsonEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;

/**
 * Structured JSON encoder that ensures all messages, formatted text, and exception
 * stack traces are passed through {@link LogSanitizer#sanitize(String)} before being
 * serialized into JSON.
 *
 * <p>Used in {@code logback-spring.xml} for the {@code runtime | integrated} profile.
 */
public class SanitizingJsonEncoder extends JsonEncoder {

    @Override
    public byte[] encode(ILoggingEvent event) {
        if (event == null) {
            return super.encode(null);
        }
        return super.encode(new SanitizedLoggingEvent(event));
    }

    private static final class SanitizedLoggingEvent implements ILoggingEvent {
        private final ILoggingEvent delegate;

        SanitizedLoggingEvent(ILoggingEvent delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getMessage() {
            return LogSanitizer.sanitize(delegate.getMessage());
        }

        @Override
        public String getFormattedMessage() {
            return LogSanitizer.sanitize(delegate.getFormattedMessage());
        }

        @Override
        public IThrowableProxy getThrowableProxy() {
            IThrowableProxy original = delegate.getThrowableProxy();
            if (original == null) {
                return null;
            }
            return new SanitizedThrowableProxy(original);
        }

        @Override
        public String getThreadName() {
            return delegate.getThreadName();
        }

        @Override
        public Level getLevel() {
            return delegate.getLevel();
        }

        @Override
        public Object[] getArgumentArray() {
            Object[] original = delegate.getArgumentArray();
            if (original == null) {
                return null;
            }
            Object[] sanitized = new Object[original.length];
            for (int i = 0; i < original.length; i++) {
                sanitized[i] = sanitizeObject(original[i]);
            }
            return sanitized;
        }

        @Override
        public String getLoggerName() {
            return delegate.getLoggerName();
        }

        @Override
        public LoggerContextVO getLoggerContextVO() {
            return delegate.getLoggerContextVO();
        }

        @Override
        public StackTraceElement[] getCallerData() {
            return delegate.getCallerData();
        }

        @Override
        public boolean hasCallerData() {
            return delegate.hasCallerData();
        }

        @Override
        public Marker getMarker() {
            return delegate.getMarker();
        }

        @Override
        public List<Marker> getMarkerList() {
            return delegate.getMarkerList();
        }

        @Override
        public Map<String, String> getMDCPropertyMap() {
            return sanitizeMap(delegate.getMDCPropertyMap());
        }

        @Override
        public Map<String, String> getMdc() {
            return sanitizeMap(delegate.getMdc());
        }

        @Override
        public long getTimeStamp() {
            return delegate.getTimeStamp();
        }

        @Override
        public int getNanoseconds() {
            return delegate.getNanoseconds();
        }

        @Override
        public Instant getInstant() {
            return delegate.getInstant();
        }

        @Override
        public long getSequenceNumber() {
            return delegate.getSequenceNumber();
        }

        private static final java.util.regex.Pattern SENSITIVE_KEY = java.util.regex.Pattern.compile(
                "^(?:password|passwd|pwd|secret|client_secret|api[_-]?key|token|custom_token|auth|authorization|cookie|session)$",
                java.util.regex.Pattern.CASE_INSENSITIVE);

        @Override
        public List<KeyValuePair> getKeyValuePairs() {
            List<KeyValuePair> original = delegate.getKeyValuePairs();
            if (original == null) {
                return null;
            }
            return original.stream()
                    .map(kv -> kv == null
                            ? null
                            : new KeyValuePair(
                                    LogSanitizer.sanitize(kv.key),
                                    sanitizeKeyValue(kv.key, kv.value)))
                    .toList();
        }

        @Override
        public void prepareForDeferredProcessing() {
            delegate.prepareForDeferredProcessing();
        }

        private static Object sanitizeKeyValue(String key, Object value) {
            if (value == null) {
                return null;
            }
            if (key != null && SENSITIVE_KEY.matcher(key).matches()) {
                return "[REDACTED]";
            }
            return sanitizeObject(value);
        }

        private static Object sanitizeObject(Object obj) {
            if (obj == null) {
                return null;
            }
            if (obj instanceof CharSequence cs) {
                return LogSanitizer.sanitize(cs.toString());
            }
            return obj;
        }

        private static Map<String, String> sanitizeMap(Map<String, String> original) {
            if (original == null) {
                return null;
            }
            Map<String, String> sanitized = new java.util.LinkedHashMap<>(original.size());
            original.forEach((k, v) -> {
                String sanitizedKey = LogSanitizer.sanitize(k);
                Object val = sanitizeKeyValue(k, v);
                sanitized.put(sanitizedKey, val != null ? val.toString() : null);
            });
            return sanitized;
        }
    }

    private static final class SanitizedThrowableProxy implements IThrowableProxy {
        private final IThrowableProxy delegate;

        SanitizedThrowableProxy(IThrowableProxy delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getMessage() {
            return LogSanitizer.sanitize(delegate.getMessage());
        }

        @Override
        public String getClassName() {
            return delegate.getClassName();
        }

        @Override
        public StackTraceElementProxy[] getStackTraceElementProxyArray() {
            return delegate.getStackTraceElementProxyArray();
        }

        @Override
        public int getCommonFrames() {
            return delegate.getCommonFrames();
        }

        @Override
        public IThrowableProxy getCause() {
            IThrowableProxy cause = delegate.getCause();
            return cause != null ? new SanitizedThrowableProxy(cause) : null;
        }

        @Override
        public IThrowableProxy[] getSuppressed() {
            IThrowableProxy[] suppressed = delegate.getSuppressed();
            if (suppressed == null) {
                return null;
            }
            IThrowableProxy[] sanitized = new IThrowableProxy[suppressed.length];
            for (int i = 0; i < suppressed.length; i++) {
                sanitized[i] = suppressed[i] != null ? new SanitizedThrowableProxy(suppressed[i]) : null;
            }
            return sanitized;
        }

        @Override
        public boolean isCyclic() {
            return delegate.isCyclic();
        }
    }
}
