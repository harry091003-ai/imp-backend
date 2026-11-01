package com.example.UserService.dto;

import com.example.UserService.entity.Status;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Email;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserResponseDTO {

    private UUID userid;

    private String username;

    private String email;

    private Status status;
    private Set<String> roles;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime lastLoginAt;

    // Issue 63 (2026-11-01): passwordUpdatedAt, failedLoginAttempts, accountLocked and enabled were removed from this
    // answer. AuthService owns them and never sends changes here, so they were always wrong (enabled=false for everyone).
    private UUID supervisorId;
}
