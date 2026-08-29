package com.example.AuthService.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SignupResponseDTO {
    private String username;
    private String message;

}
