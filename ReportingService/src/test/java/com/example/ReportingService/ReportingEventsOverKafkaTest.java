package com.example.ReportingService;

import com.project.events.IncidentResolutionReportEvent;
import com.project.events.SLA_BreachedEvent;
import com.project.events.enumsUsedByEvents.SlaBreachingRiskEvent;
import com.project.outbox.OutboxHeaders;
import com.project.outbox.OutboxProperties;
import com.project.outbox.OutboxRelay;
import com.project.outbox.OutboxWriter;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.JacksonUtils;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Outbox step 4: each event ReportingService sends goes into the outbox, through the REAL relay, into a
 * REAL Kafka broker (started inside the test), and is read back the way a consumer reads it (Spring's
 * JsonDeserializer, only the com.project.events package trusted, type from the header) - like Ai-Service
 * does for the resolved report.
 *
 * This proves what ReportingOutboxTest cannot: the relay can REBUILD each event class from the saved JSON,
 * and the reader gets exactly the same object - so Ai-Service needs no change.
 */
class ReportingEventsOverKafkaTest {

    private static EmbeddedKafkaBroker broker;

    private OutboxWriter writer;
    private OutboxRelay relay;
    private TransactionTemplate tx;

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
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:reporting-kafka-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("outbox/outbox_event_mysql.sql")).execute(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DataSourceTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        tx = new TransactionTemplate(txManager);
        writer = new OutboxWriter(jdbc, JacksonUtils.enhancedObjectMapper());

        // The same producer settings ReportingService has in application.properties.
        Map<String, Object> producer = new HashMap<>();
        producer.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        producer.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producer.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        relay = new OutboxRelay(jdbc, txManager, new DefaultKafkaProducerFactory<>(producer),
                new OutboxProperties(), JacksonUtils.enhancedObjectMapper(), getClass().getClassLoader());
    }

    @AfterEach
    void tearDown() {
        relay.close();
    }

    private static String topic(String name) {
        String topic = name + "-" + UUID.randomUUID().toString().substring(0, 6);
        broker.addTopics(topic);
        return topic;
    }

    /** The trusted-packages value every consumer service has in application.properties today. */
    private static final String TRUSTED_TODAY = "com.project.events";

    /** Save the event in the outbox, relay it, and read it back like Ai-Service does. */
    private ConsumerRecord<String, Object> roundTrip(String topic, String key, Object event) {
        return roundTrip(topic, key, event, TRUSTED_TODAY);
    }

    private ConsumerRecord<String, Object> roundTrip(String topic, String key, Object event, String trustedPackages) {
        tx.executeWithoutResult(status -> writer.save(topic, key, event));
        assertThat(relay.publishBatch()).as("the relay sent the event").isEqualTo(1);

        Map<String, Object> props = KafkaTestUtils.consumerProps(broker.getBrokersAsString(),
                "reader-" + UUID.randomUUID(), "false");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, trustedPackages);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, Object> consumer =
                     new DefaultKafkaConsumerFactory<String, Object>(props).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, topic);
            return KafkaTestUtils.getSingleRecord(consumer, topic, Duration.ofSeconds(20));
        }
    }

    private static String eventIdHeader(ConsumerRecord<?, ?> record) {
        return new String(record.headers().lastHeader(OutboxHeaders.EVENT_ID).value(), StandardCharsets.UTF_8);
    }

    private static final LocalDateTime SOME_TIME = LocalDateTime.of(2026, 10, 13, 14, 5, 7, 123_000_000);

    @Test
    void resolvedReportArrivesAsTheSameObject() {
        UUID incidentId = UUID.randomUUID();
        IncidentResolutionReportEvent event = IncidentResolutionReportEvent.builder().incidentId(incidentId)
                .title("Payments DB down").description("Every payment fails").category("DATABASE")
                .serviceName("PAYMENT_SERVICE").rootCause("connection pool too small")
                .resolutionNotes("raised the pool to 50").resolvedBy(UUID.randomUUID())
                .resolutionTimeInSeconds(300).assignmentSlaBreached(true).resolutionSlaBreached(false)
                .resolvedAt(SOME_TIME).build();

        ConsumerRecord<String, Object> record =
                roundTrip(topic("Incident-Resolved-Report-Topic"), incidentId.toString(), event);

        assertThat(record.key()).isEqualTo(incidentId.toString());
        assertThat(record.value()).isInstanceOf(IncidentResolutionReportEvent.class)
                .usingRecursiveComparison().isEqualTo(event);
        assertThat(eventIdHeader(record)).isNotBlank();
    }

    @Test
    void slaBreachedArrivesAsTheSameObject() {
        // SLA_BreachedType starts with capital letters: exactly where a JSON naming surprise would show up.
        UUID incidentId = UUID.randomUUID();
        SLA_BreachedEvent event = SLA_BreachedEvent.builder().incidentId(incidentId)
                .SLA_BreachedType("ASSIGNMENT_SLA_BREACHED").build();

        ConsumerRecord<String, Object> record =
                roundTrip(topic("Sla-Assignment-Breach-Topic"), incidentId.toString(), event);

        assertThat(record.value()).isInstanceOf(SLA_BreachedEvent.class).usingRecursiveComparison().isEqualTo(event);
        assertThat(((SLA_BreachedEvent) record.value()).getSLA_BreachedType()).isEqualTo("ASSIGNMENT_SLA_BREACHED");
    }

    private static SlaBreachingRiskEvent riskEvent(UUID incidentId) {
        return SlaBreachingRiskEvent.builder().incidentId(incidentId)
                .previousRiskLevel("HIGH").currentRiskLevel("CRITICAL").riskScore(0.95)
                .type("RESOLUTION_SLA_BREACHING_RISK").build();
    }

    @Test
    void slaBreachingRiskArrivesAsTheSameObjectWhenItsPackageIsTrusted() {
        // This class lives one package deeper: com.project.events.enumsUsedByEvents. A reader must trust
        // that package BY NAME (see the next test for why).
        UUID incidentId = UUID.randomUUID();
        SlaBreachingRiskEvent event = riskEvent(incidentId);

        ConsumerRecord<String, Object> record = roundTrip(topic("Sla-Resolution-BreachingRisk-Topic"),
                incidentId.toString(), event, TRUSTED_TODAY + ",com.project.events.enumsUsedByEvents");

        assertThat(record.value()).isInstanceOf(SlaBreachingRiskEvent.class).usingRecursiveComparison().isEqualTo(event);
    }

    @Test
    void todaysTrustedSettingRejectsTheRiskEvent() {
        // FOUND 2026-10-13 (known issue 65): the consumers' application.properties say the trusted-packages
        // check is "starts with", so com.project.events "also covers" com.project.events.enumsUsedByEvents.
        // It does NOT - Spring Kafka matches the package exactly. No harm today (nobody reads the SLA topics,
        // known issue 18), but the first SLA-risk consumer will reject every message until this is fixed.
        // This test fails the day that changes - then update the comments and delete this test.
        UUID incidentId = UUID.randomUUID();

        assertThatThrownBy(() -> roundTrip(topic("Sla-Resolution-BreachingRisk-Topic"),
                incidentId.toString(), riskEvent(incidentId), TRUSTED_TODAY))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause().hasMessageContaining("not in the trusted packages");
    }
}
