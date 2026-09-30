package com.example.APIGateway.filter;

import com.example.APIGateway.utils.GatewayErrorWriter;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

// C1 step 2b: checks TraceIdGatewayFilter - reuse a safe caller id, replace an unsafe one,
// and make sure every part of the gateway ends up using the SAME checked id.
class TraceIdGatewayFilterTest {

    private final TraceIdGatewayFilter filter = new TraceIdGatewayFilter();

    // Runs the filter. Returns the request as it was passed on to the next filter.
    private ServerWebExchange run(MockServerWebExchange exchange) {
        AtomicReference<ServerWebExchange> next = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            next.set(ex);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return next.get();
    }

    private MockServerWebExchange requestWithTraceId(String traceId) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/incidents").header("X-Trace-Id", traceId).build());
    }

    @Test
    void noIdSentMakesANewOneAndPutsItEverywhere() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/incidents").build());
        ServerWebExchange forwarded = run(exchange);

        String onReply = exchange.getResponse().getHeaders().getFirst("X-Trace-Id");
        assertNotNull(onReply);
        assertEquals(8, onReply.length(), "new ids are 8 characters: " + onReply);
        assertEquals(onReply, forwarded.getRequest().getHeaders().getFirst("X-Trace-Id"), "forwarded = reply");
        assertEquals(onReply, TraceIdGatewayFilter.currentTraceId(exchange), "everyone reads the same id");
    }

    @Test
    void safeCallerIdIsReused() {
        MockServerWebExchange exchange = requestWithTraceId("abc12345");
        ServerWebExchange forwarded = run(exchange);

        assertEquals("abc12345", exchange.getResponse().getHeaders().getFirst("X-Trace-Id"));
        assertEquals("abc12345", forwarded.getRequest().getHeaders().getFirst("X-Trace-Id"));
    }

    @Test
    void fullUuidIsSafe() {
        String uuid = "3f2a9c1e-7b4d-4e8a-9c2b-1a2b3c4d5e6f";
        ServerWebExchange forwarded = run(requestWithTraceId(uuid));
        assertEquals(uuid, forwarded.getRequest().getHeaders().getFirst("X-Trace-Id"));
    }

    // Unsafe ids are replaced - and the unsafe value never reaches a service.
    @Test
    void unsafeCallerIdsAreReplaced() {
        String[] unsafe = {
                "a".repeat(65),                       // too long
                "abc 123",                            // space
                "<script>alert(1)</script>",          // HTML
                "abc\"123",                           // quote
                "../../etc",                          // slashes and dots
                "",                                   // empty
        };
        for (String bad : unsafe) {
            MockServerWebExchange exchange = requestWithTraceId(bad);
            ServerWebExchange forwarded = run(exchange);

            String used = forwarded.getRequest().getHeaders().getFirst("X-Trace-Id");
            assertNotEquals(bad, used, "unsafe id must not be forwarded: " + bad);
            assertEquals(8, used.length(), "replaced with a fresh id for: " + bad);
            assertEquals(used, exchange.getResponse().getHeaders().getFirst("X-Trace-Id"));
        }
    }

    // The first call decides the id; every later call gets the same one.
    @Test
    void theIdIsDecidedOnce() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x").build());
        String first = TraceIdGatewayFilter.currentTraceId(exchange);
        assertEquals(first, TraceIdGatewayFilter.currentTraceId(exchange));
        assertEquals(first, TraceIdGatewayFilter.currentTraceId(exchange));
    }

    // The trap: the last-resort error handler gets the ORIGINAL request, whose header still
    // holds the caller's raw (unsafe) value. It must still report the CHECKED id.
    @Test
    void errorWrittenOnTheOriginalRequestUsesTheCheckedId() {
        MockServerWebExchange original = requestWithTraceId("<bad id>");
        ServerWebExchange forwarded = run(original);
        String checked = forwarded.getRequest().getHeaders().getFirst("X-Trace-Id");

        GatewayErrorWriter.write(original, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "down").block();

        String body = original.getResponse().getBodyAsString().block();
        assertTrue(body.contains("\"traceId\":\"" + checked + "\""), body);
        assertFalse(body.contains("bad id"), "the raw caller value must not appear: " + body);
    }

    // Runs first of all the gateway filters, so even a request refused for its size has an id.
    @Test
    void runsBeforeEveryOtherGatewayFilter() {
        assertEquals(-3, filter.getOrder());
        assertTrue(filter.getOrder() < new RequestSizeFilter(org.springframework.util.unit.DataSize.ofKilobytes(1)).getOrder());
    }
}
