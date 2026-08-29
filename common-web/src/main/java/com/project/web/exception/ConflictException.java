package com.project.web.exception;

import org.springframework.http.HttpStatus;

/**
 * The request is well-formed and allowed, but it clashes with existing state. Maps to HTTP 409.
 *
 * <p>Examples: signing up with a username that is already taken, an incident that is already in
 * the target status, re-processing an event that was already processed.</p>
 */
public class ConflictException extends ApiException {

    private static final String DEFAULT_CODE = "CONFLICT";

    public ConflictException(String message) {
        super(DEFAULT_CODE, message, HttpStatus.CONFLICT);
    }

    public ConflictException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.CONFLICT);
    }
}
