package com.example.AuthService.dto;


import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRolesRequestDTO {
    @NotEmpty(message = "At least one role must be supplied")
    private Set<String> role;
}
