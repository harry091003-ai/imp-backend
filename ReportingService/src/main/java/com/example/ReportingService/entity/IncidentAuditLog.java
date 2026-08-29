package com.example.ReportingService.entity;

import com.project.events.enumsUsedByEvents.EventType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

//This gives:
//	• full history
//	• debugging
//audit trail
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
@Entity
//audiLog means who did what, when
public class IncidentAuditLog {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID incidentId;
    private UUID eventId;// **although we handle the idempotence by making a seperate 'ProcessedEvent' table having this same field eventId..but here we make this field  also because of tracking and debugging as  in future multiple same IncidentId's Comes with same EventType  due to network failure(issue very common in Distributed system) then we must need to debug and understand it that what happens actually to resolve bugs
    @Enumerated(EnumType.STRING)
    private EventType eventType;// type of incident like

    private UUID performedBy;

    private String oldValue;
    private String newValue;

    private LocalDateTime timestamp;
}
