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
public class IncidentAuditDTO {

    private UUID incidentId;
    private UUID eventId;

    private String eventType;
    private UUID performedBy;

    private String oldValue;
    private String newValue;

    private LocalDateTime timestamp;
}
