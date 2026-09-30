package com.project.kafka.trace;

import com.project.trace.TraceId;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;

// C1 step 3: the RECEIVER's half. When a Kafka message arrives, read the tracking number from its
// envelope (the X-Trace-Id header) and write it on THIS thread's notepad (the MDC), for as long
// as the message is being processed. The log format then prints it on every line.
//
// It is the Kafka twin of common-web's TraceIdFilter (which does the same for an HTTP request).
//
// Three cases:
//   header present and safe -> use it     (the normal case: the number from the original request)
//   header missing          -> make one   (e.g. sent by a scheduled job, or before step 3 existed)
//   header present, unsafe  -> make one   (anyone who can write to Kafka could put anything there;
//                                          it must not reach our logs - same rule as the gateway)
// So while a message is being processed, there is ALWAYS a number on the notepad.
//
// Cleaning up matters: a listener thread is reused for the next message. If the number were left
// on the notepad, the next message would wrongly show the previous one's number.
//
// Spring Boot finds this bean by itself and puts it on the listener container - no per-service
// setting. (KafkaTraceIdAutoConfig registers it.)
public class TraceIdRecordInterceptor<K, V> implements RecordInterceptor<K, V> {

    // Runs BEFORE the listener method, for every delivery of a record (also for each retry).
    @Override
    public ConsumerRecord<K, V> intercept(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
        String traceId = fromHeader(record);
        if (!TraceId.isSafe(traceId)) {
            traceId = TraceId.newId();
        }
        MDC.put(TraceId.MDC_KEY, traceId);
        return record;   // never return null: that would silently SKIP the message
    }

    // Runs AFTER the record is completely dealt with: the listener finished, or it failed and the
    // error handler (retries, dead letter) finished too. So the retry and dead-letter log lines
    // still show the number, and it is wiped only after they are written.
    @Override
    public void afterRecord(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
        MDC.remove(TraceId.MDC_KEY);
    }

    // A second, safety-net wipe in case afterRecord is ever skipped.
    @Override
    public void clearThreadState(Consumer<?, ?> consumer) {
        MDC.remove(TraceId.MDC_KEY);
    }

    private static String fromHeader(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader(TraceId.HEADER);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
