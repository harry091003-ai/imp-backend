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
public class IncidentAssignedEvent {
    private UUID incidentId;
    private String title;
    private UUID createdBy;
    private UUID assignedTo;
    private UUID assignedBy;
    private UUID performedBy;// to know who performed the action.
    private UUID eventId;// this will be a unique identifier for each event.
    private LocalDateTime assignedAt;
    private String message; // Optional field to include any additional information about the incident assignment
    private String serviceName;
    private String category;
}
