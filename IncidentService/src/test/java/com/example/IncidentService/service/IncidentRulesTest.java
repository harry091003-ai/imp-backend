package com.example.IncidentService.service;

import com.example.IncidentService.client.UserClient;
import com.example.IncidentService.dto.IncidentResponseDTO;
import com.example.IncidentService.dto.UpdateIncidentMetadate_RequestDTO;
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
import org.springframework.data.domain.PageRequest;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
 *
 * Issue 57 (2026-11-01) - one rule for what a supervisor may touch: list, read, update, status and assign agree.
 *   A supervisor with no team still sees the incidents they created.
 * Issue 58 (2026-11-01) - reading an incident returns all its details.
 * Issue 38 (2026-11-01) - an assign asks UserService about the engineer only once.
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

    // ================================================================== issue 57: one rule for a supervisor

    private List<UUID> listFor(UUID who, String role) {
        return incidentService.getIncidentsBasedOnRoles(who, loggedIn(who, role), PageRequest.of(0, 20), null)
                .map(IncidentResponseDTO::getId).getContent();
    }

    private void mayRead(Incident incident, UUID who) {
        assertEquals(incident.getId(),
                incidentService.getIncidentByIdBasedOnRole(incident.getId(), loggedIn(who, "ROLE_SUPERVISOR")).getId());
    }

    private void updateTitle(Incident incident, UUID who) {
        incidentService.updateIncidentBasedOnRole(incident.getId(),
                UpdateIncidentMetadate_RequestDTO.builder().title("New title").build(), loggedIn(who, "ROLE_SUPERVISOR"));
    }

    @Test
    void aSupervisorWithNoTeamStillSeesTheIncidentsTheyCreated() {
        UUID lonely = UUID.randomUUID();
        when(userClient.getEngineersUnderSupervisor(lonely)).thenReturn(List.of());
        Incident own = incident(lonely, IncidentStatus.OPEN, null, null, null);
        incident(engineerB, IncidentStatus.OPEN, null, null, null);

        assertEquals(List.of(own.getId()), listFor(lonely, "ROLE_SUPERVISOR"));
    }

    @Test
    void aSupervisorListsEveryIncidentTheyMayTouch() {
        Incident createdByMe = incident(supervisorA, IncidentStatus.OPEN, null, null, null);
        Incident createdByMyEngineer = incident(engineerA, IncidentStatus.OPEN, null, null, null);
        Incident assignedToMyEngineer = incident(engineerB, IncidentStatus.ASSIGNED, engineerA, null, null);
        Incident assignedByMe = incident(engineerB, IncidentStatus.ASSIGNED, engineerB, null, null);
        assignedByMe.setAssignedBy(supervisorA);
        incidentRepository.save(assignedByMe);
        Incident otherTeam = incident(engineerB, IncidentStatus.ASSIGNED, engineerB, null, null);

        List<UUID> seen = listFor(supervisorA, "ROLE_SUPERVISOR");

        assertTrue(seen.containsAll(List.of(createdByMe.getId(), createdByMyEngineer.getId(),
                assignedToMyEngineer.getId(), assignedByMe.getId())), seen.toString());
        assertFalse(seen.contains(otherTeam.getId()), "another team's incident stays hidden");
    }

    @Test
    void whatASupervisorMayChangeTheyMayAlsoRead() {
        // Created by an engineer of the team, nobody assigned: read-one used to refuse this (403), update allowed it.
        Incident incident = incident(engineerA, IncidentStatus.OPEN, null, null, null);

        mayRead(incident, supervisorA);
        updateTitle(incident, supervisorA);

        assertEquals("New title", reload(incident).getTitle());
    }

    @Test
    void aSupervisorCannotReadOrChangeAnotherTeamsIncident() {
        Incident incident = incident(engineerB, IncidentStatus.ASSIGNED, engineerB, null, null);

        assertEquals("INCIDENT_ACCESS_DENIED", assertThrows(ForbiddenException.class, () -> mayRead(incident, supervisorA)).getErrorCode());
        assertEquals("INCIDENT_UPDATE_DENIED", assertThrows(ForbiddenException.class, () -> updateTitle(incident, supervisorA)).getErrorCode());
    }

    // ================================================================== issue 58: all the details

    private void assertAllDetails(IncidentResponseDTO answer, Incident saved) {
        assertEquals("Login takes 20 seconds", answer.getDescription());
        assertEquals(ServiceName.AUTH_SERVICE, answer.getServiceName());
        assertEquals(IncidentCategory.API, answer.getCategory());
        assertEquals(Environment.PRODUCTION, answer.getEnvironment());
        assertEquals("pool too small", answer.getRootCause());
        assertEquals("raised pool to 50", answer.getResolutionNotes());
        assertNotNull(answer.getCreatedAt());
        assertNotNull(answer.getUpdatedAt());
        assertEquals(saved.getLocation(), answer.getLocation());
        assertEquals(saved.getAffectedSource(), answer.getAffectedSource());
    }

    @Test
    void readingOneIncidentReturnsAllItsDetails() {
        Incident saved = incident(engineerA, IncidentStatus.RESOLVED, engineerA, "pool too small", "raised pool to 50");

        assertAllDetails(incidentService.getIncidentByIdBasedOnRole(saved.getId(), loggedIn(admin, "ROLE_ADMIN")), saved);
    }

    @Test
    void theListReturnsAllTheDetailsToo() {
        Incident saved = incident(engineerA, IncidentStatus.RESOLVED, engineerA, "pool too small", "raised pool to 50");

        assertAllDetails(incidentService.getIncidentsBasedOnRoles(admin, loggedIn(admin, "ROLE_ADMIN"), PageRequest.of(0, 20), null)
                .getContent().get(0), saved);
    }

    // ================================================================== issue 38: one call, not two

    @Test
    void anAssignAsksAboutTheEngineerOnlyOnce() {
        Incident incident = incident(supervisorA, IncidentStatus.OPEN, null, null, null);

        assign(incident, supervisorA, "ROLE_SUPERVISOR", engineerA);

        verify(userClient, times(1)).getUser(engineerA);
        assertEquals(engineerA, reload(incident).getAssignedTo());
    }

    @Test
    void anInactiveEngineerIsStillRefused() {
        Incident incident = incident(supervisorA, IncidentStatus.OPEN, null, null, null);
        UUID inactive = UUID.randomUUID();
        when(userClient.getUser(inactive)).thenReturn(
                UserValidationDTO.builder().userid(inactive).status("SUSPENDED").roles(Set.of("ROLE_ENGINEER")).build());

        refused(ValidationException.class, "ENGINEER_NOT_ACTIVE", () -> assign(incident, admin, "ROLE_ADMIN", inactive));
    }

    // ================================================================== AI plan phase 2

    @Test
    void theStatusEventSaysWhichEnvironment() {
        Incident incident = incident(engineerA, IncidentStatus.ASSIGNED, engineerA, null, null);

        changeStatus(incident, engineerA, "ROLE_ENGINEER", IncidentStatus.RESOLVED, "pool too small", "raised pool to 50");

        String payload = jdbc.queryForObject("SELECT payload FROM outbox_event", String.class);
        assertTrue(payload.contains("\"environment\":\"PRODUCTION\""), payload);
    }

    // ================================================================== BCR-03: which status change is allowed

    // Written here on purpose, not read from the code, so a wrong rule in the code cannot also make this test right.
    // ASSIGNED is reached only through the assign endpoint, so no status change leads to it.
    // REOPENED may go straight to RESOLVED, like OPEN (see resolvingAgainMayUseTheTextsSavedEarlier).
    private static final Map<IncidentStatus, Set<IncidentStatus>> ALLOWED_NEXT = Map.of(
            IncidentStatus.OPEN, Set.of(IncidentStatus.IN_PROGRESS, IncidentStatus.RESOLVED),
            IncidentStatus.ASSIGNED, Set.of(IncidentStatus.IN_PROGRESS, IncidentStatus.RESOLVED),
            IncidentStatus.IN_PROGRESS, Set.of(IncidentStatus.RESOLVED),
            IncidentStatus.RESOLVED, Set.of(IncidentStatus.CLOSED, IncidentStatus.REOPENED),
            IncidentStatus.CLOSED, Set.of(IncidentStatus.REOPENED),
            IncidentStatus.REOPENED, Set.of(IncidentStatus.IN_PROGRESS, IncidentStatus.RESOLVED));

    // All 36 pairs: every allowed change works, every other one is refused with 409 and changes nothing.
    // An admin does it, so only the status rule can say no.
    @Test
    void everyStatusChangeFollowsTheTable() {
        for (IncidentStatus from : IncidentStatus.values()) {
            for (IncidentStatus to : IncidentStatus.values()) {
                jdbc.update("DELETE FROM outbox_event");
                Incident incident = incident(engineerA, from, engineerA, "pool too small", "raised pool to 50");
                String pair = from + " -> " + to;

                if (ALLOWED_NEXT.get(from).contains(to)) {
                    changeStatus(incident, admin, "ROLE_ADMIN", to, null, null);
                    assertEquals(to, reload(incident).getIncidentStatus(), pair + " should be allowed");
                } else {
                    ConflictException error = assertThrows(ConflictException.class,
                            () -> changeStatus(incident, admin, "ROLE_ADMIN", to, null, null), pair + " should be refused");
                    assertEquals("INVALID_STATUS_TRANSITION", error.getErrorCode(), pair);
                    assertEquals(from, reload(incident).getIncidentStatus(), pair + ": status not changed");
                    assertEquals(0, outboxRows(), pair + ": a refused change sends no event");
                }
            }
        }
    }

    @Test
    void aClosedIncidentCannotGoBackToOpen() {
        Incident incident = incident(engineerA, IncidentStatus.CLOSED, engineerA, "pool too small", "raised pool to 50");

        refused(ConflictException.class, "INVALID_STATUS_TRANSITION", () ->
                changeStatus(incident, admin, "ROLE_ADMIN", IncidentStatus.OPEN, null, null));
    }

    // ASSIGNED with nobody assigned would be a lie on the screen. The assign endpoint sets it, with an engineer.
    @Test
    void theStatusChangeCannotSetAssigned() {
        Incident incident = incident(engineerA, IncidentStatus.OPEN, null, null, null);

        ConflictException error = assertThrows(ConflictException.class, () ->
                changeStatus(incident, admin, "ROLE_ADMIN", IncidentStatus.ASSIGNED, null, null));
        assertEquals("INVALID_STATUS_TRANSITION", error.getErrorCode());
        assertTrue(error.getMessage().contains("assign"), "the message says to use assign: " + error.getMessage());
    }

    @Test
    void theSameStatusAgainIsRefused() {
        Incident incident = incident(engineerA, IncidentStatus.IN_PROGRESS, engineerA, null, null);

        refused(ConflictException.class, "INVALID_STATUS_TRANSITION", () ->
                changeStatus(incident, engineerA, "ROLE_ENGINEER", IncidentStatus.IN_PROGRESS, null, null));
    }

    // The owner's decision (2026-11-02): an engineer may close and reopen an incident of theirs.
    @Test
    void anEngineerMayCloseAndReopenTheirOwnIncident() {
        Incident incident = incident(engineerA, IncidentStatus.RESOLVED, engineerA, "pool too small", "raised pool to 50");

        changeStatus(incident, engineerA, "ROLE_ENGINEER", IncidentStatus.CLOSED, null, null);
        assertEquals(IncidentStatus.CLOSED, reload(incident).getIncidentStatus());

        changeStatus(incident, engineerA, "ROLE_ENGINEER", IncidentStatus.REOPENED, null, null);
        assertEquals(IncidentStatus.REOPENED, reload(incident).getIncidentStatus());
    }

    // "Who" is still checked before "which": someone with no right to the incident learns nothing about its status.
    @Test
    void anOutsiderGets403EvenForAWrongChange() {
        Incident incident = incident(engineerA, IncidentStatus.CLOSED, engineerA, null, null);

        refused(ForbiddenException.class, "INCIDENT_STATUS_UPDATE_DENIED", () ->
                changeStatus(incident, engineerB, "ROLE_ENGINEER", IncidentStatus.OPEN, null, null));
    }
}
