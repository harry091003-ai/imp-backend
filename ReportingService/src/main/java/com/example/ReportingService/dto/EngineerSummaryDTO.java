package com.example.ReportingService.dto;

import lombok.*;

import java.util.UUID;

// AI plan phase 1: one engineer's numbers for Ai-Service's ranking. Stats are for the asked category + service.
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EngineerSummaryDTO {

    private UUID engineerId;
    private long totalIncidentsResolved;
    private double expertiseScore;
    private double slaSuccessRate;
    private int activeIncidentCount;
}
