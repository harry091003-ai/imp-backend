package com.project.outbox;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.JacksonUtils;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class OutboxSchedulerTest {

    @Test
    void aFailingRoundNeverEscapes() {
        // A database without the outbox table: every relay call fails.
        OutboxTestDb db = OutboxTestDb.create();
        db.jdbc.execute("DROP TABLE outbox_event");
        OutboxRelay relay = new OutboxRelay(db.jdbc, db.transactionManager,
                new DefaultKafkaProducerFactory<>(Map.of(
                        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1",
                        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class)),
                new OutboxProperties(), JacksonUtils.enhancedObjectMapper(), getClass().getClassLoader());
        OutboxScheduler scheduler = new OutboxScheduler(relay, new OutboxProperties());

        // If this threw, a ScheduledExecutorService would stop running the relay forever.
        assertDoesNotThrow(scheduler::round);
        relay.close();
    }
}
