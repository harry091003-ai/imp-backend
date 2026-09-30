package com.project.trace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// The shared trace-id facts: its names, and which ids count as safe.
class TraceIdTest {

    // Changing either name is a breaking change for every service AND the gateway
    // (the gateway keeps its own copy of the header name). This test makes that loud.
    @Test
    void theNamesAreTheOnesTheWholePlatformUses() {
        assertEquals("traceId", TraceId.MDC_KEY);
        assertEquals("X-Trace-Id", TraceId.HEADER);
    }

    @Test
    void ordinaryIdsAreSafe() {
        assertTrue(TraceId.isSafe("abc12345"));
        assertTrue(TraceId.isSafe("myreq-001"));
        assertTrue(TraceId.isSafe("3f2a9c1e-7b4d-4e8a-9c2b-1a2b3c4d5e6f"));   // a full UUID
        assertTrue(TraceId.isSafe("a".repeat(64)));                           // the longest allowed
        assertTrue(TraceId.isSafe("a"));
    }

    @Test
    void unsafeIdsAreRejected() {
        assertFalse(TraceId.isSafe(null));
        assertFalse(TraceId.isSafe(""));
        assertFalse(TraceId.isSafe("a".repeat(65)));              // too long
        assertFalse(TraceId.isSafe("abc 123"));                   // space
        assertFalse(TraceId.isSafe("<script>alert(1)</script>"));
        assertFalse(TraceId.isSafe("abc\"123"));
        assertFalse(TraceId.isSafe("../../etc"));
        assertFalse(TraceId.isSafe("abc\n123"));                  // a line break could fake a log line
        assertFalse(TraceId.isSafe("abc_123"));                   // underscore is not in the safe set
    }

    @Test
    void newIdsAreShortSafeAndDifferent() {
        String first = TraceId.newId();
        assertEquals(8, first.length());
        assertTrue(TraceId.isSafe(first));
        assertNotEquals(first, TraceId.newId());
    }
}
