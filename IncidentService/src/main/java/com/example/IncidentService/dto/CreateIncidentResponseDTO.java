package com.example.IncidentService.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
@Builder
public class CreateIncidentResponseDTO {

    private UUID incidentId;
    private String message;
}
