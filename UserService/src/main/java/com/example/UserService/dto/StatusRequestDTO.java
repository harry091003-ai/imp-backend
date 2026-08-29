package com.example.UserService.dto;

import com.example.UserService.entity.Status;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StatusRequestDTO {
    @NotNull(message = "Status is required")
    private Status status;
}
