package com.project.web.exception;

import org.springframework.http.HttpStatus;

/**
 * The request itself is wrong - malformed, or it fails a business rule about its own content.
 * Maps to HTTP 400.
 *
 * <p>Examples: trying to assign an incident to a user who is not an engineer, an unsupported
 * filter value, a field combination that cannot make sense.</p>
 *
 * <p>Rule of thumb vs {@link ConflictException}: use this when the CLIENT should change what it
 * sent. Use ConflictException when what the client sent was reasonable but the current server
 * state refuses it.</p>
 *
 * <p>Note: failures of {@code @Valid} on a request body are handled separately and
 * automatically by {@code GlobalExceptionHandler} - you do not throw this for those.</p>
 */
public class ValidationException extends ApiException {

    private static final String DEFAULT_CODE = "VALIDATION_FAILED";

    public ValidationException(String message) {
        super(DEFAULT_CODE, message, HttpStatus.BAD_REQUEST);
    }

    public ValidationException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.BAD_REQUEST);
    }
}
