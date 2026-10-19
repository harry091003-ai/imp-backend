package com.example.NotificationService.kafka.consumer;

import com.example.NotificationService.Entity.EventType;
import com.example.NotificationService.Entity.NotificationLog;
import com.example.NotificationService.client.UserClient;
import com.example.NotificationService.dto.UserCredentialDTO;
import com.example.NotificationService.repository.NotificationLogRepository;
import com.project.events.SLA_BreachedEvent;
import com.project.events.enumsUsedByEvents.SlaBreachingRiskEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Step 5: the whole service with a small Kafka started inside the test.
 * Proves a message sent like the outbox sends it reaches the right listener (topic names, the
 * sub-package fix of issue 65, the X-Event-Id header). Fake database and UserService.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification-kafka;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "internalsecret=dummy-test-only",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"   // the listeners may join after the message is sent
})
@EmbeddedKafka(kraft = true, partitions = 1, topics = {
        "Sla-Assignment-Breach-Topic", "Sla-Resolution-Breach-Topic",
        "Sla-Assignment-BreachingRisk-Topic", "Sla-Resolution-BreachingRisk-Topic",
        "incident.created", "incident.assigned", "incident.status.updated"})
class SlaListenersOverKafkaTest {

    @Autowired EmbeddedKafkaBroker broker;
    @Autowired NotificationLogRepository repository;
    @MockitoBean UserClient userClient;

    private static final UUID CREATOR = UUID.randomUUID();
    private static final UUID BOSS = UUID.randomUUID();

    /** Send like ReportingService's outbox does: JSON + class-name header + X-Event-Id header. */
    private void send(String topic, String key, Object event, UUID eventId) throws Exception {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        KafkaTemplate<String, Object> kafka = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);
        record.headers().add("X-Event-Id", eventId.toString().getBytes(StandardCharsets.UTF_8));
        kafka.send(record).get();
        kafka.destroy();
    }

    /** Wait (up to 30 s) until this many rows exist for this event id. */
    private List<NotificationLog> waitForRows(UUID eventId, int expected) throws InterruptedException {
        List<NotificationLog> rows = List.of();
        for (int i = 0; i < 300 && rows.size() < expected; i++) {
            Thread.sleep(100);
            rows = repository.findAll().stream().filter(r -> eventId.equals(r.getEventId())).toList();
        }
        return rows;
    }

    private void userServiceKnowsTheCreatorAndTheBoss() {
        when(userClient.getBatchOfUsers_Credentials(any())).thenAnswer(call -> {
            List<UUID> ids = call.getArgument(0);
            return ids.stream().map(id -> id.equals(CREATOR)
                    ? UserCredentialDTO.builder().userid(CREATOR).username("creator").email("creator@example.com").supervisorId(BOSS).build()
                    : UserCredentialDTO.builder().userid(BOSS).username("boss").email("boss@example.com").build()).toList();
        });
    }

    @Test
    void aBreachMessageFromKafkaIsRecordedForTheCreatorAndTheSupervisor() throws Exception {
        userServiceKnowsTheCreatorAndTheBoss();
        UUID incidentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        send("Sla-Assignment-Breach-Topic", incidentId.toString(), SLA_BreachedEvent.builder().incidentId(incidentId)
                .SLA_BreachedType("ASSIGNMENT_SLA_BREACHED").createdBy(CREATOR).build(), eventId);

        List<NotificationLog> rows = waitForRows(eventId, 2);
        assertEquals(2, rows.size(), "creator + supervisor, using the X-Event-Id from the header");
        assertTrue(rows.stream().allMatch(r -> r.getEventType() == EventType.SLA_ASSIGNMENT_BREACHED));
        assertTrue(rows.stream().allMatch(r -> incidentId.equals(r.getIncidentId())));
    }

    @Test
    void anAtRiskMessageFromTheSubPackageIsAcceptedAndRecorded() throws Exception {
        userServiceKnowsTheCreatorAndTheBoss();
        UUID incidentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        send("Sla-Resolution-BreachingRisk-Topic", incidentId.toString(), SlaBreachingRiskEvent.builder()
                .incidentId(incidentId).previousRiskLevel("NONE").currentRiskLevel("HIGH").riskScore(0.75)
                .type("RESOLUTION_SLA_BREACHING_RISK").createdBy(CREATOR).build(), eventId);

        List<NotificationLog> rows = waitForRows(eventId, 2);
        assertEquals(2, rows.size(), "without the issue-65 fix this class would be rejected and land in the dead-letter topic");
        assertTrue(rows.stream().allMatch(r -> r.getEventType() == EventType.SLA_RESOLUTION_AT_RISK));
        assertTrue(rows.get(0).getMessage().contains("NONE -> HIGH, 75%"), rows.get(0).getMessage());
    }
}
