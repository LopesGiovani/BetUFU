package br.com.betufu.platform.session.passwordreset;

import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Page;

/** Bounded, stable page returned by the administrative FAILED-event listing. */
public record PasswordResetEventRecoveryPageResponse(
    List<PasswordResetEventRecoveryResponse> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext) {
  public PasswordResetEventRecoveryPageResponse {
    items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
  }

  public static PasswordResetEventRecoveryPageResponse fromPage(
      Page<PasswordResetEventInboxEntity> source) {
    Objects.requireNonNull(source, "source must not be null");
    return new PasswordResetEventRecoveryPageResponse(
        source.getContent().stream().map(PasswordResetEventRecoveryResponse::fromEntity).toList(),
        source.getNumber(),
        source.getSize(),
        source.getTotalElements(),
        source.getTotalPages(),
        source.hasNext());
  }
}
