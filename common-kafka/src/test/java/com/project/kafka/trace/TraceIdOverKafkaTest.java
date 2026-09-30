package com.project.kafka.trace;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.project.kafka.config.KafkaTraceIdAutoConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.util.backoff.FixedBackOff;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

// The real thing, with nothing faked: a Kafka broker running inside this test, a real producer
// using our sender interceptor, and a real listener container using our receiver interceptor,
// with real retries and a real dead-letter topic.
class TraceIdOverKafkaTest {

    private static EmbeddedKafkaBroker broker;

    private final List<KafkaMessageListenerContainer<String, String>> started = new ArrayList<>();

    @BeforeAll
    static void startBroker() {
        // ONE broker, so Kafka's internal topics must not ask for 3 copies - otherwise listeners
        // can never find their "group coordinator" (NOT_COORDINATOR). Test setup only.
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

    @AfterEach
    void cleanUp() {
        started.forEach(KafkaMessageListenerContainer::stop);
        MDC.remove("traceId");
    }

    // ---------------- helpers ----------------

    private static String uniqueTopic(String name) {
        String topic = name + "-" + UUID.randomUUID().toString().substring(0, 6);
        broker.addTopics(topic);
        return topic;
    }

    // Spring's test utility defaults the KEY to an Integer. We use text keys, so say so.
    private static Map<String, Object> producerProps() {
        Map<String, Object> props = KafkaTestUtils.producerProps(broker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return props;
    }

    private static Map<String, Object> consumerProps(String group) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(broker.getBrokersAsString(), group, "false");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return props;
    }

    // A producer built the way a real service's is: Spring Boot's factory + OUR customizer.
    private static KafkaTemplate<String, String> producer() {
        DefaultKafkaProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(producerProps());
        new KafkaTraceIdAutoConfig().traceIdProducerFactoryCustomizer().customize(factory);
        return new KafkaTemplate<>(factory);
    }

    private KafkaMessageListenerContainer<String, String> listen(String topic,
                                                                 MessageListener<String, String> listener,
                                                                 RecordInterceptor<String, String> interceptor,
                                                                 DefaultErrorHandler errorHandler) {
        Map<String, Object> props = consumerProps("group-" + UUID.randomUUID());
        ContainerProperties containerProps = new ContainerProperties(topic);
        containerProps.setMessageListener(listener);
        KafkaMessageListenerContainer<String, String> container =
                new KafkaMessageListenerContainer<>(new DefaultKafkaConsumerFactory<>(props), containerProps);
        container.setRecordInterceptor(interceptor);
        if (errorHandler != null) {
            container.setCommonErrorHandler(errorHandler);
        }
        container.start();
        ContainerTestUtils.waitForAssignment(container, 1);
        started.add(container);
        return container;
    }

    private static String headerOf(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader("X-Trace-Id");
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static <T> T next(BlockingQueue<T> queue) throws InterruptedException {
        T value = queue.poll(20, TimeUnit.SECONDS);
        assertNotNull(value, "nothing arrived within 20 seconds");
        return value;
    }

    // ---------------- the tests ----------------

    // THE CORE: the number on the sender's notepad ends up on the receiver's notepad.
    @Test
    void theSendersNumberArrivesOnTheReceiversNotepad() throws Exception {
        String topic = uniqueTopic("orders");
        BlockingQueue<String> seenByListener = new LinkedBlockingQueue<>();
        listen(topic, record -> seenByListener.add(String.valueOf(MDC.get("traceId"))),
                new TraceIdRecordInterceptor<>(), null);

        MDC.put("traceId", "e2e-abc-001");                      // what TraceIdFilter does for an HTTP request
        producer().send(topic, "key", "hello").get(20, TimeUnit.SECONDS);

        assertEquals("e2e-abc-001", next(seenByListener));
    }

    // The number is really ON the message (its envelope), not just remembered somewhere.
    @Test
    void theNumberTravelsAsAKafkaHeader() throws Exception {
        String topic = uniqueTopic("header");
        MDC.put("traceId", "e2e-hdr-002");
        producer().send(topic, "key", "hello").get(20, TimeUnit.SECONDS);
        MDC.remove("traceId");

        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<String, String>(
                consumerProps("reader-" + UUID.randomUUID())).createConsumer()) {
            consumer.subscribe(List.of(topic));
            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, topic, Duration.ofSeconds(20));
            assertEquals("e2e-hdr-002", headerOf(record));
            assertEquals("hello", record.value(), "the message itself is untouched");
        }
    }

    // A receiver that sends another message keeps the SAME number going: request -> A -> B.
    @Test
    void oneNumberFollowsTheWholeChain() throws Exception {
        String topicA = uniqueTopic("chain-a");
        String topicB = uniqueTopic("chain-b");
        KafkaTemplate<String, String> template = producer();

        // A: receives, then sends to B. It runs on a consumer thread, where our receiver
        // interceptor put the number - and our sender interceptor picks it up again.
        listen(topicA, record -> template.send(topicB, "key", "forwarded"),
                new TraceIdRecordInterceptor<>(), null);

        BlockingQueue<String> seenByB = new LinkedBlockingQueue<>();
        listen(topicB, record -> seenByB.add(String.valueOf(MDC.get("traceId")) + "|" + headerOf(record)),
                new TraceIdRecordInterceptor<>(), null);

        MDC.put("traceId", "chain-xyz-003");
        template.send(topicA, "key", "start").get(20, TimeUnit.SECONDS);

        assertEquals("chain-xyz-003|chain-xyz-003", next(seenByB),
                "B must see the number from the ORIGINAL request, on its notepad AND on the message");
    }

    // Sent by something with no request (a scheduled job): the receiver makes a number,
    // so its log lines are still grouped per message.
    @Test
    void aMessageWithoutANumberGetsOne() throws Exception {
        String topic = uniqueTopic("plain");
        BlockingQueue<String> seen = new LinkedBlockingQueue<>();
        listen(topic, record -> seen.add(headerOf(record) + "|" + MDC.get("traceId")),
                new TraceIdRecordInterceptor<>(), null);

        producer().send(topic, "key", "no request behind this").get(20, TimeUnit.SECONDS);

        String[] parts = next(seen).split("\\|");
        assertEquals("null", parts[0], "the message has no header");
        assertEquals(8, parts[1].length(), "the receiver made an 8-character number");
    }

    // Anyone who can write to Kafka could put anything in the header. It must not reach our logs.
    @Test
    void anUnsafeNumberOnTheMessageIsReplaced() throws Exception {
        String topic = uniqueTopic("unsafe");
        BlockingQueue<String> seen = new LinkedBlockingQueue<>();
        listen(topic, record -> seen.add(String.valueOf(MDC.get("traceId"))),
                new TraceIdRecordInterceptor<>(), null);

        ProducerRecord<String, String> hostile = new ProducerRecord<>(topic, "key", "hello");
        hostile.headers().add("X-Trace-Id", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
        producer().send(hostile).get(20, TimeUnit.SECONDS);

        String used = next(seen);
        assertNotEquals("<script>alert(1)</script>", used);
        assertEquals(8, used.length());
    }

    // The thread is reused for the next message. The number must be wiped after each one.
    @Test
    void theNotepadIsWipedAfterEveryMessage() throws Exception {
        String topic = uniqueTopic("wipe");
        TraceIdRecordInterceptor<String, String> ours = new TraceIdRecordInterceptor<>();
        BlockingQueue<String> afterRecord = new LinkedBlockingQueue<>();

        // A tiny wrapper, only to LOOK at the notepad right after our afterRecord ran.
        RecordInterceptor<String, String> watching = new RecordInterceptor<>() {
            @Override
            public ConsumerRecord<String, String> intercept(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
                return ours.intercept(record, consumer);
            }

            @Override
            public void afterRecord(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
                ours.afterRecord(record, consumer);
                afterRecord.add(String.valueOf(MDC.get("traceId")));
            }
        };
        listen(topic, record -> { }, watching, null);

        MDC.put("traceId", "wipe-me-004");
        producer().send(topic, "key", "hello").get(20, TimeUnit.SECONDS);

        assertEquals("null", next(afterRecord), "after the message, the notepad must be empty again");
    }

    // The important failure case: the listener always throws. Every retry, every log line the
    // error handler writes, and the dead-letter copy must all carry the ORIGINAL number.
    @Test
    void retriesLogLinesAndTheDeadLetterCopyAllKeepTheNumber() throws Exception {
        String topic = uniqueTopic("failing");
        String deadLetterTopic = uniqueTopic("failing-dead");

        // The real B2 shape: retry 2 more times, then park the message in the dead-letter topic.
        KafkaTemplate<Object, Object> deadLetterTemplate =
                new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps()));
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(deadLetterTemplate,
                (record, exception) -> new TopicPartition(deadLetterTopic, -1));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(50L, 2L));

        // Capture every log line Spring Kafka's listener code writes, WITH the notepad as it was
        // at that moment.
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);

        BlockingQueue<String> attempts = new LinkedBlockingQueue<>();
        BlockingQueue<String> afterRecord = new LinkedBlockingQueue<>();
        TraceIdRecordInterceptor<String, String> ours = new TraceIdRecordInterceptor<>();
        RecordInterceptor<String, String> watching = new RecordInterceptor<>() {
            @Override
            public ConsumerRecord<String, String> intercept(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
                return ours.intercept(record, consumer);
            }

            @Override
            public void afterRecord(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
                ours.afterRecord(record, consumer);
                afterRecord.add(String.valueOf(MDC.get("traceId")));
            }
        };

        try {
            listen(topic, record -> {
                attempts.add(String.valueOf(MDC.get("traceId")));
                throw new IllegalStateException("this listener always fails");
            }, watching, errorHandler);

            MDC.put("traceId", "dlt-trace-005");
            producer().send(topic, "key", "poison").get(20, TimeUnit.SECONDS);
            MDC.remove("traceId");

            // The dead-letter copy arrives, still carrying the original number.
            try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<String, String>(
                    consumerProps("dlt-reader-" + UUID.randomUUID())).createConsumer()) {
                consumer.subscribe(List.of(deadLetterTopic));
                ConsumerRecord<String, String> parked = KafkaTestUtils.getSingleRecord(consumer, deadLetterTopic, Duration.ofSeconds(30));
                assertEquals("dlt-trace-005", headerOf(parked), "the dead-letter copy must keep the original number");
                assertEquals("poison", parked.value());
            }

            // 1 first try + 2 retries = 3 attempts, and every one saw the same number.
            for (int attempt = 1; attempt <= 3; attempt++) {
                assertEquals("dlt-trace-005", next(attempts), "attempt " + attempt);
            }
            assertNull(attempts.poll(1, TimeUnit.SECONDS), "no 4th attempt");

            // The error handler's own log lines ("Record in retry and not yet recovered") happened
            // while the number was still on the notepad - so the log format prints it on them too.
            List<ILoggingEvent> retryLogs = new ArrayList<>();
            for (ILoggingEvent event : new ArrayList<>(logs.list)) {
                if (event.getLoggerName().startsWith("org.springframework.kafka.listener")
                        && event.getFormattedMessage().contains("retry")) {
                    retryLogs.add(event);
                }
            }
            assertTrue(retryLogs.size() >= 2, "expected a log line for each of the 2 retries, got " + retryLogs.size());
            for (ILoggingEvent event : retryLogs) {
                assertEquals("dlt-trace-005", event.getMDCPropertyMap().get("traceId"),
                        "a retry log line lost the number: " + event.getFormattedMessage());
            }

            // ...and it is wiped only AFTER all of that.
            assertEquals("null", next(afterRecord));
        } finally {
            root.detachAppender(logs);
        }
    }
}
