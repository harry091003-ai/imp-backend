package com.example.AuthService.dto;

import jakarta.websocket.server.ServerEndpoint;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginResponseDTO {
    private UUID userid;
    private String username;
    private String email;
    private Integer status;
    private String message;
    private String JWTtoken;
    // Auth v2 part B: lives 7 days. Send it to POST /auth/refresh when JWTtoken (15 min) runs out.
    private String refreshToken;
}
