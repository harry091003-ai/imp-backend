package com.project.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.trace.TraceId;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.RetriableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ClassUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Sends the saved outbox rows to Kafka.
 *
 * One round (publishBatch):
 *
 *   1. CLAIM   short transaction: pick up to batchSize NEW rows, oldest first, mark them SENDING
 *   2. SEND    no transaction open: send each one and WAIT until Kafka confirms it
 *                 ok                   -> SENT
 *                 Kafka down / slow    -> back to NEW, and STOP this round (keeps the order)
 *                 the message is broken -> FAILED, and carry on with the next one
 *
 * Why no transaction during step 2: a transaction holds a database connection and locks.
 * Holding them while waiting on the network (Kafka may be down for minutes) would make the
 * service's normal requests wait too.
 *
 * Delivery is "at least once": if the app dies after Kafka confirmed but before the row is
 * marked SENT, the row is sent again later. Consumers must be safe to repeat (the inbox).
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private static final String CLAIM = """
            SELECT seq, id, topic, msg_key, event_type, payload, trace_id
            FROM outbox_event
            WHERE status = ?
            ORDER BY seq
            LIMIT ?
            FOR UPDATE""";

    private static final RowMapper<OutboxRow> ROW = (rs, n) -> new OutboxRow(
            rs.getLong("seq"), rs.getString("id"), rs.getString("topic"), rs.getString("msg_key"),
            rs.getString("event_type"), rs.getString("payload"), rs.getString("trace_id"));

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final TransactionTemplate tx;
    private final KafkaTemplate<Object, Object> kafka;
    private final OutboxProperties props;
    private final ObjectMapper mapper;
    private final ClassLoader classLoader;

    @SuppressWarnings("unchecked")
    public OutboxRelay(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                       ProducerFactory<?, ?> producerFactory, OutboxProperties props,
                       ObjectMapper mapper, ClassLoader classLoader) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
        this.tx = new TransactionTemplate(transactionManager);
        this.props = props;
        this.mapper = mapper;
        this.classLoader = classLoader;
        // The service's own Kafka settings (same serializers, same trace-id interceptor), so the
        // message is exactly what a direct send would produce. One change: never wait longer than
        // maxBlockMs for a broker that is down (Kafka's default is 60 seconds).
        this.kafka = new KafkaTemplate<>((ProducerFactory<Object, Object>) producerFactory,
                Map.<String, Object>of(ProducerConfig.MAX_BLOCK_MS_CONFIG, props.getMaxBlockMs()));
    }

    /** One round: claim, send, mark. Returns how many events were sent. */
    public int publishBatch() {
        List<OutboxRow> rows = claim();
        int sent = 0;
        for (int i = 0; i < rows.size(); i++) {
            OutboxRow row = rows.get(i);

            Object event;
            try {
                event = rebuild(row);
            } catch (Exception e) {
                markFailed(row, "Cannot rebuild the event: " + describe(e));
                continue;   // a broken row must not block the healthy ones behind it
            }

            try {
                send(row, event);
                markSent(row);
                sent++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                putBack(rows.subList(i, rows.size()));
                break;
            } catch (Exception e) {
                if (isRetriable(e)) {
                    // Kafka is down or slow. Not the message's fault: keep it, try again next round.
                    // Stop here, so a later event can never overtake this one.
                    markRetry(row, describe(e));
                    putBack(rows.subList(i + 1, rows.size()));
                    log.warn("Outbox: Kafka did not take event {} (topic {}); will retry. {}",
                            row.id(), row.topic(), describe(e));
                    break;
                }
                markFailed(row, describe(e));
            }
        }
        return sent;
    }

    /** Rows left in SENDING by a crash (claimed long ago, never finished) go back to NEW. */
    public int releaseStuckRows() {
        LocalDateTime cutoff = LocalDateTime.now().minus(Duration.ofMillis(props.getClaimTimeoutMs()));
        int released = jdbc.update(
                "UPDATE outbox_event SET status = ?, claimed_at = NULL WHERE status = ? AND claimed_at < ?",
                OutboxStatus.NEW, OutboxStatus.SENDING, Timestamp.valueOf(cutoff));
        if (released > 0) {
            log.warn("Outbox: {} event(s) were left half-sent (crash?); they will be sent again", released);
        }
        return released;
    }

    /** Delete SENT rows older than retentionDays, so the table does not grow forever. */
    public int deleteOldSentRows() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(props.getRetentionDays());
        return jdbc.update("DELETE FROM outbox_event WHERE status = ? AND sent_at < ?",
                OutboxStatus.SENT, Timestamp.valueOf(cutoff));
    }

    /** How old the oldest unsent event is. Empty if nothing is waiting. */
    public Optional<Duration> oldestUnsentAge() {
        Timestamp oldest = jdbc.queryForObject(
                "SELECT MIN(created_at) FROM outbox_event WHERE status IN (?, ?)",
                Timestamp.class, OutboxStatus.NEW, OutboxStatus.SENDING);
        return Optional.ofNullable(oldest)
                .map(t -> Duration.between(t.toLocalDateTime(), LocalDateTime.now()));
    }

    /** Log a warning if events are piling up, or if any are FAILED. */
    public void warnIfStuck() {
        oldestUnsentAge()
                .filter(age -> age.toMillis() > props.getStuckWarnAfterMs())
                .ifPresent(age -> log.warn("Outbox: the oldest unsent event is {} seconds old. Is Kafka down?",
                        age.toSeconds()));
        Integer failed = jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE status = ?",
                Integer.class, OutboxStatus.FAILED);
        if (failed != null && failed > 0) {
            log.warn("Outbox: {} event(s) are FAILED and will not be retried. See outbox_event.last_error", failed);
        }
    }

    /** Close the Kafka connection this relay opened. */
    public void close() {
        if (kafka.getProducerFactory() instanceof DisposableBean disposable) {
            try {
                disposable.destroy();
            } catch (Exception e) {
                log.debug("Outbox: closing the Kafka producer failed", e);
            }
        }
    }

    // ----------------------------------------------------------------------------------------

    private List<OutboxRow> claim() {
        List<OutboxRow> rows = tx.execute(status -> {
            List<OutboxRow> found = jdbc.query(CLAIM, ROW, OutboxStatus.NEW, props.getBatchSize());
            if (!found.isEmpty()) {
                named.update("UPDATE outbox_event SET status = :sending, claimed_at = :now WHERE seq IN (:seqs)",
                        Map.of("sending", OutboxStatus.SENDING, "now", now(), "seqs", seqs(found)));
            }
            return found;
        });
        return rows == null ? List.of() : rows;
    }

    private Object rebuild(OutboxRow row) throws Exception {
        Class<?> type = ClassUtils.forName(row.eventType(), classLoader);
        return mapper.readValue(row.payload(), type);
    }

    private void send(OutboxRow row, Object event) throws Exception {
        ProducerRecord<Object, Object> record = new ProducerRecord<>(row.topic(), row.key(), event);
        boolean hasTraceId = TraceId.isSafe(row.traceId());
        if (hasTraceId) {
            // The trace-id interceptor keeps a header that is already there.
            record.headers().add(TraceId.HEADER, row.traceId().getBytes(StandardCharsets.UTF_8));
        }
        record.headers().add(OutboxHeaders.EVENT_ID, row.id().getBytes(StandardCharsets.UTF_8));

        // Put the trace id on this thread's notepad too, so these log lines show it.
        if (hasTraceId) {
            MDC.put(TraceId.MDC_KEY, row.traceId());
        }
        try {
            kafka.send(record).get(props.getSendTimeoutMs(), TimeUnit.MILLISECONDS);
        } finally {
            MDC.remove(TraceId.MDC_KEY);
        }
    }

    private void markSent(OutboxRow row) {
        jdbc.update("UPDATE outbox_event SET status = ?, attempts = attempts + 1, sent_at = ?, last_error = NULL "
                + "WHERE seq = ?", OutboxStatus.SENT, now(), row.seq());
    }

    private void markRetry(OutboxRow row, String error) {
        jdbc.update("UPDATE outbox_event SET status = ?, attempts = attempts + 1, claimed_at = NULL, last_error = ? "
                + "WHERE seq = ?", OutboxStatus.NEW, shorten(error), row.seq());
    }

    private void markFailed(OutboxRow row, String error) {
        jdbc.update("UPDATE outbox_event SET status = ?, attempts = attempts + 1, last_error = ? WHERE seq = ?",
                OutboxStatus.FAILED, shorten(error), row.seq());
        log.error("Outbox: event {} (topic {}, type {}) is FAILED and will not be retried: {}",
                row.id(), row.topic(), row.eventType(), error);
    }

    /** Rows we claimed but did not try: back to NEW, no attempt counted. */
    private void putBack(List<OutboxRow> rows) {
        if (!rows.isEmpty()) {
            named.update("UPDATE outbox_event SET status = :new, claimed_at = NULL "
                            + "WHERE seq IN (:seqs) AND status = :sending",
                    Map.of("new", OutboxStatus.NEW, "sending", OutboxStatus.SENDING, "seqs", seqs(rows)));
        }
    }

    /** Kafka down, slow, or not answering: worth trying again. Anything else is the message's fault. */
    static boolean isRetriable(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof RetriableException || t instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
        }
        return false;
    }

    /** The error type and message of the whole cause chain, never just "null". */
    static String describe(Throwable error) {
        StringBuilder text = new StringBuilder();
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (text.length() > 0) {
                text.append(" <- ");
            }
            text.append(t.getClass().getSimpleName());
            if (t.getMessage() != null) {
                text.append(": ").append(t.getMessage());
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return text.toString();
    }

    private static String shorten(String text) {
        return text.length() <= 1000 ? text : text.substring(0, 1000);
    }

    private static List<Long> seqs(List<OutboxRow> rows) {
        return rows.stream().map(OutboxRow::seq).toList();
    }

    private static Timestamp now() {
        return Timestamp.valueOf(LocalDateTime.now());
    }

    /** One outbox row, as the relay needs it. */
    record OutboxRow(long seq, String id, String topic, String key, String eventType, String payload,
                     String traceId) {
    }
}
