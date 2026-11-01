package com.example.ReportingService.service;

import com.example.ReportingService.controller.ReportingController;
import com.example.ReportingService.dto.DailyMetricsResponseDTO;
import com.example.ReportingService.entity.DailyMetrics;
import com.example.ReportingService.entity.IncidentReport;
import com.example.ReportingService.repository.DailtyMetricsReportRepository;
import com.example.ReportingService.repository.IncidentReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue 61 (2026-11-01): the daily_metrics table existed but nothing ever wrote to it.
 * The REAL DailyMetricsService with a real database (H2 in MySQL mode). A fixed "today" keeps the test exact.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reporting-daily-metrics;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=DATE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DailyMetricsService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // no test-wide transaction: we test the real ones
class DailyMetricsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    @Autowired DailyMetricsService service;
    @Autowired DailtyMetricsReportRepository metricsRepository;
    @Autowired IncidentReportRepository reportRepository;

    @BeforeEach
    void setUp() {
        metricsRepository.deleteAll();
        reportRepository.deleteAll();
    }

    // ------------------------------------------------------------------ helpers

    private IncidentReport report(LocalDateTime createdAt, String status, String severity) {
        return reportRepository.save(IncidentReport.builder().incidentId(UUID.randomUUID()).title("t").description("d")
                .severity(severity).priority("LOW").status(status).serviceName("PAYMENT_SERVICE").category("DATABASE")
                .createdAt(createdAt).updatedAt(createdAt)
                .assignmentDeadline(createdAt.plusDays(1)).resolutionDeadline(createdAt.plusDays(7)).build());
    }

    private void resolved(LocalDateTime createdAt, LocalDateTime resolvedAt, long seconds) {
        IncidentReport report = report(createdAt, "RESOLVED", "LOW");
        report.setResolvedAt(resolvedAt);
        report.setResolutionTimeSeconds(seconds);
        reportRepository.save(report);
    }

    private DailyMetrics savedDay(LocalDate day) {
        service.refreshRecentDays(TODAY);
        return metricsRepository.findById(day).orElseThrow();
    }

    // ------------------------------------------------------------------ what one day means

    @Test
    void theNumbersOfADayAreSaved() {
        LocalDateTime morning = YESTERDAY.atTime(9, 0);
        report(morning, "OPEN", "HIGH");
        report(morning, "ASSIGNED", "CRITICAL");
        report(morning, "OPEN", "LOW");
        resolved(morning, morning.plusHours(1), 3600);

        DailyMetrics day = savedDay(YESTERDAY);

        assertEquals(4, day.getTotalIncidents());
        assertEquals(3, day.getOpenIncidents(), "the resolved one is not open");
        assertEquals(2, day.getHighSeverityCount(), "HIGH and CRITICAL");
        assertEquals(1, day.getResolvedIncidents());
        assertEquals(3600, day.getAvgResolutionTime());
    }

    @Test
    void anIncidentResolvedDaysLaterCountsOnTheDayItWasResolved() {
        LocalDateTime threeDaysAgo = TODAY.minusDays(3).atTime(9, 0);
        resolved(threeDaysAgo, YESTERDAY.atTime(9, 0), 172800);

        assertEquals(1, savedDay(TODAY.minusDays(3)).getTotalIncidents());
        assertEquals(0, savedDay(TODAY.minusDays(3)).getResolvedIncidents());
        assertEquals(0, savedDay(YESTERDAY).getTotalIncidents());
        assertEquals(1, savedDay(YESTERDAY).getResolvedIncidents());
    }

    @Test
    void theSlaBreachesOfTheDayAreCounted() {
        IncidentReport late = report(YESTERDAY.atTime(9, 0), "OPEN", "LOW");
        late.setAssignmentSlaBreached(true);
        late.setResolutionSlaBreached(true);
        reportRepository.save(late);
        report(YESTERDAY.atTime(10, 0), "OPEN", "LOW");

        DailyMetrics day = savedDay(YESTERDAY);

        assertEquals(1, day.getAssignmentSLA_BreachedCount());
        assertEquals(1, day.getResolutionSLA_BreachedCount());
    }

    @Test
    void midnightIsTheBorderBetweenTwoDays() {
        report(YESTERDAY.atTime(23, 59, 59), "OPEN", "LOW");
        report(TODAY.atStartOfDay(), "OPEN", "LOW");

        assertEquals(1, savedDay(YESTERDAY).getTotalIncidents(), "today's 00:00:00 belongs to today");
    }

    @Test
    void aDayWithNoIncidentsIsAllZeros() {
        DailyMetrics day = savedDay(YESTERDAY);

        assertEquals(0, day.getTotalIncidents());
        assertEquals(0, day.getResolvedIncidents());
        assertEquals(0, day.getAvgResolutionTime(), "not null: the column does not allow it");
    }

    // ------------------------------------------------------------------ the job

    @Test
    void theJobFillsTheLastSevenFullDaysAndNotToday() {
        service.refreshRecentDays(TODAY);

        List<DailyMetrics> rows = metricsRepository.findAll();
        assertEquals(7, rows.size());
        assertTrue(metricsRepository.findById(TODAY).isEmpty(), "today is not finished yet");
        assertTrue(metricsRepository.findById(TODAY.minusDays(7)).isPresent());
        assertTrue(metricsRepository.findById(TODAY.minusDays(8)).isEmpty());
    }

    @Test
    void runningTheJobAgainReplacesTheRowAndPicksUpLateEvents() {
        report(YESTERDAY.atTime(9, 0), "OPEN", "LOW");
        service.refreshRecentDays(TODAY);
        report(YESTERDAY.atTime(10, 0), "OPEN", "LOW");   // an event that arrived late

        service.refreshRecentDays(TODAY);

        assertEquals(7, metricsRepository.count(), "no second row for the same day");
        assertEquals(2, metricsRepository.findById(YESTERDAY).orElseThrow().getTotalIncidents());
    }

    // ------------------------------------------------------------------ reading

    @Test
    void readingGivesTodayLiveThenTheSavedDaysNewestFirst() {
        report(TODAY.atTime(8, 0), "OPEN", "LOW");
        report(YESTERDAY.atTime(9, 0), "OPEN", "LOW");
        report(YESTERDAY.atTime(10, 0), "OPEN", "LOW");
        service.refreshRecentDays(TODAY);

        List<DailyMetricsResponseDTO> days = service.getDailyMetrics(TODAY, 3);

        assertEquals(List.of(TODAY, YESTERDAY, TODAY.minusDays(2)), days.stream().map(DailyMetricsResponseDTO::getDate).toList());
        assertEquals(1, days.get(0).getTotalIncidents());
        assertEquals(2, days.get(1).getTotalIncidents());
        assertTrue(metricsRepository.findById(TODAY).isEmpty(), "reading saves nothing");
    }

    @Test
    void askingForOneDayGivesOnlyToday() {
        service.refreshRecentDays(TODAY);

        assertEquals(1, service.getDailyMetrics(TODAY, 1).size());
    }

    // ------------------------------------------------------------------ the URL

    @Test
    void theEndpointAsksTheServiceWithTheDaysFromTheUrl() throws Exception {
        DailyMetricsService fake = mock(DailyMetricsService.class);
        ReportingController controller = new ReportingController(mock(ReportingService.class),
                mock(EngineerPerformanceService.class), mock(EngineerWorkloadService.class), fake);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/reports/metrics/daily").param("days", "30")).andExpect(status().isOk());

        verify(fake).getDailyMetrics(any(), eq(30));
    }

    @Test
    void theEndpointRefusesADaysValueOutOfRange() throws Exception {
        DailyMetricsService fake = mock(DailyMetricsService.class);
        ReportingController controller = new ReportingController(mock(ReportingService.class),
                mock(EngineerPerformanceService.class), mock(EngineerWorkloadService.class), fake);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/reports/metrics/daily").param("days", "0")).andExpect(status().isBadRequest());

        verifyNoInteractions(fake);
    }
}
