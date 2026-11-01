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

    // AI plan phase 2: remembered with the incident. An older message has none: read as null.
    private String severity;
    private String priority;
    private String environment;

    private String rootCause;
    private String resolutionNotes;

    private UUID resolvedBy;

    private long resolutionTimeInSeconds;

    private boolean assignmentSlaBreached;
    private boolean resolutionSlaBreached;

    private LocalDateTime resolvedAt;
}
