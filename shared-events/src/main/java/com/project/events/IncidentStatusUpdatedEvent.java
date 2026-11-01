package com.project.events;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Getter
@Setter
public class IncidentStatusUpdatedEvent {
    private UUID incidentId;
    private String title;
    private String description;
    private String OldStatus;
    private String UpdatedStatus;
    private UUID createdBy;
    private UUID assignedTo;
    private UUID assignedBy;
    private UUID UpdatedBy;
    private UUID performedBy;// to know who performed the action.
    private UUID eventId;// this will be a unique identifier for each event.
    private LocalDateTime UpdatedAt;
    private String serviceName;
    private String category;
    private String environment; // AI plan phase 2. An older message has none: read as null
    private String message; // Optional field to include any additional information about the incident status update
    //if updated status is RESOLVED then below fields also
    private String rootCause;
    private String resolutionNotes;

    private LocalDateTime resolvedAt;
}
