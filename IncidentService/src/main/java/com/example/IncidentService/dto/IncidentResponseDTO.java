package com.example.IncidentService.dto;

import com.example.IncidentService.entity.IncidentStatus;
import com.example.IncidentService.entity.Priority;
import com.example.IncidentService.entity.Severity;
import lombok.*;

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
}
