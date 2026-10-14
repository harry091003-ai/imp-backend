package com.example.AuthService;

import com.project.events.UpdatingRoleEvent;
import com.project.events.UpdatingUserEvent;
import com.project.events.UserLoggedInEvent;
import com.project.events.UserLoggedOutAtEvent;
import com.project.events.UserRegisteredEvent;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox step 3: each REAL AuthService event goes into the outbox, through the REAL relay, into a
 * REAL Kafka broker (started inside the test), and is read back the way UserService reads it
 * (Spring's JsonDeserializer, only the com.project.events package trusted, type from the header).
 *
 * This proves what the unit tests cannot: the relay can REBUILD each event class from the saved
 * JSON, and the consumers get exactly the same object - so UserService needs no change.
 */
class AuthEventsOverKafkaTest {

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
                "jdbc:h2:mem:auth-kafka-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("outbox/outbox_event_mysql.sql")).execute(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DataSourceTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        tx = new TransactionTemplate(txManager);
        writer = new OutboxWriter(jdbc, JacksonUtils.enhancedObjectMapper());

        // The same producer settings AuthService has in application.properties.
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

    /** Save the event in the outbox, relay it, and read it back like UserService does. */
    private ConsumerRecord<String, Object> roundTrip(String topic, String key, Object event) {
        tx.executeWithoutResult(status -> writer.save(topic, key, event));
        assertThat(relay.publishBatch()).as("the relay sent the event").isEqualTo(1);

        Map<String, Object> props = KafkaTestUtils.consumerProps(broker.getBrokersAsString(),
                "reader-" + UUID.randomUUID(), "false");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.project.events");     // like UserService
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
    void userRegisteredArrivesAsTheSameObject() {
        UUID userId = UUID.randomUUID();
        UserRegisteredEvent event = UserRegisteredEvent.builder().userid(userId).username("alice")
                .email("alice@example.com").roles(Set.of("ROLE_ENGINEER")).createdAt(SOME_TIME)
                .updatedAt(SOME_TIME).lastLoginAt(null).passwordUpdatedAt(SOME_TIME).build();

        ConsumerRecord<String, Object> record = roundTrip(topic("user.registered"), userId.toString(), event);

        assertThat(record.key()).isEqualTo(userId.toString());
        assertThat(record.value()).isInstanceOf(UserRegisteredEvent.class).usingRecursiveComparison().isEqualTo(event);
        assertThat(eventIdHeader(record)).isNotBlank();
    }

    @Test
    void userLoggedInArrivesAsTheSameObject() {
        UUID userId = UUID.randomUUID();
        UserLoggedInEvent event = UserLoggedInEvent.builder().userid(userId).userLastLoggedInAt(SOME_TIME).build();

        ConsumerRecord<String, Object> record = roundTrip(topic("user.logged-in"), userId.toString(), event);

        assertThat(record.value()).isInstanceOf(UserLoggedInEvent.class).usingRecursiveComparison().isEqualTo(event);
    }

    @Test
    void userLoggedOutArrivesAsTheSameObject() {
        UUID userId = UUID.randomUUID();
        UserLoggedOutAtEvent event = UserLoggedOutAtEvent.builder().userid(userId).userLastLoggedOutAt(SOME_TIME).build();

        ConsumerRecord<String, Object> record = roundTrip(topic("user.logged-out"), userId.toString(), event);

        assertThat(record.value()).isInstanceOf(UserLoggedOutAtEvent.class).usingRecursiveComparison().isEqualTo(event);
    }

    @Test
    void userUpdatedArrivesAsTheSameObject() {
        UUID userId = UUID.randomUUID();
        // This class has fields named UpdatedUserName / UpdatedEmail (capital U): worth proving.
        UpdatingUserEvent event = UpdatingUserEvent.builder().userId(userId).UpdatedUserName("alice2")
                .UpdatedEmail("alice2@example.com").build();

        ConsumerRecord<String, Object> record = roundTrip(topic("user.Updated"), userId.toString(), event);

        assertThat(record.value()).isInstanceOf(UpdatingUserEvent.class).usingRecursiveComparison().isEqualTo(event);
        assertThat(((UpdatingUserEvent) record.value()).getUpdatedEmail()).isEqualTo("alice2@example.com");
    }

    @Test
    void rolesUpdateArrivesAsTheSameObject() {
        UUID userId = UUID.randomUUID();
        UpdatingRoleEvent event = UpdatingRoleEvent.builder().username("alice")
                .role(Set.of("ROLE_SUPERVISOR", "ROLE_ENGINEER")).build();

        ConsumerRecord<String, Object> record = roundTrip(topic("roles.update"), userId.toString(), event);

        assertThat(record.key()).isEqualTo(userId.toString());
        assertThat(record.value()).isInstanceOf(UpdatingRoleEvent.class).usingRecursiveComparison().isEqualTo(event);
    }
}
