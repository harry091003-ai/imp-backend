package com.example.AuthService.dto;

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
    private String refreshToken;
}
