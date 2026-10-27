package com.example.AuthService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

// Auth v2 part B: body of POST /auth/refresh.
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RefreshRequestDTO {
    // Our tokens are 43 characters. 200 leaves room and still stops a huge value from being hashed.
    @NotBlank
    @Size(max = 200)
    private String refreshToken;
}
