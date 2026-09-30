package com.project.kafka.trace;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

// The SENDER's half: stick the current trace id on the message header before it leaves.
class TraceIdProducerInterceptorTest {

    private final TraceIdProducerInterceptor<String, String> interceptor = new TraceIdProducerInterceptor<>();

    @AfterEach
    void cleanNotepad() {
        MDC.remove("traceId");
    }

    private static String headerOf(ProducerRecord<?, ?> record) {
        Header header = record.headers().lastHeader("X-Trace-Id");
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    @Test
    void stampsTheNumberFromTheNotepadOntoTheMessage() {
        MDC.put("traceId", "abc12345");
        ProducerRecord<String, String> sent = interceptor.onSend(new ProducerRecord<>("orders", "k", "v"));
        assertEquals("abc12345", headerOf(sent));
    }

    // The message keeps everything else exactly as it was.
    @Test
    void doesNotChangeTheMessageItself() {
        MDC.put("traceId", "abc12345");
        ProducerRecord<String, String> original = new ProducerRecord<>("orders", "the-key", "the-value");
        ProducerRecord<String, String> sent = interceptor.onSend(original);

        assertEquals("orders", sent.topic());
        assertEquals("the-key", sent.key());
        assertEquals("the-value", sent.value());
    }

    // A dead-letter copy carries the ORIGINAL message's number. It must not be overwritten by
    // whatever is on this thread's notepad.
    @Test
    void anExistingHeaderIsNeverOverwritten() {
        MDC.put("traceId", "on-the-notepad");
        ProducerRecord<String, String> record = new ProducerRecord<>("orders", "k", "v");
        record.headers().add("X-Trace-Id", "original-id".getBytes(StandardCharsets.UTF_8));

        ProducerRecord<String, String> sent = interceptor.onSend(record);

        assertEquals("original-id", headerOf(sent));
        assertEquals(1, countHeaders(sent), "must not add a second X-Trace-Id header");
    }

    // e.g. a scheduled job: no request, so nothing on the notepad. Send without a number.
    @Test
    void nothingOnTheNotepadMeansNoHeader() {
        ProducerRecord<String, String> sent = interceptor.onSend(new ProducerRecord<>("orders", "k", "v"));
        assertNull(headerOf(sent));
    }

    // Defence in depth: even our own notepad is not trusted to hold something odd.
    @Test
    void anUnsafeNumberOnTheNotepadIsNotSent() {
        MDC.put("traceId", "<script>");
        ProducerRecord<String, String> sent = interceptor.onSend(new ProducerRecord<>("orders", "k", "v"));
        assertNull(headerOf(sent));
    }

    private static int countHeaders(ProducerRecord<?, ?> record) {
        int count = 0;
        for (Header ignored : record.headers().headers("X-Trace-Id")) {
            count++;
        }
        return count;
    }
}
