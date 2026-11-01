package com.example.ReportingService.dto;

import lombok.*;

import java.time.LocalDate;

// Issue 61: one day of GET /reports/metrics/daily. What each number means: see DailyMetricsService.
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DailyMetricsResponseDTO {

    private LocalDate date;

    private long totalIncidents;
    private long openIncidents;
    private long resolvedIncidents;
    private long highSeverityCount;

    private double avgResolutionTime;

    private long assignmentSLA_BreachedCount;
    private long resolutionSLA_BreachedCount;
}
