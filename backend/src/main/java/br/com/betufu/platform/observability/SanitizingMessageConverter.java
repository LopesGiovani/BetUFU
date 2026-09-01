package br.com.betufu.platform.observability;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback {@link MessageConverter} that passes every formatted message through {@link
 * LogSanitizer#sanitize(String)} before it is written to any appender.
 *
 * <p>Registered in {@code logback-spring.xml} via:
 *
 * <pre>{@code
 * <conversionRule conversionWord="msg"
 *                 converterClass="br.com.betufu.platform.observability.SanitizingMessageConverter"/>
 * }</pre>
 *
 * <p>This guarantees that all logs emitted via SLF4J (whether in application code, third-party
 * libraries, or exception handlers) are automatically sanitized against tokens, cookies, secrets,
 * credentials, and Unicode control characters without requiring manual invocation by the developer.
 */
public class SanitizingMessageConverter extends MessageConverter {

  @Override
  public String convert(ILoggingEvent event) {
    String original = super.convert(event);
    return LogSanitizer.sanitize(original);
  }
}
