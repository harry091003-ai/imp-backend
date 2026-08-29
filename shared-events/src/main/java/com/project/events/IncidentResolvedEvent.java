package com.project.events;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IncidentResolvedEvent {

    private UUID incidentId;
    private UUID eventId;
    private UUID performedBy;

    private String title;
    private String description;

    private String category;
    private String serviceName;
    private String severity;

    private String rootCause;
    private String resolutionNotes;

    private LocalDateTime resolvedAt;

}
