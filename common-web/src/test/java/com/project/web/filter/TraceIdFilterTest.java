package com.project.web.filter;

import com.project.trace.TraceId;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue 51 (2026-10-28): the services' TraceIdFilter reused ANY incoming X-Trace-Id.
 * Now it applies the same safety rule as the gateway and Kafka (TraceId.isSafe): a bad id is replaced.
 * Why it matters: the id is printed on every log line. A value with a line break or a huge value could
 * write fake log lines or flood the logs.
 */
class TraceIdFilterTest {

    /** Runs the filter with this X-Trace-Id (null = no header) and returns the id it used. */
    private static String traceIdUsedFor(String incoming) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/anything");
        if (incoming != null) {
            request.addHeader(TraceId.HEADER, incoming);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        new TraceIdFilter().doFilter(request, response, new MockFilterChain());

        String used = (String) request.getAttribute(TraceId.MDC_KEY);
        assertEquals(used, response.getHeader(TraceId.HEADER), "the reply carries the same id");
        return used;
    }

    @Test
    void aSafeIdFromTheGatewayIsKept() throws Exception {
        assertEquals("a1b2c3d4", traceIdUsedFor("a1b2c3d4"));
    }

    @Test
    void noIdGetsANewOne() throws Exception {
        assertTrue(TraceId.isSafe(traceIdUsedFor(null)));
    }

    @Test
    void anIdWithALineBreakIsReplaced() throws Exception {
        String used = traceIdUsedFor("abc\r\n2026-10-28 INFO fake log line");
        assertTrue(TraceId.isSafe(used), used);
        assertFalse(used.contains("fake"));
    }

    @Test
    void aHugeIdIsReplaced() throws Exception {
        String used = traceIdUsedFor("x".repeat(5000));
        assertTrue(used.length() <= 64, "replaced, not kept: length " + used.length());
    }

    @Test
    void anIdWithQuotesOrSpacesIsReplaced() throws Exception {
        assertTrue(TraceId.isSafe(traceIdUsedFor("abc def\"<script>")));
    }
}
