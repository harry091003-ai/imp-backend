package com.example.APIGateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.util.unit.DataSize;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

// B4 step 6: checks RequestSizeFilter's 4 rules.
// "passed" = the request went on to the next filter. Rejected = it stopped here with an error.
class RequestSizeFilterTest {

    private final RequestSizeFilter filter = new RequestSizeFilter(DataSize.ofKilobytes(256));

    // Runs the filter. Returns true if the request was let through.
    private boolean run(MockServerWebExchange exchange) {
        AtomicBoolean passed = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            passed.set(true);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return passed.get();
    }

    @Test
    void smallBodyPasses() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/incidents").header("Content-Length", "10000").build());
        assertTrue(run(exchange));
    }

    @Test
    void bodyExactlyAtLimitPasses() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/incidents").header("Content-Length", String.valueOf(256 * 1024)).build());
        assertTrue(run(exchange));
    }

    @Test
    void bodyOneByteOverLimitIs413() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/incidents").header("Content-Length", String.valueOf(256 * 1024 + 1)).build());
        assertFalse(run(exchange));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exchange.getResponse().getStatusCode());
        String body = exchange.getResponse().getBodyAsString().block();
        assertTrue(body.contains("\"error\":\"PAYLOAD_TOO_LARGE\""), body);
        assertTrue(body.contains("\"status\":413"), body);
        assertTrue(body.contains("\"traceId\":"), body);
    }

    @Test
    void tenMegabytesIs413() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/ai/rag/suggestion").header("Content-Length", String.valueOf(10 * 1024 * 1024)).build());
        assertFalse(run(exchange));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exchange.getResponse().getStatusCode());
    }

    // The hole in Spring's built-in filter: chunked = no Content-Length.
    @Test
    void chunkedBodyWithNoLengthIs411() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/incidents").header("Transfer-Encoding", "chunked").build());
        assertFalse(run(exchange));
        assertEquals(HttpStatus.LENGTH_REQUIRED, exchange.getResponse().getStatusCode());
    }

    // A normal GET has no body, so no length and no chunked. Must pass.
    @Test
    void getWithNoBodyPasses() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/incidents").build());
        assertTrue(run(exchange));
    }

    @Test
    void nonNumberLengthIs400() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/incidents").header("Content-Length", "abc").build());
        assertFalse(run(exchange));
        assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
    }

    // The caller's trace id comes back on the error, so logs can be matched up.
    @Test
    void rejectionKeepsCallersTraceId() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/incidents").header("Content-Length", "999999999")
                        .header("X-Trace-Id", "abc12345").build());
        run(exchange);
        assertEquals("abc12345", exchange.getResponse().getHeaders().getFirst("X-Trace-Id"));
    }

    // The value in application.properties is the text "256KB".
    // This checks Spring Boot turns that text into 262144 bytes (KB = 1024 here, not 1000).
    @Test
    void propertyText256KBMeans262144Bytes() {
        DataSize size = ApplicationConversionService.getSharedInstance().convert("256KB", DataSize.class);
        assertEquals(262144, size.toBytes());
    }
}
