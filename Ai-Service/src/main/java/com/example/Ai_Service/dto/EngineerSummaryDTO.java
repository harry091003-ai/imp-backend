package com.example.Ai_Service.dto;

import lombok.*;

import java.util.UUID;

// One engineer's numbers from ReportingService's /internal/reports/engineer-summaries.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngineerSummaryDTO {

    private UUID engineerId;
    private long totalIncidentsResolved;
    private double expertiseScore;
    private double slaSuccessRate;
    private int activeIncidentCount;
}
