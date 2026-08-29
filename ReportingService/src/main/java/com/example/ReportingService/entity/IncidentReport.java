package com.example.ReportingService.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
@Entity
//here we also apply  indexing for getting the response of fetch data from db faster
@Table(name = "incident_report",
indexes = {
        @Index(name = "idx_status_severity_createdAt",columnList = "status,severity,createdAt"),
        @Index(name="idx_created_at",columnList = "createdAt"),
        @Index(name="idx_service_name",columnList = "serviceName"),
        @Index(name="idx_status",columnList = "status"),
        @Index(name = "idx_assignmentSlaBreached_assignmentSlaEventPublished",columnList="assignmentSlaBreached,assignmentSlaEventPublished"),
        @Index(name = "idx_resolutionSlaBreached_resolutionSlaEventPublished",columnList = "resolutionSlaBreached,resolutionSlaEventPublished"),
        @Index(name = "idx_assignedAt_assignmentSlaBreached_assignmentDeadline",columnList = "assignedAt,assignmentSlaBreached,assignmentDeadline"),
        @Index(name = "idx_resolvedAt_resolutionSlaBreached_status_resolutionDeadline",columnList = "resolvedAt,resolutionSlaBreached,status,resolutionDeadline")
})
public class IncidentReport {

    @Id
    private UUID incidentId;
    private String description;
    private String title;

    private String severity;
    private String priority;
    private String status;//status of incident like incident is open ,assigned ,closed etc

    private UUID createdBy;
    private UUID assignedTo;
    private UUID assignedBy;
    private UUID resolvedBy;

    private String serviceName;
    private String category;

    private LocalDateTime createdAt;
    private LocalDateTime assignedAt;
    private LocalDateTime resolvedAt;
private LocalDateTime updatedAt;

    private Long resolutionTimeSeconds; // MTTR
    private Long timeToAssignSeconds;// to measure How Fast Team Respond

// for checking Is the Service Layer Agreements are breached or preserved
    private boolean assignmentSlaBreached;
    private boolean resolutionSlaBreached;

    //storing the deadline time of assigning and resolving event
    private LocalDateTime assignmentDeadline;
    private LocalDateTime resolutionDeadline;

    //for checking is that SLAeventBreach Published or not
    private boolean assignmentSlaEventPublished = false;
    private boolean resolutionSlaEventPublished = false;

    // tells  riskScore and riskLevel before the sla actually breached
    private Double assignmentRiskScore;
    private Double resolutionRiskScore;
   @Builder.Default
    @Enumerated(EnumType.STRING)
    private SLA_RiskLevel assignmentRiskLevel=SLA_RiskLevel.NONE;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private SLA_RiskLevel resolutionRiskLevel=SLA_RiskLevel.NONE;

}
