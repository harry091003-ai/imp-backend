package com.project.events;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class IncidentResolutionReportEvent {
    private UUID incidentId;

    private String title;
    private String description;

    private String category;
    private String serviceName;

    private String rootCause;
    private String resolutionNotes;

    private UUID resolvedBy;

    private long resolutionTimeInSeconds;

    private boolean assignmentSlaBreached;
    private boolean resolutionSlaBreached;

    private double assignmentRiskScore;
    private double resolutionRiskScore;

    private LocalDateTime resolvedAt;
}
