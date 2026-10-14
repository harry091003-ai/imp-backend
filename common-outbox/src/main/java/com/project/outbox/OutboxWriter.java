package com.project.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.trace.TraceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * What a service calls INSTEAD of kafkaTemplate.send(...).
 *
 * It does not talk to Kafka at all. It saves the event as one row in the outbox_event table,
 * inside the transaction that is already open for the business data. So:
 *
 *   commit   -> the data AND the event are saved together
 *   rollback -> neither is saved (no "ghost" event about data that does not exist)
 *
 * The OutboxRelay sends the row to Kafka a moment later.
 *
 * Why plain SQL (JdbcTemplate) and not a JPA entity: a JPA entity in a shared jar would need
 * extra scanning setup in every service. JdbcTemplate needs none, and inside a transaction it
 * uses the same database connection as JPA, so it joins the same transaction by itself.
 */
public class OutboxWriter {

    private static final Logger log = LoggerFactory.getLogger(OutboxWriter.class);

    private static final String INSERT = """
            INSERT INTO outbox_event
                (id, topic, msg_key, event_type, payload, trace_id, status, attempts, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?)""";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public OutboxWriter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /**
     * Save an event to be sent to Kafka.
     *
     * @param topic the Kafka topic, same as you would pass to kafkaTemplate.send(...)
     * @param key   the Kafka key (incident id, user id ...). Events with the same key stay in order.
     * @param event the event object, same as today
     * @return the event id (also sent as the X-Event-Id header)
     * @throws IllegalStateException if no transaction is open
     */
    public String save(String topic, String key, Object event) {
        // Without a transaction the row would be committed on its own, separate from the data.
        // That is exactly the problem the outbox exists to solve, so refuse loudly.
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("OutboxWriter.save must run inside a database transaction "
                    + "(@Transactional), so the event is saved together with the data.");
        }
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(event, "event");

        String payload;
        try {
            payload = mapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot turn this event into JSON: " + event.getClass().getName(), e);
        }

        // Save the request's trace id now. The relay runs later on its own thread, where it is gone.
        String traceId = MDC.get(TraceId.MDC_KEY);

        String id = UUID.randomUUID().toString();
        jdbc.update(INSERT, id, topic, key, event.getClass().getName(), payload,
                TraceId.isSafe(traceId) ? traceId : null, OutboxStatus.NEW,
                Timestamp.valueOf(LocalDateTime.now()));

        log.debug("Event {} for topic {} saved to the outbox", id, topic);
        return id;
    }
}
