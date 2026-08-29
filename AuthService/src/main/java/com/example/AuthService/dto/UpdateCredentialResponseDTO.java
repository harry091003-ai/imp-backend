package com.example.AuthService.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateCredentialResponseDTO {
    private UUID userId;
    private String updatedEmail;
    private String updatedUsername;
    private LocalDateTime updatedAt;
    private String message;
}
