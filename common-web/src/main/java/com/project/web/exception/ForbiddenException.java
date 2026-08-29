package com.project.web.exception;

import org.springframework.http.HttpStatus;

/**
 * The caller is authenticated, but is not allowed to do this. Maps to HTTP 403.
 *
 * <p>Examples: an engineer trying to read an incident that is not theirs, an inactive user
 * trying to create an incident, a supervisor updating an incident outside their team.</p>
 *
 * <p>Do not use this for "not logged in" - that is {@link UnauthorizedException} (401).</p>
 */
public class ForbiddenException extends ApiException {

    private static final String DEFAULT_CODE = "FORBIDDEN";

    public ForbiddenException(String message) {
        super(DEFAULT_CODE, message, HttpStatus.FORBIDDEN);
    }

    public ForbiddenException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.FORBIDDEN);
    }
}
