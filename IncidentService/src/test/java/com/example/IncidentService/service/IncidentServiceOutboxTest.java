package com.example.IncidentService.service;

import com.example.IncidentService.client.UserClient;
import com.example.IncidentService.dto.CreateIncidentRequestDTO;
import com.example.IncidentService.dto.UpdateIncidentStatus_RequestDTO;
import com.example.IncidentService.dto.UserValidationDTO;
import com.example.IncidentService.entity.Environment;
import com.example.IncidentService.entity.Incident;
import com.example.IncidentService.entity.IncidentCategory;
import com.example.IncidentService.entity.IncidentStatus;
import com.example.IncidentService.entity.Priority;
import com.example.IncidentService.entity.ServiceName;
import com.example.IncidentService.entity.Severity;
import com.example.IncidentService.kafka.producer.IncidentEventProducer;
import com.example.IncidentService.repository.IncidentRepository;
import com.project.events.IncidentAssignedEvent;
import com.project.events.IncidentCreatedEvent;
import com.project.events.IncidentStatusUpdatedEvent;
import com.project.outbox.OutboxWriter;
import com.project.web.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.JacksonUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Outbox step 2: the REAL IncidentService code, with a real database (H2 in MySQL mode) and a fake
 * UserService. There is no Kafka here at all - the service must not need it any more.
 *
 * What it proves:
 *   - each change saves the incident AND one outbox row, in one transaction
 *   - if the outbox row cannot be saved, the incident is not saved either (both or neither)
 *   - UserService is always called OUTSIDE a database transaction
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:incident-outbox;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:outbox/outbox_event_mysql.sql"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({IncidentService.class, IncidentEventProducer.class, IncidentServiceOutboxTest.OutboxSetup.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // no test-wide transaction: we test the real ones
class IncidentServiceOutboxTest {

    @TestConfiguration
    static class OutboxSetup {
        @Bean
        OutboxWriter outboxWriter(DataSource dataSource) {
            return new OutboxWriter(new JdbcTemplate(dataSource), JacksonUtils.enhancedObjectMapper());
        }
    }

    @Autowired
    IncidentService incidentService;
    @Autowired
    IncidentRepository incidentRepository;
    @Autowired
    DataSource dataSource;

    @MockitoBean
    UserClient userClient;

    private JdbcTemplate jdbc;
    /** For every call to UserService: was a database transaction open at that moment? */
    private final List<Boolean> userServiceCalledInsideTransaction = new ArrayList<>();

    @BeforeEach
    void cleanTables() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM outbox_event");
        incidentRepository.deleteAll();
        userServiceCalledInsideTransaction.clear();
    }

    // ------------------------------------------------------------------ helpers

    /** The fake UserService: answers for each user, and remembers if a transaction was open. */
    private void userServiceKnows(Map<UUID, UserValidationDTO> users) {
        when(userClient.getUser(any())).thenAnswer(call -> {
            userServiceCalledInsideTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
            return users.get(call.getArgument(0, UUID.class));
        });
    }

    private static UserValidationDTO user(UUID id, String status, String... roles) {
        return UserValidationDTO.builder().userid(id).status(status).roles(Set.of(roles)).build();
    }

    private static Authentication loggedIn(UUID id, String role) {
        return new UsernamePasswordAuthenticationToken(id.toString(), null, List.of(new SimpleGrantedAuthority(role)));
    }

    private static CreateIncidentRequestDTO newIncident() {
        return CreateIncidentRequestDTO.builder()
                .title("Payments DB down").description("Every payment fails with a timeout")
                .severity(Severity.HIGH).priority(Priority.HIGH).environment(Environment.PRODUCTION)
                .serviceName(ServiceName.PAYMENT_SERVICE).category(IncidentCategory.DATABASE)
                .build();
    }

    private Incident existingIncident(UUID createdBy) {
        return incidentRepository.save(Incident.builder()
                .title("Login slow").description("Login takes 20 seconds")
                .severity(Severity.MEDIUM).priority(Priority.MEDIUM).environment(Environment.PRODUCTION)
                .serviceName(ServiceName.AUTH_SERVICE).category(IncidentCategory.API)
                .createdBy(createdBy).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build());
    }

    private List<Map<String, Object>> outboxRows() {
        return jdbc.queryForList("SELECT * FROM outbox_event ORDER BY seq");
    }

    // ------------------------------------------------------------------ tests

    @Test
    void creatingAnIncidentSavesTheIncidentAndOneOutboxRow() {
        UUID engineer = UUID.randomUUID();
        userServiceKnows(Map.of(engineer, user(engineer, "ACTIVE", "ROLE_ENGINEER")));

        UUID incidentId = incidentService.CreateIncident(engineer, newIncident()).getIncidentId();

        assertTrue(incidentRepository.existsById(incidentId));
        List<Map<String, Object>> rows = outboxRows();
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals("incident.created", row.get("TOPIC"));
        assertEquals(incidentId.toString(), row.get("MSG_KEY"), "key = incident id, so its events stay in order");
        assertEquals(IncidentCreatedEvent.class.getName(), row.get("EVENT_TYPE"));
        assertEquals("NEW", row.get("STATUS"), "waiting for the relay");
        assertTrue(row.get("PAYLOAD").toString().contains("Payments DB down"));
        assertEquals(List.of(false), userServiceCalledInsideTransaction, "UserService was called outside any transaction");
    }

    @Test
    void ifTheEventCannotBeSavedTheIncidentIsNotSavedEither() {
        UUID engineer = UUID.randomUUID();
        userServiceKnows(Map.of(engineer, user(engineer, "ACTIVE", "ROLE_ENGINEER")));
        jdbc.execute("ALTER TABLE outbox_event RENAME TO outbox_event_hidden");   // the event insert will fail
        try {
            assertThrows(RuntimeException.class, () -> incidentService.CreateIncident(engineer, newIncident()));
        } finally {
            jdbc.execute("ALTER TABLE outbox_event_hidden RENAME TO outbox_event");
        }

        assertEquals(0, incidentRepository.count(), "both or neither: no incident without its event");
        assertEquals(0, outboxRows().size());
    }

    @Test
    void assigningSavesTheChangeAndAnAssignedEventRow() {
        UUID supervisor = UUID.randomUUID();
        UUID engineer = UUID.randomUUID();
        Incident incident = existingIncident(engineer);
        userServiceKnows(Map.of(
                supervisor, user(supervisor, "ACTIVE", "ROLE_SUPERVISOR"),
                engineer, user(engineer, "ACTIVE", "ROLE_ENGINEER")));

        incidentService.AssignIncident(incident.getId(), engineer, loggedIn(supervisor, "ROLE_SUPERVISOR"));

        assertEquals(IncidentStatus.ASSIGNED, incidentRepository.findById(incident.getId()).orElseThrow().getIncidentStatus());
        List<Map<String, Object>> rows = outboxRows();
        assertEquals(1, rows.size());
        assertEquals("incident.assigned", rows.get(0).get("TOPIC"));
        assertEquals(incident.getId().toString(), rows.get(0).get("MSG_KEY"));
        assertEquals(IncidentAssignedEvent.class.getName(), rows.get(0).get("EVENT_TYPE"));
        assertFalse(userServiceCalledInsideTransaction.contains(true), "no UserService call inside a transaction");
    }

    @Test
    void changingTheStatusSavesTheChangeAndAStatusEventRow() {
        UUID engineer = UUID.randomUUID();
        Incident incident = existingIncident(engineer);
        userServiceKnows(Map.of(engineer, user(engineer, "ACTIVE", "ROLE_ENGINEER")));
        UpdateIncidentStatus_RequestDTO request = UpdateIncidentStatus_RequestDTO.builder()
                .status(IncidentStatus.RESOLVED).rootCause("connection pool too small")
                .resolutionNotes("raised the pool to 50").build();

        incidentService.updateIncidentStatusBasedOnRole(incident.getId(), request, loggedIn(engineer, "ROLE_ENGINEER"));

        assertEquals(IncidentStatus.RESOLVED, incidentRepository.findById(incident.getId()).orElseThrow().getIncidentStatus());
        List<Map<String, Object>> rows = outboxRows();
        assertEquals(1, rows.size());
        assertEquals("incident.status.updated", rows.get(0).get("TOPIC"));
        assertEquals(IncidentStatusUpdatedEvent.class.getName(), rows.get(0).get("EVENT_TYPE"));
        assertTrue(rows.get(0).get("PAYLOAD").toString().contains("connection pool too small"),
                "a RESOLVED event carries the root cause (Ai-Service learns from it)");
        assertFalse(userServiceCalledInsideTransaction.contains(true), "no UserService call inside a transaction");
    }

    @Test
    void whenTheUserIsNotActiveNothingIsSaved() {
        UUID engineer = UUID.randomUUID();
        userServiceKnows(Map.of(engineer, user(engineer, "INACTIVE", "ROLE_ENGINEER")));

        assertThrows(ForbiddenException.class, () -> incidentService.CreateIncident(engineer, newIncident()));

        assertEquals(0, incidentRepository.count());
        assertEquals(0, outboxRows().size());
    }
}
