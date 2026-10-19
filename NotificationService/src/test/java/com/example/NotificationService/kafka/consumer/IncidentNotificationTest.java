package com.example.NotificationService.kafka.consumer;

import com.example.NotificationService.Entity.EventType;
import com.example.NotificationService.Entity.NotificationLog;
import com.example.NotificationService.Entity.NotificationStatus;
import com.example.NotificationService.client.UserClient;
import com.example.NotificationService.dto.UserCredentialDTO;
import com.example.NotificationService.repository.NotificationLogRepository;
import com.example.NotificationService.service.EmailNoticationService;
import com.project.events.IncidentAssignedEvent;
import com.project.events.IncidentCreatedEvent;
import com.project.events.IncidentStatusUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Step 6: the 3 incident listeners save honest rows.
 * Each row: the user id, status RECORDED, no "sent" time, a correct message without an email address.
 * Real code, in-memory database, fake UserService. Written first: 3 of 4 failed on the old code.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification-incident;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({IncidentEventConsumer.class, EmailNoticationService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IncidentNotificationTest {

    @Autowired IncidentEventConsumer consumer;
    @Autowired NotificationLogRepository repository;
    @MockitoBean UserClient userClient;

    private static final UUID CREATOR = UUID.randomUUID();
    private static final UUID SUPERVISOR = UUID.randomUUID();
    private static final UUID ENGINEER = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        // The fake UserService knows these three people.
        when(userClient.getBatchOfUsers_Credentials(any())).thenAnswer(call -> {
            List<UUID> ids = call.getArgument(0);
            return ids.stream().map(id -> UserCredentialDTO.builder().userid(id)
                    .username("user-" + id.toString().substring(0, 4)).email(id + "@example.com").build()).toList();
        });
    }

    private Set<UUID> recipients() {
        return repository.findAll().stream().map(NotificationLog::getRecipientId).collect(Collectors.toSet());
    }

    /** What every new row must look like, whatever the event. */
    private void everyRowIsHonest(EventType type) {
        List<NotificationLog> rows = repository.findAll();
        assertFalse(rows.isEmpty());
        for (NotificationLog row : rows) {
            assertEquals(type, row.getEventType());
            assertNotNull(row.getRecipientId(), "the user id is saved, so 'show MY notifications' finds it");
            assertEquals(NotificationStatus.RECORDED, row.getStatus(), "saved, no email sent - not SUCCESS");
            assertNull(row.getSentAt(), "nothing was sent");
            assertFalse(row.getMessage().contains("@"), "no email address in the text a user will read: " + row.getMessage());
            assertFalse(row.getMessage().toLowerCase().contains("sent"), "no claim that an email was sent: " + row.getMessage());
            assertTrue(row.getMessage().length() <= 255, "fits the varchar(255) column");
        }
    }

    @Test
    void anIncidentCreatedNotificationIsHonestAndKnowsItsUser() {
        UUID incidentId = UUID.randomUUID();

        consumer.consumeIncidentCreatedEvent(IncidentCreatedEvent.builder().incidentId(incidentId)
                .eventId(UUID.randomUUID()).performedBy(CREATOR).createdBy(CREATOR).title("t").build());

        assertEquals(Set.of(CREATOR), recipients(), "only the creator, as before");
        everyRowIsHonest(EventType.INCIDENT_CREATED);
        assertEquals("Incident " + incidentId + " was created.", repository.findAll().get(0).getMessage());
    }

    @Test
    void anIncidentAssignedNotificationSaysAssignedNotCreated() {
        UUID incidentId = UUID.randomUUID();

        consumer.consumeIncidentAssignedEvent(IncidentAssignedEvent.builder().incidentId(incidentId)
                .eventId(UUID.randomUUID()).performedBy(SUPERVISOR).createdBy(CREATOR).assignedBy(SUPERVISOR)
                .assignedTo(ENGINEER).title("t").build());

        assertEquals(Set.of(CREATOR, SUPERVISOR, ENGINEER), recipients(), "the same 3 people as before");
        everyRowIsHonest(EventType.INCIDENT_ASSIGNED);
        assertTrue(repository.findAll().stream().allMatch(r -> r.getMessage().equals("Incident " + incidentId + " was assigned.")),
                "the old text said 'Incident Created' for every event type");
    }

    @Test
    void aStatusChangeNotificationShowsTheOldAndTheNewStatus() {
        UUID incidentId = UUID.randomUUID();

        consumer.consumeIncidentStatusUpdatedEvent(IncidentStatusUpdatedEvent.builder().incidentId(incidentId)
                .eventId(UUID.randomUUID()).performedBy(ENGINEER).createdBy(CREATOR).assignedBy(SUPERVISOR)
                .assignedTo(ENGINEER).OldStatus("ASSIGNED").UpdatedStatus("RESOLVED").title("t").build());

        assertEquals(Set.of(CREATOR, SUPERVISOR, ENGINEER), recipients());
        everyRowIsHonest(EventType.INCIDENT_STATUS_UPDATED);
        assertTrue(repository.findAll().stream().allMatch(r ->
                r.getMessage().equals("Incident " + incidentId + ": status changed from ASSIGNED to RESOLVED.")));
    }

    @Test
    void theSameEventTwiceIsStillSavedOnlyOnce() {
        // The duplicate guard (unique event id + email) must keep working after the change.
        IncidentCreatedEvent event = IncidentCreatedEvent.builder().incidentId(UUID.randomUUID())
                .eventId(UUID.randomUUID()).performedBy(CREATOR).createdBy(CREATOR).title("t").build();

        consumer.consumeIncidentCreatedEvent(event);
        consumer.consumeIncidentCreatedEvent(event);   // Kafka delivers it again

        assertEquals(1, repository.count());
    }
}
