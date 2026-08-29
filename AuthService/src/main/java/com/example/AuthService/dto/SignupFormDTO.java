package com.example.AuthService.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class SignupFormDTO {
    private UUID userid;
    @NotBlank
    private String username;
    @NotBlank
    @Email
    private String email;
    // @NotBlank was missing here: @Size alone lets null through, so a signup with no
    // password at all passed validation and blew up inside passwordEncoder.encode(null).
    @NotBlank
    @Size(min=6)
    private String password;


}
