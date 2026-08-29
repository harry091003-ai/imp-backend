package com.project.web.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for every "expected" business failure in the platform.
 *
 * <p>The whole point of this class: the EXCEPTION TYPE carries the meaning, so nobody has to
 * read a message string to work out what happened. Before this existed, every failure in the
 * codebase was {@code throw new RuntimeException("Incident not found")} - which Spring turned
 * into a generic HTML 500 page. The client could not tell "you sent a bad id" apart from
 * "our database is down".</p>
 *
 * <p>Each subclass fixes its own HTTP status, so the status lives in exactly one place instead
 * of being re-decided at every throw site. The {@code errorCode} is chosen at the throw site,
 * because that is the only place that knows the specific thing that failed
 * (e.g. {@code INCIDENT_NOT_FOUND} vs {@code USER_NOT_FOUND} - both are 404).</p>
 *
 * <p>Anything that is NOT one of these subclasses is treated as an unexpected bug by
 * {@code GlobalExceptionHandler} and is deliberately hidden from the client (500 + traceId).</p>
 */
public abstract class ApiException extends RuntimeException {

    /** Short, stable, machine-readable code. Fills the "error" field of the JSON response. */
    private final String errorCode;

    /** HTTP status this failure maps to. Fixed by the subclass, never by the caller. */
    private final HttpStatus status;

    protected ApiException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    protected ApiException(String errorCode, String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
