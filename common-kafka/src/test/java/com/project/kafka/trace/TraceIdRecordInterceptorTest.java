package com.project.kafka.trace;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

// The RECEIVER's half: read the trace id from the message header and put it on the notepad.
class TraceIdRecordInterceptorTest {

    private final TraceIdRecordInterceptor<String, String> interceptor = new TraceIdRecordInterceptor<>();

    @AfterEach
    void cleanNotepad() {
        MDC.remove("traceId");
    }

    private static ConsumerRecord<String, String> messageWithHeader(byte[] headerValue) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 0, 0L, "k", "v");
        record.headers().add("X-Trace-Id", headerValue);   // a null value is allowed on purpose
        return record;
    }

    private static ConsumerRecord<String, String> messageWithHeader(String value) {
        return messageWithHeader(value.getBytes(StandardCharsets.UTF_8));
    }

    private static ConsumerRecord<String, String> messageWithoutHeader() {
        return new ConsumerRecord<>("orders", 0, 0L, "k", "v");
    }

    @Test
    void aSafeHeaderIsPutOnTheNotepad() {
        interceptor.intercept(messageWithHeader("abc12345"), null);
        assertEquals("abc12345", MDC.get("traceId"));
    }

    // Returning null would silently SKIP the message. It must return the very same record.
    @Test
    void returnsTheSameMessage() {
        ConsumerRecord<String, String> record = messageWithHeader("abc12345");
        assertSame(record, interceptor.intercept(record, null));
    }

    // Sent by a scheduled job, or before this feature existed: make one, so this message's log
    // lines are still grouped together.
    @Test
    void aMissingHeaderGetsAFreshNumber() {
        interceptor.intercept(messageWithoutHeader(), null);
        String made = MDC.get("traceId");
        assertNotNull(made);
        assertEquals(8, made.length());
    }

    @Test
    void aHeaderWithNoValueGetsAFreshNumber() {
        interceptor.intercept(messageWithHeader((byte[]) null), null);
        assertEquals(8, MDC.get("traceId").length());
    }

    // Anyone who can write to Kafka could put anything in a header. It must not reach our logs.
    @Test
    void anUnsafeHeaderIsReplaced() {
        String[] unsafe = {"<script>alert(1)</script>", "abc 123", "a".repeat(65), "abc\n123", ""};
        for (String bad : unsafe) {
            interceptor.intercept(messageWithHeader(bad), null);
            String used = MDC.get("traceId");
            assertNotEquals(bad, used, "unsafe header must not be used: " + bad);
            assertEquals(8, used.length());
        }
    }

    // If a message has two X-Trace-Id headers, the last one wins (Kafka's own rule for lastHeader).
    @Test
    void theLastHeaderWinsIfThereAreTwo() {
        ConsumerRecord<String, String> record = messageWithHeader("first-one");
        record.headers().add("X-Trace-Id", "second-one".getBytes(StandardCharsets.UTF_8));
        interceptor.intercept(record, null);
        assertEquals("second-one", MDC.get("traceId"));
    }

    // The thread is reused for the next message. The number must be wiped, or the next message
    // would show this one's number.
    @Test
    void theNotepadIsWipedAfterTheMessage() {
        ConsumerRecord<String, String> record = messageWithHeader("abc12345");
        interceptor.intercept(record, null);
        assertNotNull(MDC.get("traceId"));

        interceptor.afterRecord(record, null);
        assertNull(MDC.get("traceId"));
    }

    @Test
    void theSafetyNetWipesToo() {
        MDC.put("traceId", "left-behind");
        interceptor.clearThreadState(null);
        assertNull(MDC.get("traceId"));
    }
}
