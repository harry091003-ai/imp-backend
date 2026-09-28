package com.example.APIGateway.handler;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.*;

// Checks the gateway's last-resort error handler:
// honest status, our JSON shape, and no internal details leaked to the caller.
class GatewayExceptionHandlerTest {

    private final GatewayExceptionHandler handler = new GatewayExceptionHandler();

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/incidents").header("X-Trace-Id", "abc12345").build());
    }

    private String handle(MockServerWebExchange exchange, Throwable error) {
        handler.handle(exchange, error).block();
        return exchange.getResponse().getBodyAsString().block();
    }

    // The case from the bug: no instance of the service is registered.
    @Test
    void serviceNotRegisteredIs503WithoutLeakingTheServiceName() {
        MockServerWebExchange exchange = exchange();
        String body = handle(exchange, new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Unable to find instance for INCIDENTSERVICE"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        assertTrue(body.contains("\"error\":\"SERVICE_UNAVAILABLE\""), body);
        assertFalse(body.contains("AUTH_SERVICE"), "must not blame AuthService: " + body);
        assertFalse(body.contains("INCIDENTSERVICE"), "internal service names stay in the log: " + body);
    }

    // Registered, but nothing listening (crashed / restarting).
    @Test
    void connectionRefusedIs503() {
        MockServerWebExchange exchange = exchange();
        handle(exchange, new ConnectException("Connection refused: /127.0.0.1:8085"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
    }

    @Test
    void tooSlowIs504() {
        MockServerWebExchange exchange = exchange();
        String body = handle(exchange, new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Response took longer than 5s"));
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exchange.getResponse().getStatusCode());
        assertTrue(body.contains("took too long"), body);
    }

    @Test
    void noRouteIs404() {
        MockServerWebExchange exchange = exchange();
        handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
    }

    // Our own bug: 500, and the real message is NOT shown to the caller.
    @Test
    void unexpectedErrorIs500AndHidesTheMessage() {
        MockServerWebExchange exchange = exchange();
        String body = handle(exchange, new IllegalStateException("secret internal detail"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.getResponse().getStatusCode());
        assertFalse(body.contains("secret internal detail"), body);
        assertTrue(body.contains("Something went wrong"), body);
    }

    // Our normal shape, with the caller's trace id, so logs can be matched up.
    @Test
    void bodyHasOurShapeAndTheTraceId() {
        MockServerWebExchange exchange = exchange();
        String body = handle(exchange, new ConnectException("refused"));

        for (String field : new String[]{"\"timestamp\":", "\"status\":503", "\"error\":", "\"message\":",
                "\"path\":\"/incidents\"", "\"traceId\":\"abc12345\""}) {
            assertTrue(body.contains(field), "missing " + field + " in " + body);
        }
        assertEquals("abc12345", exchange.getResponse().getHeaders().getFirst("X-Trace-Id"));
    }

    // Reply already half-sent: too late to write an error, so the error is passed on.
    @Test
    void alreadyCommittedResponseIsLeftAlone() {
        MockServerWebExchange exchange = exchange();
        exchange.getResponse().setComplete().block();   // marks the response as sent

        IllegalStateException error = new IllegalStateException("died mid-reply");
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> handler.handle(exchange, error).block());
        assertSame(error, thrown);
    }
}
