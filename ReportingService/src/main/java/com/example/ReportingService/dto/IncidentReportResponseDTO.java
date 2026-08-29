package com.example.ReportingService.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class IncidentReportResponseDTO {

    private UUID incidentId;
    private String title;

    private String severity;
    private String priority;
    private String status;

    private String serviceName;
    private String category;

    private UUID createdBy;
    private UUID assignedTo;
    private UUID assignedBy;

    private LocalDateTime createdAt;
    private LocalDateTime assignedAt;
    private LocalDateTime resolvedAt;

    private Long timeToAssignSeconds;
    private Long resolutionTimeSeconds;
}
