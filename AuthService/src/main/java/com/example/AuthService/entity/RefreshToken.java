package com.example.AuthService.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

// Auth v2 part B: one refresh token (table made by Flyway V6).
// Only the SHA-256 hash of the token is kept - the token itself exists only on the client.
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "refresh_token", indexes = {
        @Index(name = "idx_refresh_token_user", columnList = "user_id"),
        @Index(name = "idx_refresh_token_family", columnList = "family_id")
})
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;          // same for every token made from one login

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;       // SHA-256 of the token, as 64 hex characters

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;   // swapped for a new one; null = not used yet

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt; // cancelled (logout, password/role change, reuse); null = not cancelled
}
