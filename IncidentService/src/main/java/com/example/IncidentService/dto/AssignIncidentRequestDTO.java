package com.example.IncidentService.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AssignIncidentRequestDTO {
    @NotNull(message = "Engineer id is required")
    private UUID enginerId;
}
