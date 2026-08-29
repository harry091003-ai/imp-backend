package com.project.web.exception;

import org.springframework.http.HttpStatus;

/**
 * The requested thing does not exist. Maps to HTTP 404.
 *
 * <p>Examples: unknown incident id, unknown user id, a role row that was never seeded.</p>
 *
 * <p>Use the two-argument constructor when you want a specific code the client can branch on:
 * {@code new NotFoundException("INCIDENT_NOT_FOUND", "Incident not found: " + id)}.</p>
 */
public class NotFoundException extends ApiException {

    private static final String DEFAULT_CODE = "NOT_FOUND";

    public NotFoundException(String message) {
        super(DEFAULT_CODE, message, HttpStatus.NOT_FOUND);
    }

    public NotFoundException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.NOT_FOUND);
    }
}
