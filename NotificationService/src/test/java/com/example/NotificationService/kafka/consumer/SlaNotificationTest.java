package com.example.NotificationService.kafka.consumer;

import com.example.NotificationService.Entity.EventType;
import com.example.NotificationService.Entity.NotificationLog;
import com.example.NotificationService.Entity.NotificationStatus;
import com.example.NotificationService.client.UserClient;
import com.example.NotificationService.dto.UserCredentialDTO;
import com.example.NotificationService.repository.NotificationLogRepository;
import com.example.NotificationService.service.SlaNotificationService;
import com.project.events.SLA_BreachedEvent;
import com.project.events.enumsUsedByEvents.SlaBreachingRiskEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Step 5: the 4 SLA listeners. Real code, in-memory database, fake UserService.
 * Rule: tell the creator, the engineer (if any) and the supervisor. Each person once. No email.
 * Written first: against an empty skeleton the tests failed.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification-sla;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SlaEventConsumer.class, SlaNotificationService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // like a real Kafka listener: no transaction around it
class SlaNotificationTest {

    @Autowired SlaEventConsumer consumer;
    @Autowired NotificationLogRepository repository;
    @MockitoBean UserClient userClient;

    // The people of the story.
    private static final UUID CREATOR = UUID.randomUUID();
    private static final UUID CREATORS_BOSS = UUID.randomUUID();
    private static final UUID ENGINEER = UUID.randomUUID();
    private static final UUID ENGINEERS_BOSS = UUID.randomUUID();

    /** What the fake UserService knows: id -> (name, supervisor). */
    private final Map<UUID, UserCredentialDTO> userService = new HashMap<>();
    /** For each call to UserService: was a database transaction open at that moment? */
    private final List<Boolean> calledInsideTransaction = new ArrayList<>();

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        userService.clear();
        calledInsideTransaction.clear();
        knows(CREATOR, "creator", CREATORS_BOSS);
        knows(CREATORS_BOSS, "creatorsboss", null);
        knows(ENGINEER, "engineer", ENGINEERS_BOSS);
        knows(ENGINEERS_BOSS, "engineersboss", null);
        when(userClient.getBatchOfUsers_Credentials(any())).thenAnswer(call -> {
            calledInsideTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
            List<UUID> ids = call.getArgument(0);
            return ids.stream().filter(userService::containsKey).map(userService::get).toList();
        });
    }

    private void knows(UUID id, String name, UUID supervisor) {
        userService.put(id, UserCredentialDTO.builder().userid(id).username(name).email(name + "@example.com")
                .supervisorId(supervisor).build());
    }

    // ------------------------------------------------------------------ helpers

    private static byte[] header(UUID eventId) {
        return eventId.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static SLA_BreachedEvent breach(UUID createdBy, UUID assignedTo) {
        return SLA_BreachedEvent.builder().incidentId(UUID.randomUUID()).SLA_BreachedType("X")
                .createdBy(createdBy).assignedTo(assignedTo).build();
    }

    private static SlaBreachingRiskEvent risk(UUID createdBy, UUID assignedTo) {
        return SlaBreachingRiskEvent.builder().incidentId(UUID.randomUUID()).previousRiskLevel("HIGH")
                .currentRiskLevel("CRITICAL").riskScore(0.95).type("X").createdBy(createdBy).assignedTo(assignedTo).build();
    }

    private Set<UUID> recipients() {
        return repository.findAll().stream().map(NotificationLog::getRecipientId).collect(Collectors.toSet());
    }

    // ------------------------------------------------------------------ who is told

    @Test
    void anAssignmentBreachTellsTheCreatorAndTheCreatorsSupervisor() {
        UUID eventId = UUID.randomUUID();
        SLA_BreachedEvent event = breach(CREATOR, null);   // nobody assigned - that is why it breached

        consumer.consumeAssignmentBreach(event, header(eventId));

        assertEquals(Set.of(CREATOR, CREATORS_BOSS), recipients());
        for (NotificationLog row : repository.findAll()) {
            assertEquals(EventType.SLA_ASSIGNMENT_BREACHED, row.getEventType());
            assertEquals(NotificationStatus.RECORDED, row.getStatus(), "saved, no email sent");
            assertEquals(eventId, row.getEventId(), "the id from the X-Event-Id header");
            assertEquals(event.getIncidentId(), row.getIncidentId());
            assertNull(row.getSentAt(), "nothing was sent");
            assertNotNull(row.getRecipientEmail());
            assertTrue(row.getMessage().contains(event.getIncidentId().toString()));
        }
    }

    @Test
    void aResolutionBreachTellsTheCreatorTheEngineerAndTheEngineersSupervisor() {
        consumer.consumeResolutionBreach(breach(CREATOR, ENGINEER), header(UUID.randomUUID()));

        assertEquals(Set.of(CREATOR, ENGINEER, ENGINEERS_BOSS), recipients(),
                "the ENGINEER's supervisor is told - not the creator's, once someone is assigned");
        assertTrue(repository.findAll().stream().allMatch(r -> r.getEventType() == EventType.SLA_RESOLUTION_BREACHED));
    }

    @Test
    void anAtRiskEventIsSavedWithItsLevelsInTheMessage() {
        consumer.consumeAssignmentRisk(risk(CREATOR, null), header(UUID.randomUUID()));
        consumer.consumeResolutionRisk(risk(CREATOR, ENGINEER), header(UUID.randomUUID()));

        List<NotificationLog> rows = repository.findAll();
        assertEquals(2 + 3, rows.size());
        assertEquals(2, rows.stream().filter(r -> r.getEventType() == EventType.SLA_ASSIGNMENT_AT_RISK).count());
        assertEquals(3, rows.stream().filter(r -> r.getEventType() == EventType.SLA_RESOLUTION_AT_RISK).count());
        assertTrue(rows.get(0).getMessage().contains("HIGH -> CRITICAL"), rows.get(0).getMessage());
        assertTrue(rows.get(0).getMessage().contains("95%"), rows.get(0).getMessage());
        assertTrue(rows.stream().allMatch(r -> r.getMessage().length() <= 255), "fits the varchar(255) column");
    }

    @Test
    void eachPersonIsToldOnlyOnce() {
        // The creator is also the engineer, and the engineer's supervisor is also... the creator's supervisor.
        knows(ENGINEER, "engineer", CREATORS_BOSS);

        consumer.consumeResolutionBreach(breach(ENGINEER, ENGINEER), header(UUID.randomUUID()));

        assertEquals(Set.of(ENGINEER, CREATORS_BOSS), recipients());
        assertEquals(2, repository.count(), "one row per PERSON, not per role");
    }

    @Test
    void someoneWithoutASupervisorIsSimplySkipped() {
        UUID loner = UUID.randomUUID();
        knows(loner, "loner", null);

        consumer.consumeAssignmentBreach(breach(loner, null), header(UUID.randomUUID()));

        assertEquals(Set.of(loner), recipients());
    }

    // ------------------------------------------------------------------ repeats (the inbox)

    @Test
    void theSameMessageTwiceIsSavedOnlyOnce() {
        UUID eventId = UUID.randomUUID();
        SLA_BreachedEvent event = breach(CREATOR, ENGINEER);

        consumer.consumeResolutionBreach(event, header(eventId));
        consumer.consumeResolutionBreach(event, header(eventId));   // Kafka delivers it again

        assertEquals(3, repository.count());
    }

    @Test
    void withoutTheHeaderARepeatIsStillCaught() {
        SLA_BreachedEvent event = breach(CREATOR, null);

        consumer.consumeAssignmentBreach(event, null);
        consumer.consumeAssignmentBreach(event, null);

        assertEquals(2, repository.count(), "the same message works out the same id, so the repeat is skipped");
    }

    @Test
    void withoutTheHeaderTwoDifferentRiskLevelsAreTwoNotifications() {
        SlaBreachingRiskEvent high = risk(CREATOR, null);
        high.setCurrentRiskLevel("HIGH");
        SlaBreachingRiskEvent critical = risk(CREATOR, null);
        critical.setIncidentId(high.getIncidentId());   // same incident, the level went up

        consumer.consumeAssignmentRisk(high, null);
        consumer.consumeAssignmentRisk(critical, null);

        assertEquals(4, repository.count(), "HIGH and CRITICAL are two different alerts (2 people each)");
    }

    // ------------------------------------------------------------------ rules and failures

    @Test
    void userServiceIsCalledWithNoDatabaseTransactionOpen() {
        consumer.consumeResolutionBreach(breach(CREATOR, ENGINEER), header(UUID.randomUUID()));

        assertFalse(calledInsideTransaction.isEmpty());
        assertFalse(calledInsideTransaction.contains(true), "never hold a database transaction while waiting for another service");
    }

    @Test
    void anOldMessageWithoutTheCreatorSavesNothingAndDoesNotFail() {
        // A message sent before step 2 has no createdBy / assignedTo.
        consumer.consumeAssignmentBreach(breach(null, null), header(UUID.randomUUID()));

        assertEquals(0, repository.count());
        verify(userClient, never()).getBatchOfUsers_Credentials(any());
    }

    @Test
    void ifUserServiceFailsNothingIsSavedAndTheErrorGoesBackToKafka() {
        // doThrow-when: replaces the fake answer without calling it (when(...) would run the old answer once)
        doThrow(new RuntimeException("UserService is down")).when(userClient).getBatchOfUsers_Credentials(any());

        assertThrows(RuntimeException.class,
                () -> consumer.consumeResolutionBreach(breach(CREATOR, ENGINEER), header(UUID.randomUUID())));

        assertEquals(0, repository.count(), "Kafka retries the message later (then the dead-letter topic)");
    }
}
