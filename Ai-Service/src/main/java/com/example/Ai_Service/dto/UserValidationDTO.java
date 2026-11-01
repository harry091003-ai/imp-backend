package com.example.Ai_Service.dto;

import lombok.*;

import java.util.Set;
import java.util.UUID;

// One user from UserService's /internal/validation/batch.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserValidationDTO {

    private UUID userid;
    private String status;
    private Set<String> roles;
}
