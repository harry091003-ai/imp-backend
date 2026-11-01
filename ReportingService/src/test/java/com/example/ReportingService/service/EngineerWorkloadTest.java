package com.example.ReportingService.service;

import com.example.ReportingService.kafka.consumer.IncidentEventConsumer;
import com.example.ReportingService.kafka.producer.IncidentResolvedReportEventProducer;
import com.example.ReportingService.repository.EngineerPerformanceStatsRepository;
import com.example.ReportingService.repository.EngineerWorkloadRepository;
import com.example.ReportingService.repository.IncidentAuditLogRepository;
import com.example.ReportingService.repository.IncidentReportRepository;
import com.example.ReportingService.repository.ProcessedEventRepository;
import com.project.events.IncidentAssignedEvent;
import com.project.events.IncidentCreatedEvent;
import com.project.events.IncidentStatusUpdatedEvent;
import com.project.outbox.OutboxWriter;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue 62 (2026-11-01): the engineer's "active incidents" number drifted.
 *   - a reassign added 1 to the new engineer and never took 1 from the old one
 *   - a resolve took 1 from whoever pressed the button (maybe a supervisor), not from the engineer
 *   - a reopen never added 1 back
 * The expertise stats and the event sent to Ai-Service also credited whoever pressed the button.
 *
 * The REAL Kafka listener and services with a real database (H2 in MySQL mode). The listener methods are
 * called directly, the way Kafka would call them.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reporting-workload;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:outbox/outbox_event_mysql.sql"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({IncidentEventConsumer.class, IdempotencyService.class, EngineerPerformanceService.class,
        EngineerWorkloadService.class, IncidentResolvedReportEventProducer.class, EngineerWorkloadTest.OutboxSetup.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // no test-wide transaction: we test the real ones
class EngineerWorkloadTest {

    @TestConfiguration
    static class OutboxSetup {
        @Bean
        OutboxWriter outboxWriter(DataSource dataSource) {
            return new OutboxWriter(new JdbcTemplate(dataSource), JacksonUtils.enhancedObjectMapper());
        }
    }

    @Autowired IncidentEventConsumer consumer;
    @Autowired IncidentReportRepository reportRepository;
    @Autowired IncidentAuditLogRepository auditLogRepository;
    @Autowired EngineerWorkloadRepository workloadRepository;
    @Autowired EngineerPerformanceStatsRepository statsRepository;
    @Autowired ProcessedEventRepository processedEventRepository;
    @Autowired DataSource dataSource;

    private JdbcTemplate jdbc;

    private final UUID engineerA = UUID.randomUUID();
    private final UUID engineerB = UUID.randomUUID();
    private final UUID supervisor = UUID.randomUUID();
    private static final LocalDateTime CREATED = LocalDateTime.now().minusMinutes(30);

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM outbox_event");
        auditLogRepository.deleteAll();
        workloadRepository.deleteAll();
        statsRepository.deleteAll();
        processedEventRepository.deleteAll();
        reportRepository.deleteAll();
    }

    // ------------------------------------------------------------------ helpers

    private UUID newIncident() {
        UUID id = UUID.randomUUID();
        consumer.consumeIncidentCreatedEvent(IncidentCreatedEvent.builder().incidentId(id).eventId(UUID.randomUUID())
                .performedBy(engineerA).title("Payments down").description("Every payment fails")
                .createdBy(engineerA).severity("HIGH").priority("LOW").createdAt(CREATED)
                .serviceName("PAYMENT_SERVICE").category("DATABASE").build());
        return id;
    }

    private void assign(UUID incident, UUID engineer) {
        consumer.consumeIncidentAssignedEvent(IncidentAssignedEvent.builder().incidentId(incident)
                .eventId(UUID.randomUUID()).performedBy(supervisor).assignedTo(engineer).assignedBy(supervisor)
                .assignedAt(CREATED.plusMinutes(2)).build());
    }

    private void changeStatus(UUID incident, String oldStatus, String newStatus, UUID who) {
        LocalDateTime at = CREATED.plusMinutes(5);
        IncidentStatusUpdatedEvent.IncidentStatusUpdatedEventBuilder event = IncidentStatusUpdatedEvent.builder()
                .incidentId(incident).eventId(UUID.randomUUID()).performedBy(who).UpdatedBy(who)
                .title("Payments down").description("Every payment fails")
                .OldStatus(oldStatus).UpdatedStatus(newStatus).UpdatedAt(at)
                .serviceName("PAYMENT_SERVICE").category("DATABASE");
        if (newStatus.equals("RESOLVED")) {
            event.rootCause("pool too small").resolutionNotes("raised the pool").resolvedAt(at);
        }
        consumer.consumerIncidentStatusUpdateEvent(event.build());
    }

    private int activeCount(UUID engineer) {
        return workloadRepository.findByEngineerId(engineer).map(w -> w.getActiveIncidentCount()).orElse(0);
    }

    private long resolvedCredit(UUID engineer) {
        return statsRepository.findByEngineerIdAndCategoryAndServiceName(engineer, "DATABASE", "PAYMENT_SERVICE")
                .map(s -> s.getTotalIncidentsResolved()).orElse(0L);
    }

    // ------------------------------------------------------------------ the count

    @Test
    void assigningAddsOne() {
        assign(newIncident(), engineerA);
        assign(newIncident(), engineerA);

        assertEquals(2, activeCount(engineerA));
    }

    @Test
    void reassigningMovesTheIncidentFromTheOldEngineerToTheNewOne() {
        UUID incident = newIncident();
        assign(incident, engineerA);

        assign(incident, engineerB);

        assertEquals(0, activeCount(engineerA), "the old engineer no longer has it");
        assertEquals(1, activeCount(engineerB));
    }

    @Test
    void aSupervisorPressingResolveTakesItFromTheEngineer() {
        UUID incident = newIncident();
        assign(incident, engineerA);

        changeStatus(incident, "ASSIGNED", "RESOLVED", supervisor);

        assertEquals(0, activeCount(engineerA), "the engineer is free again");
        assertEquals(0, activeCount(supervisor), "the supervisor never had it");
    }

    @Test
    void reopeningGivesItBackToTheEngineer() {
        UUID incident = newIncident();
        assign(incident, engineerA);
        changeStatus(incident, "ASSIGNED", "RESOLVED", engineerA);

        changeStatus(incident, "RESOLVED", "REOPENED", engineerA);

        assertEquals(1, activeCount(engineerA));
    }

    @Test
    void closingAResolvedIncidentDoesNotTakeOneOffTwice() {
        UUID first = newIncident();
        UUID second = newIncident();
        assign(first, engineerA);
        assign(second, engineerA);
        changeStatus(first, "ASSIGNED", "RESOLVED", engineerA);

        changeStatus(first, "RESOLVED", "CLOSED", engineerA);

        assertEquals(1, activeCount(engineerA), "the second incident is still active");
    }

    @Test
    void aCountThatHasAlreadyDriftedHealsItselfAtTheNextEvent() {
        UUID incident = newIncident();
        assign(incident, engineerA);
        jdbc.update("UPDATE engineer_workload SET active_incident_count = 99 WHERE engineer_id = ?", engineerA);

        changeStatus(incident, "ASSIGNED", "IN_PROGRESS", engineerA);

        assertEquals(1, activeCount(engineerA), "the wrong number left by the old code is corrected");
    }

    // ------------------------------------------------------------------ the credit

    @Test
    void theAssignedEngineerGetsTheCreditEvenIfASupervisorPressedResolve() {
        UUID incident = newIncident();
        assign(incident, engineerA);

        changeStatus(incident, "ASSIGNED", "RESOLVED", supervisor);

        assertEquals(1, resolvedCredit(engineerA));
        assertEquals(0, resolvedCredit(supervisor));
    }

    @Test
    void anIncidentNobodyWasAssignedIsCreditedToWhoeverResolvedIt() {
        UUID incident = newIncident();

        changeStatus(incident, "OPEN", "RESOLVED", engineerB);

        assertEquals(1, resolvedCredit(engineerB));
    }

    @Test
    void theReportStillSaysWhoReallyPressedResolve() {
        UUID incident = newIncident();
        assign(incident, engineerA);

        changeStatus(incident, "ASSIGNED", "RESOLVED", supervisor);

        assertEquals(supervisor, reportRepository.findById(incident).orElseThrow().getResolvedBy());
    }

    @Test
    void aiServiceIsToldTheEngineerNotTheSupervisor() {
        // Ai-Service recommends engineers from this field: a supervisor's id would be recommended as an engineer.
        UUID incident = newIncident();
        assign(incident, engineerA);

        changeStatus(incident, "ASSIGNED", "RESOLVED", supervisor);

        String payload = jdbc.queryForObject("SELECT payload FROM outbox_event", String.class);
        assertTrue(payload.contains(engineerA.toString()), payload);
        assertFalse(payload.contains(supervisor.toString()), payload);
    }

    // ------------------------------------------------------------------ AI plan phase 2

    @Test
    void aiServiceIsToldSeverityPriorityAndEnvironment() {
        UUID incident = newIncident();
        LocalDateTime at = CREATED.plusMinutes(5);

        consumer.consumerIncidentStatusUpdateEvent(IncidentStatusUpdatedEvent.builder().incidentId(incident)
                .eventId(UUID.randomUUID()).performedBy(engineerA).UpdatedBy(engineerA)
                .title("Payments down").description("Every payment fails")
                .OldStatus("OPEN").UpdatedStatus("RESOLVED").UpdatedAt(at)
                .serviceName("PAYMENT_SERVICE").category("DATABASE").environment("PRODUCTION")
                .rootCause("pool too small").resolutionNotes("raised the pool").resolvedAt(at).build());

        String payload = jdbc.queryForObject("SELECT payload FROM outbox_event", String.class);
        assertTrue(payload.contains("\"severity\":\"HIGH\""), payload);
        assertTrue(payload.contains("\"priority\":\"LOW\""), payload);
        assertTrue(payload.contains("\"environment\":\"PRODUCTION\""), payload);
    }
}
