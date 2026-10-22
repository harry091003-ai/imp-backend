package com.example.IncidentService.service;

import com.example.IncidentService.client.UserClient;
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
import com.project.outbox.OutboxWriter;
import com.project.web.exception.ApiException;
import com.project.web.exception.ConflictException;
import com.project.web.exception.ForbiddenException;
import com.project.web.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.JacksonUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Small bugs 1 and 2, on the REAL IncidentService code (H2 in MySQL mode, fake UserService, no Kafka).
 *
 * Bug 1 (issue 3) - root cause and resolution notes:
 *   1. an empty value never erases a saved one
 *   2. RESOLVED needs both texts (sent now, or saved before)
 *   3. REOPENED keeps the old texts
 *
 * Bug 2 (issue 56) - who may assign:
 *   - admin: any incident, any active engineer
 *   - supervisor: only an incident of theirs or their team, only to an engineer of their team
 *   - nobody: a RESOLVED / CLOSED incident, or the same engineer again
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:incident-rules;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:outbox/outbox_event_mysql.sql"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({IncidentService.class, IncidentEventProducer.class, IncidentRulesTest.OutboxSetup.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // no test-wide transaction: we test the real ones
class IncidentRulesTest {

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
    private Map<UUID, UserValidationDTO> users;   // what the fake UserService answers

    // The people in every test. Supervisor A leads engineer A; supervisor B leads engineer B.
    private final UUID admin = UUID.randomUUID();
    private final UUID supervisorA = UUID.randomUUID();
    private final UUID engineerA = UUID.randomUUID();
    private final UUID engineerA2 = UUID.randomUUID();
    private final UUID supervisorB = UUID.randomUUID();
    private final UUID engineerB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM outbox_event");
        incidentRepository.deleteAll();

        users = Map.of(
                admin, user(admin, "ROLE_ADMIN"),
                supervisorA, user(supervisorA, "ROLE_SUPERVISOR"),
                supervisorB, user(supervisorB, "ROLE_SUPERVISOR"),
                engineerA, user(engineerA, "ROLE_ENGINEER"),
                engineerA2, user(engineerA2, "ROLE_ENGINEER"),
                engineerB, user(engineerB, "ROLE_ENGINEER"));
        when(userClient.getUser(any())).thenAnswer(call -> users.get(call.getArgument(0, UUID.class)));
        when(userClient.getEngineersUnderSupervisor(supervisorA)).thenReturn(List.of(engineerA, engineerA2));
        when(userClient.getEngineersUnderSupervisor(supervisorB)).thenReturn(List.of(engineerB));
    }

    // ------------------------------------------------------------------ helpers

    private static UserValidationDTO user(UUID id, String role) {
        return UserValidationDTO.builder().userid(id).status("ACTIVE").roles(Set.of(role)).build();
    }

    private static Authentication loggedIn(UUID id, String role) {
        return new UsernamePasswordAuthenticationToken(id.toString(), null, List.of(new SimpleGrantedAuthority(role)));
    }

    private Incident incident(UUID createdBy, IncidentStatus status, UUID assignedTo, String rootCause, String notes) {
        return incidentRepository.save(Incident.builder()
                .title("Login slow").description("Login takes 20 seconds")
                .severity(Severity.MEDIUM).priority(Priority.MEDIUM).environment(Environment.PRODUCTION)
                .serviceName(ServiceName.AUTH_SERVICE).category(IncidentCategory.API)
                .incidentStatus(status).assignedTo(assignedTo)
                .rootCause(rootCause).resolutionNotes(notes)
                .createdBy(createdBy).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build());
    }

    private Incident reload(Incident incident) {
        return incidentRepository.findById(incident.getId()).orElseThrow();
    }

    private void changeStatus(Incident incident, UUID who, String role, IncidentStatus status, String rootCause, String notes) {
        UpdateIncidentStatus_RequestDTO request = UpdateIncidentStatus_RequestDTO.builder()
                .status(status).rootCause(rootCause).resolutionNotes(notes).build();
        incidentService.updateIncidentStatusBasedOnRole(incident.getId(), request, loggedIn(who, role));
    }

    private void assign(Incident incident, UUID who, String role, UUID engineer) {
        incidentService.AssignIncident(incident.getId(), engineer, loggedIn(who, role));
    }

    private int outboxRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event", Integer.class);
    }

    /** Runs the action, expects the given error type and code, and checks nothing was saved as an event. */
    private <T extends ApiException> void refused(Class<T> type, String code, Executable action) {
        T error = assertThrows(type, action);
        assertEquals(code, error.getErrorCode());
        assertEquals(0, outboxRows(), "a refused request sends no event");
    }

    // ================================================================== bug 1: root cause

    @Test
    void reopeningWithoutTextsKeepsTheSavedRootCauseAndNotes() {
        Incident incident = incident(engineerA, IncidentStatus.RESOLVED, engineerA, "pool too small", "raised pool to 50");

        changeStatus(incident, engineerA, "ROLE_ENGINEER", IncidentStatus.REOPENED, null, null);

        Incident after = reload(incident);
        assertEquals(IncidentStatus.REOPENED, after.getIncidentStatus());
        assertEquals("pool too small", after.getRootCause(), "rule 1 + 3: nothing sent, so nothing erased");
        assertEquals("raised pool to 50", after.getResolutionNotes());
    }

    @Test
    void blankTextsDoNotEraseEither() {
        Incident incident = incident(engineerA, IncidentStatus.RESOLVED, engineerA, "pool too small", "raised pool to 50");

        changeStatus(incident, engineerA, "ROLE_ENGINEER", IncidentStatus.REOPENED, "   ", "");

        assertEquals("pool too small", reload(incident).getRootCause());
        assertEquals("raised pool to 50", reload(incident).getResolutionNotes());
    }

    @Test
    void aNewTextReplacesTheOldOne() {
        Incident incident = incident(engineerA, IncidentStatus.REOPENED, engineerA, "pool too small", "raised pool to 50");

        changeStatus(incident, engineerA, "ROLE_ENGINEER", IncidentStatus.IN_PROGRESS, "a slow DNS lookup", null);

        assertEquals("a slow DNS lookup", reload(incident).getRootCause());
        assertEquals("raised pool to 50", reload(incident).getResolutionNotes(), "notes not sent, so kept");
    }

    @Test
    void resolvingWithNoRootCauseAnywhereIsRefused() {
        Incident incident = incident(engineerA, IncidentStatus.IN_PROGRESS, engineerA, null, null);

        refused(ValidationException.class, "ROOT_CAUSE_REQUIRED", () ->
                changeStatus(incident, engineerA, "ROLE_ENGINEER", IncidentStatus.RESOLVED, null, null));

        assertEquals(IncidentStatus.IN_PROGRESS, reload(incident).getIncidentStatus(), "status not changed");
    }

    @Test
    void resolvingWithARootCauseButNoNotesIsRefused() {
        Incident incident = incident(engineerA, IncidentStatus.IN_PROGRESS, engineerA, null, null);

        refused(ValidationException.class, "ROOT_CAUSE_REQUIRED", () ->
                changeStatus(incident, engineerA, "ROLE_ENGINEER", IncidentStatus.RESOLVED, "pool too small", null));
    }

    @Test
    void resolvingAgainMayUseTheTextsSavedEarlier() {
        Incident incident = incident(engineerA, IncidentStatus.REOPENED, engineerA, "pool too small", "raised pool to 50");

        changeStatus(incident, engineerA, "ROLE_ENGINEER", IncidentStatus.RESOLVED, null, null);

        Incident after = reload(incident);
        assertEquals(IncidentStatus.RESOLVED, after.getIncidentStatus());
        assertEquals("pool too small", after.getRootCause());
        String payload = jdbc.queryForObject("SELECT payload FROM outbox_event", String.class);
        assertTrue(payload.contains("pool too small"), "the RESOLVED event still carries the root cause");
    }

    // ================================================================== bug 2: who may assign

    @Test
    void aSupervisorMayAssignTheirTeamsIncidentToTheirOwnEngineer() {
        Incident incident = incident(engineerA, IncidentStatus.OPEN, null, null, null);

        assign(incident, supervisorA, "ROLE_SUPERVISOR", engineerA2);

        assertEquals(engineerA2, reload(incident).getAssignedTo());
        assertEquals(IncidentStatus.ASSIGNED, reload(incident).getIncidentStatus());
        assertEquals(1, outboxRows());
    }

    @Test
    void aSupervisorMayAssignAnIncidentTheyCreatedThemselves() {
        Incident incident = incident(supervisorA, IncidentStatus.OPEN, null, null, null);

        assign(incident, supervisorA, "ROLE_SUPERVISOR", engineerA);

        assertEquals(engineerA, reload(incident).getAssignedTo());
    }

    @Test
    void aSupervisorMayNotAssignToAnEngineerOfAnotherTeam() {
        Incident incident = incident(engineerA, IncidentStatus.OPEN, null, null, null);

        refused(ForbiddenException.class, "ENGINEER_NOT_IN_YOUR_TEAM", () ->
                assign(incident, supervisorA, "ROLE_SUPERVISOR", engineerB));

        assertNull(reload(incident).getAssignedTo());
    }

    @Test
    void aSupervisorMayNotAssignAnotherTeamsIncident() {
        Incident incident = incident(engineerB, IncidentStatus.OPEN, null, null, null);

        refused(ForbiddenException.class, "INCIDENT_NOT_IN_YOUR_TEAM", () ->
                assign(incident, supervisorA, "ROLE_SUPERVISOR", engineerA));

        assertNull(reload(incident).getAssignedTo());
    }

    @Test
    void anAdminMayAssignAnyIncidentToAnyEngineer() {
        Incident incident = incident(engineerB, IncidentStatus.OPEN, null, null, null);

        assign(incident, admin, "ROLE_ADMIN", engineerA);

        assertEquals(engineerA, reload(incident).getAssignedTo());
    }

    @Test
    void nobodyMayAssignAResolvedIncident() {
        Incident incident = incident(engineerA, IncidentStatus.RESOLVED, engineerA, "pool too small", "raised pool to 50");

        refused(ConflictException.class, "INCIDENT_ALREADY_CLOSED", () ->
                assign(incident, admin, "ROLE_ADMIN", engineerA2));

        assertEquals(IncidentStatus.RESOLVED, reload(incident).getIncidentStatus(), "not quietly reopened");
        assertEquals(engineerA, reload(incident).getAssignedTo());
    }

    @Test
    void nobodyMayAssignAClosedIncident() {
        Incident incident = incident(engineerA, IncidentStatus.CLOSED, engineerA, "pool too small", "raised pool to 50");

        refused(ConflictException.class, "INCIDENT_ALREADY_CLOSED", () ->
                assign(incident, supervisorA, "ROLE_SUPERVISOR", engineerA2));
    }

    @Test
    void aReopenedIncidentMayBeAssignedAgain() {
        Incident incident = incident(engineerA, IncidentStatus.REOPENED, engineerA, "pool too small", "raised pool to 50");

        assign(incident, supervisorA, "ROLE_SUPERVISOR", engineerA2);

        assertEquals(engineerA2, reload(incident).getAssignedTo());
    }

    @Test
    void theSameEngineerAgainIsRefused() {
        Incident incident = incident(engineerA, IncidentStatus.ASSIGNED, engineerA, null, null);

        refused(ConflictException.class, "ALREADY_ASSIGNED_TO_THIS_ENGINEER", () ->
                assign(incident, supervisorA, "ROLE_SUPERVISOR", engineerA));
    }

    // ================================================================== bug 3: two people at once

    @Test
    void twoAssignsAtTheSameMomentTheSecondIsRefusedNotSilentlyOverwritten() throws Exception {
        Incident incident = incident(engineerA, IncidentStatus.OPEN, null, null, null);

        // Force the overlap, every run: B reads the incident, THEN A saves, THEN B saves its old copy.
        CountDownLatch bHasReadTheIncident = new CountDownLatch(1);
        CountDownLatch aHasSaved = new CountDownLatch(1);
        doAnswer(call -> {
            UUID id = call.getArgument(0, UUID.class);
            if (id.equals(engineerA2)) {            // request A: wait until B has read the incident
                bHasReadTheIncident.await(10, TimeUnit.SECONDS);
            }
            if (id.equals(engineerB)) {             // request B: it has read the incident; wait until A has saved
                bHasReadTheIncident.countDown();
                aHasSaved.await(10, TimeUnit.SECONDS);
            }
            return users.get(id);
        }).when(userClient).getUser(any());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> a = pool.submit(() -> attempt(() -> assign(incident, admin, "ROLE_ADMIN", engineerA2)));
            Future<String> b = pool.submit(() -> attempt(() -> assign(incident, admin, "ROLE_ADMIN", engineerB)));
            assertEquals("OK", a.get(30, TimeUnit.SECONDS), "A saves first");
            aHasSaved.countDown();

            assertEquals("CONCURRENT_UPDATE", b.get(30, TimeUnit.SECONDS),
                    "B saw the incident BEFORE A's change, so its save is refused");
        } finally {
            pool.shutdownNow();
        }
        assertEquals(engineerA2, reload(incident).getAssignedTo(), "A's change is kept, not overwritten");
        assertEquals(1, outboxRows(), "only A's 'assigned' event: B's was rolled back with its save");
    }

    @Test
    void everySavedChangeRaisesTheVersionByOne() {
        UUID id = incident(engineerA, IncidentStatus.OPEN, null, null, null).getId();
        assertEquals(0L, versionOf(id), "a new incident starts at 0");

        assign(reload(id), admin, "ROLE_ADMIN", engineerA2);
        assertEquals(1L, versionOf(id));

        changeStatus(reload(id), engineerA2, "ROLE_ENGINEER", IncidentStatus.IN_PROGRESS, null, null);
        assertEquals(2L, versionOf(id));
    }

    private Incident reload(UUID id) {
        return incidentRepository.findById(id).orElseThrow();
    }

    private long versionOf(UUID incidentId) {
        return jdbc.queryForObject("SELECT version FROM incidents WHERE id = ?", Long.class, incidentId);
    }

    private static String attempt(Runnable action) {
        try {
            action.run();
            return "OK";
        } catch (ApiException e) {
            return e.getErrorCode();
        } catch (OptimisticLockingFailureException e) {
            return "CONCURRENT_UPDATE";   // what common-web turns into a 409
        }
    }
}
