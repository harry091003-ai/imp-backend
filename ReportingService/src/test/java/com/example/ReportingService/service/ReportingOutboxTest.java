package com.example.ReportingService.service;

import com.example.ReportingService.entity.IncidentReport;
import com.example.ReportingService.entity.SLA_RiskLevel;
import com.example.ReportingService.kafka.consumer.IncidentEventConsumer;
import com.example.ReportingService.kafka.producer.IncidentResolvedReportEventProducer;
import com.example.ReportingService.kafka.producer.SLA_BreachedEventProducer;
import com.example.ReportingService.kafka.producer.SLA_BreachingRiskEventProducer;
import com.example.ReportingService.repository.EngineerWorkloadRepository;
import com.example.ReportingService.repository.IncidentAuditLogRepository;
import com.example.ReportingService.repository.IncidentReportRepository;
import com.example.ReportingService.repository.ProcessedEventRepository;
import com.example.ReportingService.schedular.slaBreached.SLA_BreachedSchedular;
import com.example.ReportingService.schedular.slaBreachingRisk.SLA_BreachingRiskSchedular;
import com.project.events.IncidentAssignedEvent;
import com.project.events.IncidentCreatedEvent;
import com.project.events.IncidentResolutionReportEvent;
import com.project.events.IncidentStatusUpdatedEvent;
import com.project.events.SLA_BreachedEvent;
import com.project.events.enumsUsedByEvents.SlaBreachingRiskEvent;
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
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outbox step 4: the REAL ReportingService code (the Kafka listener and both SLA schedulers) with a real
 * database (H2 in MySQL mode). There is no Kafka here: the listener methods are called directly, the way
 * Kafka would call them, and the schedulers are called directly instead of waiting for their timer.
 *
 * What it proves:
 *   - a RESOLVED event saves the report change AND one outbox row, in one transaction (both or neither)
 *   - the SLA schedulers set "event published" AND save the event row together (known issue 22 is closed)
 *   - running a scheduler twice does not save the same event twice
 *   - the same Kafka event delivered twice is only handled ONCE (known issue 60 is fixed)
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reporting-outbox;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:outbox/outbox_event_mysql.sql"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({IncidentEventConsumer.class, IdempotencyService.class, EngineerPerformanceService.class,
        EngineerWorkloadService.class, IncidentResolvedReportEventProducer.class,
        SLA_BreachedEventProducer.class, SLA_BreachingRiskEventProducer.class, ReportingOutboxTest.OutboxSetup.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // no test-wide transaction: we test the real ones
class ReportingOutboxTest {

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
    @Autowired ProcessedEventRepository processedEventRepository;
    @Autowired SLA_BreachedEventProducer breachedProducer;
    @Autowired SLA_BreachingRiskEventProducer riskProducer;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired DataSource dataSource;

    // Built by hand, not as Spring beans: as beans, their @Scheduled timers would start running by
    // themselves in the background during the test. We want to call them ourselves, at the right moment.
    private SLA_BreachedSchedular breachedSchedular;
    private SLA_BreachingRiskSchedular riskSchedular;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM outbox_event");
        auditLogRepository.deleteAll();
        workloadRepository.deleteAll();
        processedEventRepository.deleteAll();
        jdbc.update("DELETE FROM engineer_performance_stats");
        reportRepository.deleteAll();
        breachedSchedular = new SLA_BreachedSchedular(reportRepository, breachedProducer, transactionTemplate);
        riskSchedular = new SLA_BreachingRiskSchedular(reportRepository, riskProducer, transactionTemplate);
    }

    // ------------------------------------------------------------------ helpers

    private static final LocalDateTime CREATED = LocalDateTime.now().minusMinutes(10);

    private static IncidentCreatedEvent created(UUID incidentId) {
        return IncidentCreatedEvent.builder().incidentId(incidentId).eventId(UUID.randomUUID())
                .performedBy(UUID.randomUUID()).title("Payments DB down").description("Every payment fails")
                .createdBy(UUID.randomUUID()).severity("HIGH").priority("LOW").createdAt(CREATED)
                .serviceName("PAYMENT_SERVICE").category("DATABASE").build();
    }

    private static IncidentAssignedEvent assigned(UUID incidentId, UUID engineer) {
        return IncidentAssignedEvent.builder().incidentId(incidentId).eventId(UUID.randomUUID())
                .performedBy(UUID.randomUUID()).assignedTo(engineer).assignedBy(UUID.randomUUID())
                .assignedAt(CREATED.plusMinutes(2)).build();
    }

    private static IncidentStatusUpdatedEvent resolved(UUID incidentId, UUID engineer) {
        LocalDateTime at = CREATED.plusMinutes(5);
        return IncidentStatusUpdatedEvent.builder().incidentId(incidentId).eventId(UUID.randomUUID())
                .performedBy(engineer).title("Payments DB down").description("Every payment fails")
                .OldStatus("ASSIGNED").UpdatedStatus("RESOLVED").UpdatedBy(engineer).UpdatedAt(at)
                .serviceName("PAYMENT_SERVICE").category("DATABASE")
                .rootCause("connection pool too small").resolutionNotes("raised the pool to 50").resolvedAt(at).build();
    }

    private List<Map<String, Object>> outboxRows() {
        return jdbc.queryForList("SELECT * FROM outbox_event ORDER BY seq");
    }

    private IncidentReport report(UUID incidentId) {
        return reportRepository.findById(incidentId).orElseThrow();
    }

    /** A report that is waiting to be assigned, with its deadline already set. */
    private IncidentReport waitingReport(LocalDateTime createdAt, LocalDateTime assignmentDeadline) {
        return reportRepository.save(IncidentReport.builder().incidentId(UUID.randomUUID()).title("t")
                .description("d").severity("HIGH").priority("HIGH").status("OPEN").serviceName("PAYMENT_SERVICE")
                .category("DATABASE").createdAt(createdAt).updatedAt(createdAt)
                .assignmentDeadline(assignmentDeadline).resolutionDeadline(createdAt.plusDays(10)).build());
    }

    // ------------------------------------------------------------------ the Kafka listener

    @Test
    void aResolvedEventSavesTheReportAndOneOutboxRow() {
        UUID incidentId = UUID.randomUUID();
        UUID engineer = UUID.randomUUID();
        consumer.consumeIncidentCreatedEvent(created(incidentId));
        consumer.consumeIncidentAssignedEvent(assigned(incidentId, engineer));
        assertEquals(0, outboxRows().size(), "created / assigned send nothing");

        consumer.consumerIncidentStatusUpdateEvent(resolved(incidentId, engineer));

        assertEquals("RESOLVED", report(incidentId).getStatus());
        List<Map<String, Object>> rows = outboxRows();
        assertEquals(1, rows.size());
        assertEquals("Incident-Resolved-Report-Topic", rows.get(0).get("TOPIC"));
        assertEquals(incidentId.toString(), rows.get(0).get("MSG_KEY"), "key = incident id, same as before");
        assertEquals(IncidentResolutionReportEvent.class.getName(), rows.get(0).get("EVENT_TYPE"));
        assertEquals("NEW", rows.get(0).get("STATUS"), "waiting for the relay");
        assertTrue(rows.get(0).get("PAYLOAD").toString().contains("connection pool too small"),
                "Ai-Service gets the root cause");
    }

    @Test
    void ifTheResolvedEventCannotBeSavedNothingIsSavedAndKafkaWillRetry() {
        UUID incidentId = UUID.randomUUID();
        UUID engineer = UUID.randomUUID();
        consumer.consumeIncidentCreatedEvent(created(incidentId));
        consumer.consumeIncidentAssignedEvent(assigned(incidentId, engineer));
        long ledgerBefore = processedEventRepository.count();
        IncidentStatusUpdatedEvent event = resolved(incidentId, engineer);

        jdbc.execute("ALTER TABLE outbox_event RENAME TO outbox_event_hidden");   // the event insert will fail
        try {
            assertThrows(RuntimeException.class, () -> consumer.consumerIncidentStatusUpdateEvent(event));
        } finally {
            jdbc.execute("ALTER TABLE outbox_event_hidden RENAME TO outbox_event");
        }

        assertEquals("ASSIGNED", report(incidentId).getStatus(), "the report change rolled back too");
        assertEquals(ledgerBefore, processedEventRepository.count(),
                "the event is NOT marked as handled, so Kafka's retry will handle it again");

        consumer.consumerIncidentStatusUpdateEvent(event);   // Kafka's retry, now the table is back
        assertEquals("RESOLVED", report(incidentId).getStatus());
        assertEquals(1, outboxRows().size());
    }

    // ------------------------------------------------------------------ known issue 60: the inbox

    @Test
    void theSameAssignedEventTwiceIsHandledOnlyOnce() {
        UUID incidentId = UUID.randomUUID();
        UUID engineer = UUID.randomUUID();
        consumer.consumeIncidentCreatedEvent(created(incidentId));
        IncidentAssignedEvent event = assigned(incidentId, engineer);

        consumer.consumeIncidentAssignedEvent(event);
        consumer.consumeIncidentAssignedEvent(event);   // Kafka delivers the same message again

        assertEquals(1, workloadRepository.findByEngineerId(engineer).orElseThrow().getActiveIncidentCount(),
                "the engineer's workload went up by 1, not 2");
        assertEquals(2, auditLogRepository.findByIncidentIdOrderByTimestampAsc(incidentId).size(),
                "one audit row for created + one for assigned - no extra row for the repeat");
    }

    @Test
    void theSameResolvedEventTwiceSavesOnlyOneOutboxRow() {
        UUID incidentId = UUID.randomUUID();
        UUID engineer = UUID.randomUUID();
        consumer.consumeIncidentCreatedEvent(created(incidentId));
        consumer.consumeIncidentAssignedEvent(assigned(incidentId, engineer));
        IncidentStatusUpdatedEvent event = resolved(incidentId, engineer);

        consumer.consumerIncidentStatusUpdateEvent(event);
        consumer.consumerIncidentStatusUpdateEvent(event);   // the repeat

        assertEquals(1, outboxRows().size(), "Ai-Service is told once, not twice");
    }

    @Test
    void theSameCreatedEventTwiceDoesNotResetTheReport() {
        UUID incidentId = UUID.randomUUID();
        UUID engineer = UUID.randomUUID();
        IncidentCreatedEvent event = created(incidentId);
        consumer.consumeIncidentCreatedEvent(event);
        consumer.consumeIncidentAssignedEvent(assigned(incidentId, engineer));

        consumer.consumeIncidentCreatedEvent(event);   // a late repeat of the FIRST event

        assertEquals("ASSIGNED", report(incidentId).getStatus(), "the repeat did not reset the report to OPEN");
    }

    // ------------------------------------------------------------------ SLA breached scheduler

    @Test
    void aBreachedAssignmentSavesTheFlagAndTheEventTogether() {
        IncidentReport late = waitingReport(LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(1));

        breachedSchedular.checkSlaBreaches();

        IncidentReport after = report(late.getIncidentId());
        assertTrue(after.isAssignmentSlaBreached());
        assertTrue(after.isAssignmentSlaEventPublished());
        List<Map<String, Object>> rows = outboxRows();
        assertEquals(1, rows.size());
        assertEquals("Sla-Assignment-Breach-Topic", rows.get(0).get("TOPIC"));
        assertEquals(late.getIncidentId().toString(), rows.get(0).get("MSG_KEY"));
        assertEquals(SLA_BreachedEvent.class.getName(), rows.get(0).get("EVENT_TYPE"));

        breachedSchedular.checkSlaBreaches();   // the next run
        assertEquals(1, outboxRows().size(), "the next run does not save the same event again");
    }

    @Test
    void ifTheBreachEventCannotBeSavedTheFlagStaysOffAndTheNextRunSendsIt() {
        IncidentReport late = waitingReport(LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(1));

        jdbc.execute("ALTER TABLE outbox_event RENAME TO outbox_event_hidden");
        try {
            assertThrows(RuntimeException.class, () -> breachedSchedular.checkSlaBreaches());
        } finally {
            jdbc.execute("ALTER TABLE outbox_event_hidden RENAME TO outbox_event");
        }
        assertFalse(report(late.getIncidentId()).isAssignmentSlaEventPublished(),
                "not marked 'published' when the event was not saved (this was known issue 22)");

        breachedSchedular.checkSlaBreaches();   // the next run, with the table back

        assertTrue(report(late.getIncidentId()).isAssignmentSlaEventPublished());
        assertEquals(1, outboxRows().size());
    }

    // ------------------------------------------------------------------ SLA breaching-risk scheduler

    @Test
    void aRiskLevelChangeSavesTheLevelAndTheEventTogether() {
        // created 100 minutes ago, deadline in 5 minutes: 100 of 105 minutes used = 0.95 = CRITICAL
        IncidentReport nearlyLate = waitingReport(LocalDateTime.now().minusMinutes(100), LocalDateTime.now().plusMinutes(5));

        riskSchedular.monitorAssignmentRisk();

        assertEquals(SLA_RiskLevel.CRITICAL, report(nearlyLate.getIncidentId()).getAssignmentRiskLevel());
        List<Map<String, Object>> rows = outboxRows();
        assertEquals(1, rows.size());
        assertEquals("Sla-Assignment-BreachingRisk-Topic", rows.get(0).get("TOPIC"));
        assertEquals(SlaBreachingRiskEvent.class.getName(), rows.get(0).get("EVENT_TYPE"));
        assertTrue(rows.get(0).get("PAYLOAD").toString().contains("CRITICAL"));

        riskSchedular.monitorAssignmentRisk();   // the next run: the level did not change
        assertEquals(1, outboxRows().size(), "no new event when the level is the same");
    }

    @Test
    void ifTheRiskEventCannotBeSavedTheOldLevelIsKept() {
        IncidentReport nearlyLate = waitingReport(LocalDateTime.now().minusMinutes(100), LocalDateTime.now().plusMinutes(5));

        jdbc.execute("ALTER TABLE outbox_event RENAME TO outbox_event_hidden");
        try {
            assertThrows(RuntimeException.class, () -> riskSchedular.monitorAssignmentRisk());
        } finally {
            jdbc.execute("ALTER TABLE outbox_event_hidden RENAME TO outbox_event");
        }

        assertEquals(SLA_RiskLevel.NONE, report(nearlyLate.getIncidentId()).getAssignmentRiskLevel(),
                "level not saved without its event - so the next run will try again");
    }
}
