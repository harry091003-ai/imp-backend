package com.example.ReportingService.dto;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MetricsResponseDTO {

    private long totalIncidents;
    private long openIncidents;
    private long resolvedIncidents;

    private Double avgResolutionTime;
    private Double avgAssignmentTime;

//count of assignment and resolution SLA Breached
    private long assignmentSLA_BreachedCount;
    private  long resolutionSLA_BreachedCount;
}
