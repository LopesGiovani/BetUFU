package br.com.betufu.platform.observability;

import ch.qos.logback.classic.pattern.ExtendedThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback {@link ExtendedThrowableProxyConverter} that passes rendered stack traces and exception
 * messages through {@link LogSanitizer#sanitize(String)} before output.
 *
 * <p>Registered in {@code logback-spring.xml} via:
 *
 * <pre>{@code
 * <conversionRule conversionWord="wEx"
 *                 converterClass="br.com.betufu.platform.observability.SanitizingExtendedThrowableProxyConverter"/>
 * }</pre>
 *
 * <p>Ensures that exceptions logged with {@code logger.error("message", ex)} never leak tokens,
 * connection strings, passwords, or cookies through exception messages or stack traces in console
 * outputs.
 */
public class SanitizingExtendedThrowableProxyConverter extends ExtendedThrowableProxyConverter {

  @Override
  public String convert(ILoggingEvent event) {
    String original = super.convert(event);
    return LogSanitizer.sanitize(original);
  }
}
