package com.project.web.handler;

import com.project.web.dto.ErrorResponse;
import com.project.web.exception.ApiException;
import com.project.web.filter.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The single place that turns any exception thrown by any controller into an HTTP response.
 *
 * <p>You never call this class. Spring routes exceptions to it automatically: when an exception
 * escapes a controller method, Spring looks for an {@code @ExceptionHandler} whose parameter type
 * matches (most specific match wins) and uses its return value as the response.</p>
 *
 * <p>Because this lives in a shared module, all services get identical error behaviour without
 * copy-pasting anything.</p>
 *
 * <h2>The safety rule</h2>
 * <p>Handlers for KNOWN failures pass their message straight through - those messages were
 * written by us, for the client. The catch-all handler for UNKNOWN failures deliberately does
 * NOT: a raw {@code NullPointerException} message or a SQL error can leak table names, file
 * paths and library versions to whoever is calling the API. The real detail is logged on the
 * server; the client only gets a generic sentence and a traceId to quote back to us.</p>
 *
 * <h2>Note for the reactive APIGateway</h2>
 * <p>{@code @RestControllerAdvice} is a Spring MVC mechanism. It does nothing at all in a
 * WebFlux application, which is why APIGateway does not depend on this module and instead
 * hand-builds a matching JSON shape in its own filter.</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Shown to clients for any unexpected server-side failure. Deliberately says nothing useful. */
    private static final String GENERIC_SERVER_MESSAGE =
            "An unexpected error occurred. Please quote the traceId when reporting this.";

    // ---------------------------------------------------------------------------------
    // 1. Our own business exceptions - the common case.
    //    One handler covers all five subclasses, because each subclass already carries its
    //    own status and code. Adding a sixth exception type needs no change here.
    // ---------------------------------------------------------------------------------
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex,
                                                            HttpServletRequest request) {
        HttpStatus status = ex.getStatus();

        // 4xx is somebody using the API wrong - worth a WARN, not worth a stack trace.
        // 5xx from our own code is a real problem - log it fully.
        if (status.is5xxServerError()) {
            log.error("[{}] {} {} -> {} {}: {}", traceId(request), request.getMethod(),
                    request.getRequestURI(), status.value(), ex.getErrorCode(), ex.getMessage(), ex);
        } else {
            log.warn("[{}] {} {} -> {} {}: {}", traceId(request), request.getMethod(),
                    request.getRequestURI(), status.value(), ex.getErrorCode(), ex.getMessage());
        }

        return build(status, ex.getErrorCode(), ex.getMessage(), request, null);
    }

    // ---------------------------------------------------------------------------------
    // 2. @Valid failed on a request body.
    //    Spring throws this before the controller method body ever runs.
    // MethodArgumentNotValidException → validating the request body (@RequestBody)
    // ---------------------------------------------------------------------------------
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {

        List<ErrorResponse.FieldValidationError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldError)
                .collect(Collectors.toList());

        log.warn("[{}] {} {} -> 400 VALIDATION_FAILED: {} field(s) rejected", traceId(request),
                request.getMethod(), request.getRequestURI(), fieldErrors.size());

        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "One or more fields are invalid.", request, fieldErrors);
    }

    // ---------------------------------------------------------------------------------
    // 3. Validation on a method parameter (@Validated on the class + constraints on params).
    // ConstraintViolationException → validating method parameters (@RequestParam, @PathVariable, etc.)
    // or direct constraint validation
    // ---------------------------------------------------------------------------------
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                   HttpServletRequest request) {

        List<ErrorResponse.FieldValidationError> fieldErrors = ex.getConstraintViolations()
                .stream()
                .map(this::toFieldError)
                .collect(Collectors.toList());

        log.warn("[{}] {} {} -> 400 VALIDATION_FAILED: {}", traceId(request),
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "One or more parameters are invalid.", request, fieldErrors);
    }

    // ---------------------------------------------------------------------------------
    // 4. A path variable or query param could not be converted to the declared type.
    //    This is the very common "someone put a non-UUID in /incidents/{id}" case, which
    //    used to produce a confusing 500.
    // ---------------------------------------------------------------------------------
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {

        String required = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "the expected type";
        String message = "Parameter '" + ex.getName() + "' is not a valid " + required + ".";

        log.warn("[{}] {} {} -> 400 INVALID_PARAMETER: {}", traceId(request),
                request.getMethod(), request.getRequestURI(), message);

        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", message, request, null);
    }

    // ---------------------------------------------------------------------------------
    // 5. The JSON body could not be parsed at all - malformed JSON, or a value that does not
    //    match an enum (e.g. "severity": "URGENT" when only LOW/MEDIUM/HIGH/CRITICAL exist).
    //    The raw Jackson message names Java classes, so it is not passed to the client.
    // ---------------------------------------------------------------------------------
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                              HttpServletRequest request) {

        log.warn("[{}] {} {} -> 400 MALFORMED_REQUEST_BODY: {}", traceId(request),
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST_BODY",
                "Request body is missing or not valid JSON, or a field has an unsupported value.",
                request, null);
    }

    // ---------------------------------------------------------------------------------
    // 6. A required query parameter was not sent.
    // ---------------------------------------------------------------------------------
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex,
                                                            HttpServletRequest request) {

        String message = "Required parameter '" + ex.getParameterName() + "' is missing.";

        log.warn("[{}] {} {} -> 400 MISSING_PARAMETER: {}", traceId(request),
                request.getMethod(), request.getRequestURI(), message);

        return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", message, request, null);
    }

    // ---------------------------------------------------------------------------------
    // 7. A database constraint rejected the write - almost always a duplicate key.
    //    The raw message contains SQL, index names and table names: never forwarded.
    // ---------------------------------------------------------------------------------
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest request) {

        log.warn("[{}] {} {} -> 409 DATA_INTEGRITY_VIOLATION: {}", traceId(request),
                request.getMethod(), request.getRequestURI(), ex.getMostSpecificCause().getMessage());

        return build(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
                "This operation conflicts with existing data.", request, null);
    }

    // ---------------------------------------------------------------------------------
    // 8. Spring Security refused the call - typically a @PreAuthorize that did not pass.
    // ---------------------------------------------------------------------------------
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                            HttpServletRequest request) {

        log.warn("[{}] {} {} -> 403 ACCESS_DENIED", traceId(request),
                request.getMethod(), request.getRequestURI());

        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "You do not have permission to perform this action.", request, null);
    }

    // ---------------------------------------------------------------------------------
    // 9. Authentication itself failed.
    // ---------------------------------------------------------------------------------
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex,
                                                              HttpServletRequest request) {

        log.warn("[{}] {} {} -> 401 UNAUTHORIZED: {}", traceId(request),
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "Authentication is required or the provided credentials are not valid.",
                request, null);
    }

    // ---------------------------------------------------------------------------------
    // 10. Anything we did not anticipate. This is the safety net AND the security boundary.
    //     Full detail to the log, nothing but a traceId to the client.
    // ---------------------------------------------------------------------------------
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {

        log.error("[{}] {} {} -> 500 INTERNAL_ERROR (unhandled {})", traceId(request),
                request.getMethod(), request.getRequestURI(), ex.getClass().getName(), ex);

        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                GENERIC_SERVER_MESSAGE, request, null);
    }

    // ---------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------

    private ResponseEntity<ErrorResponse> build(HttpStatus status,
                                                String errorCode,
                                                String message,
                                                HttpServletRequest request,
                                                List<ErrorResponse.FieldValidationError> fieldErrors) {

        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(errorCode)
                .message(message)
                .path(request != null ? request.getRequestURI() : null)
                .traceId(traceId(request))
                // null is omitted from the JSON entirely (see @JsonInclude on ErrorResponse)
                .fieldErrors(fieldErrors == null || fieldErrors.isEmpty() ? null : fieldErrors)
                .build();

        return ResponseEntity.status(status).body(body);
    }

    private String traceId(HttpServletRequest request) {
        return TraceIdFilter.currentTraceId(request);
    }

    private ErrorResponse.FieldValidationError toFieldError(FieldError fieldError) {
        return ErrorResponse.FieldValidationError.builder()
                .field(fieldError.getField())
                .message(fieldError.getDefaultMessage())
                .rejectedValue(safeRejectedValue(fieldError.getField(), fieldError.getRejectedValue()))
                .build();
    }
//Spring gives you its own validation-error object → these methods convert it into your application's
// own FieldValidationError object.
    private ErrorResponse.FieldValidationError toFieldError(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : null;
        return ErrorResponse.FieldValidationError.builder()
                .field(path)
                .message(violation.getMessage())
                .rejectedValue(safeRejectedValue(path, violation.getInvalidValue()))
                .build();
    }

    /**
     * Echoing back what the client sent makes validation errors far easier to debug - except
     * for secrets.
     * this is basically used while send the rejectedValue to user back in the response body. If the rejectedValue is a password or secret, it will be redacted which help to avoid sensitive information leakage.
     * as we should Never reflect a password (or anything that looks like one) into a response body that may end up in a log or a browser console.
     */
    private String safeRejectedValue(String field, Object rejectedValue) {
        if (rejectedValue == null) {
            return null;
        }
        if (field != null) {
            String lower = field.toLowerCase();
            if (lower.contains("password") || lower.contains("secret") || lower.contains("token")) {
                return "[REDACTED]";
            }
        }
        String asString = String.valueOf(rejectedValue);
        return asString.length() > 200 ? asString.substring(0, 200) + "..." : asString;
    }
}
