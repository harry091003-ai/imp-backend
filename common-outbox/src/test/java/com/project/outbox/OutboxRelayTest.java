package com.project.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.JacksonUtils;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The relay, against a REAL Kafka broker started inside the test (no Docker needed),
 * and an in-memory database.
 */
class OutboxRelayTest {

    private static EmbeddedKafkaBroker broker;

    private final ObjectMapper mapper = JacksonUtils.enhancedObjectMapper();
    private final List<OutboxRelay> relays = new ArrayList<>();
    private OutboxTestDb db;
    private OutboxWriter writer;
    private OutboxProperties props;

    @BeforeAll
    static void startBroker() {
        broker = new EmbeddedKafkaKraftBroker(1, 1).brokerProperties(Map.of(
                "offsets.topic.replication.factor", "1",
                "transaction.state.log.replication.factor", "1",
                "transaction.state.log.min.isr", "1",
                "group.initial.rebalance.delay.ms", "0"));
        broker.afterPropertiesSet();
    }

    @AfterAll
    static void stopBroker() {
        broker.destroy();
    }

    @BeforeEach
    void setUp() {
        db = OutboxTestDb.create();
        writer = new OutboxWriter(db.jdbc, mapper);
        props = new OutboxProperties();
    }

    @AfterEach
    void tearDown() {
        relays.forEach(OutboxRelay::close);
        MDC.clear();
    }

    // ---------------------------------------------------------------- helpers

    /** The same producer settings our services have in application.properties. */
    private static Map<String, Object> serviceProducerProps(String servers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return props;
    }

    private OutboxRelay relay(String servers) {
        OutboxRelay relay = new OutboxRelay(db.jdbc, db.transactionManager,
                new DefaultKafkaProducerFactory<>(serviceProducerProps(servers)), props, mapper,
                getClass().getClassLoader());
        relays.add(relay);
        return relay;
    }

    private OutboxRelay relay() {
        return relay(broker.getBrokersAsString());
    }

    private static String topic(String name) {
        String topic = name + "-" + UUID.randomUUID().toString().substring(0, 6);
        broker.addTopics(topic);
        return topic;
    }

    private String save(String topic, String key, Object event) {
        return db.tx.execute(status -> writer.save(topic, key, event));
    }

    /** Reads the raw text Kafka holds, as any consumer would receive it. */
    private static List<ConsumerRecord<String, String>> read(String topic, int howMany) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(broker.getBrokersAsString(),
                "reader-" + UUID.randomUUID(), "false");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<String, String>(props).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, topic);
            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(20), howMany).forEach(records::add);
            return records;
        }
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- tests

    @Test
    void sendsWaitingEventsInOrderAndMarksThemSent() {
        String topic = topic("orders");
        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            ids.add(save(topic, "same-incident", TestOrderEvent.named("event " + i)));
        }

        int sent = relay().publishBatch();

        assertEquals(5, sent);
        List<ConsumerRecord<String, String>> records = read(topic, 5);
        for (int i = 0; i < 5; i++) {
            assertTrue(records.get(i).value().contains("event " + (i + 1)), "arrived in the order saved");
            assertEquals("same-incident", records.get(i).key());
        }
        for (String id : ids) {
            assertEquals(OutboxStatus.SENT, db.status(id));
            assertNotNull(db.row(id).get("SENT_AT"));
            assertEquals(1, db.attempts(id));
        }
    }

    @Test
    void theMessageIsExactlyWhatADirectSendProducesToday() throws Exception {
        TestOrderEvent event = TestOrderEvent.named("same bytes");
        String viaOutbox = topic("via-outbox");
        String direct = topic("direct");

        save(viaOutbox, "k", event);
        relay().publishBatch();
        KafkaTemplate<Object, Object> today =
                new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(serviceProducerProps(broker.getBrokersAsString())));
        today.send(direct, "k", event).get(20, TimeUnit.SECONDS);
        today.destroy();

        ConsumerRecord<String, String> fromOutbox = read(viaOutbox, 1).get(0);
        ConsumerRecord<String, String> fromDirect = read(direct, 1).get(0);
        // Same JSON and same type header, so our consumers need NO change at all.
        assertEquals(fromDirect.value(), fromOutbox.value());
        assertEquals(header(fromDirect, "__TypeId__"), header(fromOutbox, "__TypeId__"));
        assertEquals(TestOrderEvent.class.getName(), header(fromOutbox, "__TypeId__"));
    }

    @Test
    void carriesTheTraceIdAndTheEventId() {
        String topic = topic("headers");
        MDC.put("traceId", "trc-777");              // the request that saved the event
        String id = save(topic, "k", TestOrderEvent.named("x"));
        MDC.clear();                                 // the relay's own thread has no notepad

        relay().publishBatch();

        ConsumerRecord<String, String> record = read(topic, 1).get(0);
        assertEquals("trc-777", header(record, "X-Trace-Id"));
        assertEquals(id, header(record, OutboxHeaders.EVENT_ID));
    }

    @Test
    void nothingWaitingMeansNothingSent() {
        assertEquals(0, relay().publishBatch());
    }

    @Test
    void whenKafkaIsDownEveryRowIsKeptAndTheRoundStops() {
        props.setMaxBlockMs(1_000);                  // fail fast in the test
        String first = save("orders", "k", TestOrderEvent.named("1"));
        String second = save("orders", "k", TestOrderEvent.named("2"));
        String third = save("orders", "k", TestOrderEvent.named("3"));

        int sent = relay("localhost:1").publishBatch();    // nothing listens on port 1

        assertEquals(0, sent);
        assertEquals(OutboxStatus.NEW, db.status(first), "kept, to try again");
        assertEquals(1, db.attempts(first));
        assertNotNull(db.row(first).get("LAST_ERROR"));
        assertNull(db.row(first).get("CLAIMED_AT"));
        // the round stopped at the first failure: the others were not even tried, so order is kept
        assertEquals(OutboxStatus.NEW, db.status(second));
        assertEquals(0, db.attempts(second));
        assertEquals(OutboxStatus.NEW, db.status(third));
        assertEquals(0, db.attempts(third));
    }

    @Test
    void aBrokenRowIsParkedAndTheRestStillGo() {
        String topic = topic("mixed");
        String broken = db.insertRaw(topic, "com.nowhere.ThisClassDoesNotExist", "{}", OutboxStatus.NEW,
                LocalDateTime.now(), null, null);
        String good = save(topic, "k", TestOrderEvent.named("healthy"));

        int sent = relay().publishBatch();

        assertEquals(1, sent);
        assertEquals(OutboxStatus.FAILED, db.status(broken));
        assertTrue(db.row(broken).get("LAST_ERROR").toString().contains("ThisClassDoesNotExist"));
        assertEquals(OutboxStatus.SENT, db.status(good));
        assertTrue(read(topic, 1).get(0).value().contains("healthy"));
    }

    @Test
    void rowsLeftHalfSentByACrashGoBackToNew() {
        String crashed = db.insertRaw("orders", TestOrderEvent.class.getName(), "{}", OutboxStatus.SENDING,
                LocalDateTime.now().minusMinutes(10), LocalDateTime.now().minusMinutes(10), null);
        String inProgress = db.insertRaw("orders", TestOrderEvent.class.getName(), "{}", OutboxStatus.SENDING,
                LocalDateTime.now(), LocalDateTime.now(), null);

        int released = relay().releaseStuckRows();

        assertEquals(1, released);
        assertEquals(OutboxStatus.NEW, db.status(crashed));
        assertNull(db.row(crashed).get("CLAIMED_AT"));
        assertEquals(OutboxStatus.SENDING, db.status(inProgress), "a fresh claim is left alone");
    }

    @Test
    void cleanupDeletesOnlyOldSentRows() {
        LocalDateTime now = LocalDateTime.now();
        String oldSent = db.insertRaw("t", "x", "{}", OutboxStatus.SENT, now.minusDays(9), null, now.minusDays(8));
        String recentSent = db.insertRaw("t", "x", "{}", OutboxStatus.SENT, now.minusDays(2), null, now.minusDays(1));
        String oldFailed = db.insertRaw("t", "x", "{}", OutboxStatus.FAILED, now.minusDays(30), null, null);
        String oldNew = db.insertRaw("t", "x", "{}", OutboxStatus.NEW, now.minusDays(30), null, null);

        int deleted = relay().deleteOldSentRows();

        assertEquals(1, deleted);
        assertEquals(3, db.count());
        assertEquals(OutboxStatus.SENT, db.status(recentSent));
        assertEquals(OutboxStatus.FAILED, db.status(oldFailed), "kept for a person to look at");
        assertEquals(OutboxStatus.NEW, db.status(oldNew), "never delete what was not sent");
        assertEquals(0, db.jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE id = ?", Integer.class, oldSent));
    }

    @Test
    void reportsHowOldTheOldestUnsentEventIs() {
        assertTrue(relay().oldestUnsentAge().isEmpty());

        db.insertRaw("t", "x", "{}", OutboxStatus.NEW, LocalDateTime.now().minusMinutes(5), null, null);

        Duration age = relay().oldestUnsentAge().orElseThrow();
        assertTrue(age.toSeconds() >= 299, "about 5 minutes, was " + age);
    }

    @Test
    void kafkaTroubleIsRetriedButABrokenMessageIsNot() {
        assertTrue(OutboxRelay.isRetriable(new RuntimeException(
                new org.apache.kafka.common.errors.TimeoutException("broker not available"))));
        assertTrue(OutboxRelay.isRetriable(new java.util.concurrent.TimeoutException()));
        assertFalse(OutboxRelay.isRetriable(new org.apache.kafka.common.errors.SerializationException("bad")));
        assertFalse(OutboxRelay.isRetriable(new IllegalArgumentException("bad")));
    }
}
