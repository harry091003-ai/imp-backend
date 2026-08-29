package com.project.web.exception;

import org.springframework.http.HttpStatus;

/**
 * The caller is not authenticated at all, or their credentials/token are not valid.
 * Maps to HTTP 401.
 *
 * <p>Examples: bad email/password on login, a JWT whose tokenVersion no longer matches,
 * a locked account.</p>
 *
 * <p>401 means "who are you?". 403 ({@link ForbiddenException}) means "I know who you are,
 * and no".</p>
 */
public class UnauthorizedException extends ApiException {

    private static final String DEFAULT_CODE = "UNAUTHORIZED";

    public UnauthorizedException(String message) {
        super(DEFAULT_CODE, message, HttpStatus.UNAUTHORIZED);
    }

    public UnauthorizedException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.UNAUTHORIZED);
    }
}
