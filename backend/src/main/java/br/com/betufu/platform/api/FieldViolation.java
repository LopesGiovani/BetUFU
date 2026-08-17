package br.com.betufu.platform.api;

import java.util.Objects;

/**
 * A single field-level validation violation, part of the public error contract.
 *
 * <p>Sorted by {@code field} name to produce deterministic output regardless of
 * validator evaluation order — per D-15.
 *
 * <p>The rejected value is deliberately absent. The frontend already holds the submitted
 * form data and can match fields by name; echoing the value back would risk leaking
 * sensitive input (e.g., a password field's content) in the error response.
 *
 * @param field   the path to the invalid field (e.g., "email")
 * @param message explanation of what is wrong, in Portuguese when possible
 */
public record FieldViolation(String field, String message) implements Comparable<FieldViolation> {

    public FieldViolation {
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    @Override
    public int compareTo(FieldViolation other) {
        return this.field.compareTo(other.field);
    }
}
