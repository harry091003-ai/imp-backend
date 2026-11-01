package com.example.ReportingService.service;

import com.example.ReportingService.dto.DailyMetricsResponseDTO;
import com.example.ReportingService.entity.DailyMetrics;
import com.example.ReportingService.repository.DailtyMetricsReportRepository;
import com.example.ReportingService.repository.IncidentReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Issue 61 (2026-11-01): the daily_metrics table existed but nothing ever wrote to it.
// What one day's numbers mean:
//   totalIncidents, highSeverityCount, both SLA counts: incidents CREATED that day
//   openIncidents:                                      of those, how many are still not RESOLVED / CLOSED
//   resolvedIncidents, avgResolutionTime:               incidents RESOLVED that day (they may be older)
@Service
@RequiredArgsConstructor
public class DailyMetricsService {

    // A late event (or a resolve) can still change a recent day, and the service may have been off for days.
    // So each run works out the last 7 full days again. Older days stay as they were saved.
    static final int DAYS_TO_REFRESH = 7;

    private final DailtyMetricsReportRepository dailyMetricsRepository;
    private final IncidentReportRepository incidentReportRepository;

    public void refreshRecentDays(LocalDate today) {
        for (int i = 1; i <= DAYS_TO_REFRESH; i++) {
            // The date is the primary key, so a second run replaces the row and never adds another one.
            dailyMetricsRepository.save(calculate(today.minusDays(i)));
        }
    }

    // Newest first. Today is still changing, so it is worked out live and not saved.
    public List<DailyMetricsResponseDTO> getDailyMetrics(LocalDate today, int days) {
        List<DailyMetricsResponseDTO> answer = new ArrayList<>();
        answer.add(toDto(calculate(today)));
        if (days > 1) {
            dailyMetricsRepository
                    .findByDateBetweenOrderByDateDesc(today.minusDays(days - 1), today.minusDays(1))
                    .forEach(day -> answer.add(toDto(day)));
        }
        return answer;
    }

    private DailyMetrics calculate(LocalDate day) {
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = day.plusDays(1).atStartOfDay();
        Object[] created = incidentReportRepository.countsForIncidentsCreatedBetween(from, to).get(0);
        Object[] resolved = incidentReportRepository.countsForIncidentsResolvedBetween(from, to).get(0);

        return DailyMetrics.builder()
                .date(day)
                .totalIncidents(asLong(created[0]))
                .openIncidents(asLong(created[1]))
                .highSeverityCount(asLong(created[2]))
                .assignmentSLA_BreachedCount(asLong(created[3]))
                .resolutionSLA_BreachedCount(asLong(created[4]))
                .resolvedIncidents(asLong(resolved[0]))
                .avgResolutionTime(resolved[1] == null ? 0 : ((Number) resolved[1]).doubleValue())   // nothing resolved: 0
                .build();
    }

    private static long asLong(Object value) {
        return ((Number) value).longValue();
    }

    private static DailyMetricsResponseDTO toDto(DailyMetrics day) {
        return DailyMetricsResponseDTO.builder()
                .date(day.getDate())
                .totalIncidents(day.getTotalIncidents())
                .openIncidents(day.getOpenIncidents())
                .resolvedIncidents(day.getResolvedIncidents())
                .highSeverityCount(day.getHighSeverityCount())
                .avgResolutionTime(day.getAvgResolutionTime())
                .assignmentSLA_BreachedCount(day.getAssignmentSLA_BreachedCount())
                .resolutionSLA_BreachedCount(day.getResolutionSLA_BreachedCount())
                .build();
    }
}
