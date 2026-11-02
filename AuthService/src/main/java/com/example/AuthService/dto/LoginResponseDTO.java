package com.example.AuthService.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    // BCR-02 (2026-11-02): never in the JSON. LoginController puts it in the HttpOnly imp_refresh cookie instead,
    // where no script on the page can read it. The field stays for the Java side (controller, tests).
    @JsonIgnore
    private String refreshToken;
}
