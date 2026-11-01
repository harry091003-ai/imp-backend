package com.example.IncidentService.dto;

import com.example.IncidentService.entity.Environment;
import com.example.IncidentService.entity.IncidentCategory;
import com.example.IncidentService.entity.IncidentStatus;
import com.example.IncidentService.entity.Priority;
import com.example.IncidentService.entity.ServiceName;
import com.example.IncidentService.entity.Severity;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
@Builder
public class IncidentResponseDTO {

    private UUID id;
    private String title;
    private Severity severity;
    private IncidentStatus incidentStatus;
    private Priority priority;
    private UUID createdBy;
    private UUID assignedTo;
    private UUID assignedBy;
    // Issue 58 (2026-11-01): a read returned only the 8 fields above, so nobody could see the rest of an incident.
    private String description;
    private ServiceName serviceName;
    private IncidentCategory category;
    private Environment environment;
    private String affectedSource;
    private String location;
    private String rootCause;
    private String resolutionNotes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
