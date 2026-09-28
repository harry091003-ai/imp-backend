package com.example.APIGateway.handler;

import com.example.APIGateway.utils.FailureDescription;
import com.example.APIGateway.utils.GatewayErrorWriter;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.logging.Level;
import java.util.logging.Logger;

// The gateway's last safety net for errors (added 2026-09-25, with the JwtGatewayFilter fix).
//
// Any error that no filter handled itself ends up here. Mostly: the gateway could not reach
// the service it was forwarding to (the service is down, not registered, or too slow).
//
// Why it is needed: before the fix, JwtGatewayFilter caught these errors itself and wrongly
// called every one "AUTH_SERVICE_UNAVAILABLE". Now they come here instead, and get:
//   - an honest status (503 service down, 504 too slow, 500 our own bug)
//   - our normal JSON error shape, with the traceId (Spring's default handler has neither)
//
// Same safety rule as common-web's GlobalExceptionHandler: the caller gets a short fixed
// message. The real error (it can name internal services) goes ONLY to the log.
@Component
@Order(-2)   // runs before Spring Boot's own default error handler, which is -1
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger logger = Logger.getLogger(GatewayExceptionHandler.class.getName());

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // Part of the reply has already been sent (the service died halfway through).
        // Too late to change the status or write a body - pass the error on.
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = statusFor(ex);
        String traceId = GatewayErrorWriter.resolveTraceId(exchange);
        String where = exchange.getRequest().getMethod() + " " + exchange.getRequest().getURI().getPath();

        if (status.is5xxServerError()) {
            // FailureDescription: the error type + cause chain, never just "null".
            logger.log(Level.SEVERE, "[" + traceId + "] " + where + " -> " + status.value() + " "
                    + status.name() + ": " + FailureDescription.of(ex), ex);
        } else {
            // 4xx = the caller's mistake (e.g. a path with no route). One short line, no stack trace.
            logger.warning("[" + traceId + "] " + where + " -> " + status.value() + " " + status.name());
        }

        return GatewayErrorWriter.write(exchange, status, status.name(), messageFor(status));
    }

    // Which status to answer with.
    private HttpStatus statusFor(Throwable ex) {
        // Errors that already carry a status. The gateway uses these for:
        //   503 "no instance of that service is registered"
        //   504 "the service did not answer in time"
        //   404 "no route matches this path"
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
            return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        }
        // The service is registered, but nothing is listening (it crashed, or is restarting).
        if (ex instanceof ConnectException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        // Anything else is unexpected - treat it as our own bug.
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    // What to tell the caller. Short and fixed - never the raw error text.
    private String messageFor(HttpStatus status) {
        return switch (status) {
            case SERVICE_UNAVAILABLE -> "The service is not available right now. Please try again.";
            case GATEWAY_TIMEOUT -> "The service took too long to answer. Please try again.";
            case NOT_FOUND -> "No endpoint matches this path.";
            case METHOD_NOT_ALLOWED -> "This HTTP method is not supported here.";
            default -> status.is4xxClientError()
                    ? status.getReasonPhrase() + "."
                    : "Something went wrong. Please try again.";
        };
    }
}
