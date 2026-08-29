package com.example.UserService.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AssignEngineerRequestDTO {
    @NotNull(message = "Engineer id is required")
    private UUID engineerId;

    @NotNull(message = "Supervisor id is required")
    private UUID supervisorId;
}
