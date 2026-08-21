package br.com.betufu.platform.api;

import java.util.Objects;
import org.springframework.http.HttpStatus;

/**
 * A business rejection that can safely cross a module boundary into the HTTP error handler.
 *
 * <p>The exception carries only a stable error code and an HTTP status. Its message is the
 * non-sensitive public code, so domain input and infrastructure details never travel through an
 * exception message.
 */
public final class ApiProblemException extends RuntimeException {

    private final HttpStatus status;
    private final ApiErrorCode errorCode;

    public ApiProblemException(HttpStatus status, ApiErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode, "errorCode must not be null").code());
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.errorCode = errorCode;
    }

    public HttpStatus status() {
        return status;
    }

    public ApiErrorCode errorCode() {
        return errorCode;
    }
}
