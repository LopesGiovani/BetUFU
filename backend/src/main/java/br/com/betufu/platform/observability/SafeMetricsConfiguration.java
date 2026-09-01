package br.com.betufu.platform.observability;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enforces safe metrics collection and prevents high-cardinality or sensitive data exposure in
 * {@link io.micrometer.core.instrument.MeterRegistry}.
 */
@Configuration(proxyBeanMethods = false)
public class SafeMetricsConfiguration {

  private static final Set<String> HIGH_CARDINALITY_OR_SENSITIVE_KEYS =
      Set.of(
          "correlationid",
          "userid",
          "user",
          "username",
          "sub",
          "subject",
          "issuer",
          "iss",
          "email",
          "token",
          "password",
          "secret",
          "client_secret",
          "authorization",
          "auth",
          "cookie",
          "session",
          "sessionid");

  private static final int MAX_CARDINALITY_TAGS = 100;

  /**
   * MeterFilter that strips sensitive / high-cardinality tags and sanitizes remaining tag values.
   */
  @Bean
  public MeterFilter sanitizeAndFilterHighCardinalityTags() {
    return new MeterFilter() {
      @Override
      public Meter.Id map(Meter.Id id) {
        List<Tag> sanitizedTags = new ArrayList<>();
        for (Tag tag : id.getTags()) {
          String lowerKey = tag.getKey().toLowerCase(java.util.Locale.ROOT);
          if (HIGH_CARDINALITY_OR_SENSITIVE_KEYS.contains(lowerKey)) {
            continue;
          }
          String sanitizedValue = LogSanitizer.sanitize(tag.getValue());
          sanitizedTags.add(Tag.of(tag.getKey(), sanitizedValue != null ? sanitizedValue : ""));
        }
        return id.replaceTags(sanitizedTags);
      }
    };
  }

  /** Prevents unbounded cardinality DoS on URI tag values for HTTP server requests. */
  @Bean
  public MeterFilter limitHttpServerRequestsUriCardinality() {
    return MeterFilter.maximumAllowableTags(
        "http.server.requests", "uri", MAX_CARDINALITY_TAGS, MeterFilter.deny());
  }
}
