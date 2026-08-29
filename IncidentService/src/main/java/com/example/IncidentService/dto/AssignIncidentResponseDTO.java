package com.example.IncidentService.dto;

import lombok.*;

import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class AssignIncidentResponseDTO {

    private UUID incidentid;
    private UUID enginerId;
    private UUID assignedBy;
    private String message;
}
