package com.example.AuthService.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.util.UUID;

// Auth v2 part B: answer of POST /auth/refresh - a NEW pair. The refresh token that was sent is used up.
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RefreshResponseDTO {
    private UUID userid;
    private String JWTtoken;        // same field name as in LoginResponseDTO, so a client reads both the same way
    // BCR-02 (2026-11-02): never in the JSON. RefreshController puts it in the HttpOnly imp_refresh cookie.
    @JsonIgnore
    private String refreshToken;
}
