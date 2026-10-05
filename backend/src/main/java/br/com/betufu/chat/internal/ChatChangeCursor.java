package br.com.betufu.chat.internal;

import java.time.Instant;
import java.util.UUID;

/** Sequence is advisory: PostgreSQL identity allocation does not order transaction commits. */
public record ChatChangeCursor(
    String kind,
    UUID eventId,
    long changeCursor,
    boolean resyncRequired,
    String state,
    Instant deadline) {}
