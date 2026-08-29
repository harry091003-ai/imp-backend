package com.example.Ai_Service.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngineerPerformanceStatsDTO {

    private UUID engineerId;
    // total resolved incidents
    private long totalIncidentsResolved;

    // avg resolution time
    private double avgResolutionTimeSeconds;

    // successful SLA rate (0 to 1)
    private double slaSuccessRate;



    private Double avgSlaUtilization;

    // expertise score
    private double expertiseScore;

    //

    private LocalDateTime lastUpdated;
}
