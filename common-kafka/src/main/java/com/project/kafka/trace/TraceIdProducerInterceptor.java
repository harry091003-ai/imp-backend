package com.project.kafka.trace;

import com.project.trace.TraceId;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

// C1 step 3: the SENDER's half. Just before a Kafka message leaves, stick the current tracking
// number on its ENVELOPE (a Kafka message header called X-Trace-Id).
//
// Where does "the current number" come from? From this thread's notepad (the MDC):
//   - an HTTP request  -> common-web's TraceIdFilter wrote it there
//   - a Kafka listener -> TraceIdRecordInterceptor wrote it there (so a service that receives a
//                         message and then sends another one keeps the SAME number going)
//
// Kafka calls onSend() on the thread that called send(), so the notepad is right there.
// (Kafka itself catches and logs an exception thrown by an interceptor, so a problem here can
// never stop a message from being sent.)
//
// Installed for every producer by KafkaTraceIdAutoConfig - no per-service setting.
public class TraceIdProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {

    @Override
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
        // Already has one? Leave it. (A dead-letter copy carries the ORIGINAL message's number;
        // it must not be overwritten by whatever happens to be on this thread.)
        if (record.headers().lastHeader(TraceId.HEADER) != null) {
            return record;
        }

        // Nothing on the notepad (e.g. a scheduled job, not a request)? Send without one.
        // The receiver makes its own, so its logs are still grouped per message.
        String traceId = MDC.get(TraceId.MDC_KEY);
        if (TraceId.isSafe(traceId)) {
            record.headers().add(TraceId.HEADER, traceId.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        // Nothing to do when Kafka confirms (or fails) a send.
    }

    @Override
    public void close() {
        // Nothing to clean up.
    }

    @Override
    public void configure(Map<String, ?> configs) {
        // Nothing to configure.
    }
}
