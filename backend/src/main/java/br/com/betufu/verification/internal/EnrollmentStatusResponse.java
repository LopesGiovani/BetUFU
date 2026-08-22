package br.com.betufu.verification.internal;

import java.time.Instant;

public record EnrollmentStatusResponse(
        String status,
        Instant createdAt) {
}
