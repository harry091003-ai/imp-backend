package com.project.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * The ONE error body every service returns. Same field names, same order, every time -
 * whether it came from AuthService, IncidentService or ReportingService.
 *
 * <p>That sameness is the entire point: a client writes its error handling once
 * ("read .status, show .message, log .traceId") and it works against all of them.</p>
 *
 * <pre>
 * {
 *   "timestamp": "2026-08-28T10:15:00Z",
 *   "status": 404,
 *   "error": "INCIDENT_NOT_FOUND",
 *   "message": "Incident not found: 3f2a...",
 *   "path": "/incidents/3f2a...",
 *   "traceId": "a1b2c3d4"
 * }
 * </pre>
 *
 * <p>{@code fieldErrors} is the only optional field. It is omitted entirely (not null, absent)
 * for everything except {@code @Valid} failures, so the six-field contract above stays stable.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /** When the error was produced, UTC. Serialized as an ISO-8601 string, e.g. 2026-08-28T10:15:00Z */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    /** HTTP status code, repeated in the body so clients that only log the body still see it. */
    private Integer status;

    /** Stable machine-readable code, e.g. INCIDENT_NOT_FOUND. Safe to branch on in a client. */
    private String error;

    /** Human-readable explanation. Safe to show a user. Never contains internal detail. */
    private String message;

    /** The request path that failed, e.g. /incidents/3f2a... */
    private String path;

    /** Correlation id, also written to the server log line for this request. */
    private String traceId;

    /** Present only for @Valid failures: which fields were rejected and why. */
    private List<FieldValidationError> fieldErrors;

    /**
     * One rejected field. Kept as a nested type because it only ever appears inside an
     * ErrorResponse and has no meaning on its own.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldValidationError {

        /** Name of the rejected field, e.g. "title". */
        private String field;

        /** Why it was rejected, e.g. "Title is required". */
        private String message;

        /** What the client actually sent, as a string. Null for sensitive fields. */
        private String rejectedValue;
    }
}
