package com.example.UserService.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserCredentialDTO {
    private UUID userid;

    private String username;

    private String email;
    // ADDED 2026-10-17 (SLA notifications step 3): who this user's supervisor is (null = none).
    // NotificationService uses it to tell the supervisor about an SLA alert.
    private UUID supervisorId;

}