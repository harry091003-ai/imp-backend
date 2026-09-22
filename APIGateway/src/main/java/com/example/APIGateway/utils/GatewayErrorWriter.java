package com.example.APIGateway.utils;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

// B4 step 6: one place that writes the gateway's JSON error.
// Moved here from JwtGatewayFilter, because RequestSizeFilter needs the same thing.
// Two copies would slowly drift apart; one copy can't.
//
// The shape must match ErrorResponse in common-web (the other services).
// The gateway can't use common-web (it is WebFlux, common-web is MVC), so it is written by hand.
public final class GatewayErrorWriter {

    // Must match TraceIdFilter.TRACE_ID_HEADER in common-web.
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private GatewayErrorWriter() {
    }

    // Writes {timestamp, status, error, message, path, traceId} and ends the request.
    public static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String errorCode, String message) {
        String traceId = resolveTraceId(exchange);
        String path = exchange.getRequest().getURI().getPath();

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(TRACE_ID_HEADER, traceId);

        String body = "{"
                + "\"timestamp\":\"" + Instant.now() + "\","
                + "\"status\":" + status.value() + ","
                + "\"error\":\"" + jsonEscape(errorCode) + "\","
                + "\"message\":\"" + jsonEscape(message) + "\","
                + "\"path\":\"" + jsonEscape(path) + "\","
                + "\"traceId\":\"" + jsonEscape(traceId) + "\""
                + "}";

        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    // Use the caller's trace id if they sent one, else one we already set, else make a short new one.
    public static String resolveTraceId(ServerWebExchange exchange) {
        String inbound = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (inbound != null && !inbound.isBlank()) {
            return inbound;
        }
        String existing = exchange.getResponse().getHeaders().getFirst(TRACE_ID_HEADER);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // Minimal JSON escaping - the values here are our own codes, paths and messages.
    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }
}
