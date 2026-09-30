package com.example.APIGateway.filter;

import com.example.APIGateway.utils.GatewayErrorWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.logging.Logger;

// B4 step 6: stop huge requests at the gateway, before they reach any service.
//
// Before: nothing limited the body size. A 10 MB request went all the way to a service,
// which read it into memory. Many of those at once can run a service out of memory.
//
// Why not Spring's built-in RequestSize filter?
//   1. It only reads the Content-Length header. A request sent "chunked" has no
//      Content-Length, so it walks straight past the limit.
//   2. Its error has an empty body, not our normal JSON error.
//
// The rules:
//   Content-Length too big         -> 413 PAYLOAD_TOO_LARGE
//   body sent with no length       -> 411 LENGTH_REQUIRED (we can't check what we can't measure)
//   Content-Length is not a number -> 400 INVALID_CONTENT_LENGTH
//   anything else                  -> let it through
@Component
public class RequestSizeFilter implements GlobalFilter, Ordered {

    private static final Logger logger = Logger.getLogger(RequestSizeFilter.class.getName());

    private final long maxBytes;

    public RequestSizeFilter(@Value("${gateway.request.max-body-size}") DataSize maxBodySize) {
        this.maxBytes = maxBodySize.toBytes();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String contentLength = headers.getFirst(HttpHeaders.CONTENT_LENGTH);

        // No Content-Length.
        if (contentLength == null || contentLength.isBlank()) {
            // A body sent without a length ("chunked"). We can't know its size up front, so refuse it.
            if (headers.containsKey(HttpHeaders.TRANSFER_ENCODING)) {
                return reject(exchange, HttpStatus.LENGTH_REQUIRED, "LENGTH_REQUIRED",
                        "Send a Content-Length header with the request body.");
            }
            // No length and no chunked = no body (a normal GET). Nothing to check.
            return chain.filter(exchange);
        }

        long size;
        try {
            size = Long.parseLong(contentLength.trim());
        } catch (NumberFormatException e) {
            return reject(exchange, HttpStatus.BAD_REQUEST, "INVALID_CONTENT_LENGTH",
                    "Content-Length must be a number.");
        }

        if (size > maxBytes) {
            return reject(exchange, HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                    "Request body is too large. The limit is " + maxBytes + " bytes.");
        }

        return chain.filter(exchange);
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String errorCode, String message) {
        String traceId = TraceIdGatewayFilter.currentTraceId(exchange);
        // WARNING, not SEVERE: this is a bad request from outside, not our bug.
        logger.warning("[" + traceId + "] Request rejected: " + errorCode + " (" + status.value() + ") "
                + exchange.getRequest().getMethod() + " " + exchange.getRequest().getURI().getPath());
        return GatewayErrorWriter.write(exchange, status, errorCode, message);
    }

    // -2 = runs BEFORE JwtGatewayFilter (-1).
    // So a huge request is refused before we spend time checking its token.
    @Override
    public int getOrder() {
        return -2;
    }
}
