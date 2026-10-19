package com.example.NotificationService.Entity;

import com.example.NotificationService.client.UserClient;
import com.example.NotificationService.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Step 4: the Java side of the table change, on an in-memory database.
 * The new types, status and recipientId save and load; old rows still work; the duplicate guard still holds.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification-store;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // each save is its own real transaction
class NotificationLogStoreTest {

    @Autowired NotificationLogRepository repository;

    @MockitoBean UserClient userClient;   // the service's Feign client: not used here, just not started

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private static NotificationLog row(EventType type, NotificationStatus status, UUID recipientId, UUID eventId,
                                       String email) {
        return NotificationLog.builder().eventId(eventId).incidentId(UUID.randomUUID()).performedBy(UUID.randomUUID())
                .eventType(type).status(status).recipientId(recipientId).recipientEmail(email).recipientName("eva")
                .message("test").createdAt(LocalDateTime.now()).build();
    }

    @Test
    void everyNewSlaTypeIsSavedAndReadBackWithTheRecipientId() {
        UUID eva = UUID.randomUUID();
        for (EventType type : new EventType[]{EventType.SLA_ASSIGNMENT_BREACHED, EventType.SLA_RESOLUTION_BREACHED,
                EventType.SLA_ASSIGNMENT_AT_RISK, EventType.SLA_RESOLUTION_AT_RISK}) {
            NotificationLog saved = repository.save(
                    row(type, NotificationStatus.RECORDED, eva, UUID.randomUUID(), "eva@example.com"));

            NotificationLog read = repository.findById(saved.getId()).orElseThrow();
            assertEquals(type, read.getEventType());
            assertEquals(NotificationStatus.RECORDED, read.getStatus());
            assertEquals(eva, read.getRecipientId(), "the user id is kept, so 'show MY notifications' can work");
        }
        assertEquals(4, repository.count());
    }

    @Test
    void anOldStyleRowWithoutARecipientIdStillWorks() {
        // Rows written before this change have no recipient_id. They must still save and load.
        NotificationLog saved = repository.save(
                row(EventType.INCIDENT_CREATED, NotificationStatus.SUCCESS, null, UUID.randomUUID(), "old@example.com"));

        NotificationLog read = repository.findById(saved.getId()).orElseThrow();
        assertNull(read.getRecipientId());
        assertEquals(EventType.INCIDENT_CREATED, read.getEventType());
        assertEquals(NotificationStatus.SUCCESS, read.getStatus());
    }

    @Test
    void theSameEventForTheSameEmailIsStillRefusedTheSecondTime() {
        // The duplicate guard the incident listeners rely on (unique event_id + recipient_email) must survive.
        UUID eventId = UUID.randomUUID();
        repository.saveAndFlush(row(EventType.SLA_ASSIGNMENT_BREACHED, NotificationStatus.RECORDED, UUID.randomUUID(),
                eventId, "eva@example.com"));

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(
                row(EventType.SLA_ASSIGNMENT_BREACHED, NotificationStatus.RECORDED, UUID.randomUUID(),
                        eventId, "eva@example.com")));
        assertEquals(1, repository.count());
    }

    @Test
    void theSameEventForTwoDifferentPeopleIsTwoRows() {
        // One SLA alert -> creator, engineer, supervisor: one row each.
        UUID eventId = UUID.randomUUID();
        repository.save(row(EventType.SLA_RESOLUTION_BREACHED, NotificationStatus.RECORDED, UUID.randomUUID(),
                eventId, "creator@example.com"));
        repository.save(row(EventType.SLA_RESOLUTION_BREACHED, NotificationStatus.RECORDED, UUID.randomUUID(),
                eventId, "engineer@example.com"));

        assertEquals(2, repository.count());
    }
}
