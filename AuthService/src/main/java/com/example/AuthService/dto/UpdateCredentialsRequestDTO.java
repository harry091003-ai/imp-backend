package com.example.AuthService.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateCredentialsRequestDTO {

    // Both added B4 step 2, matching users.email (100) / users.username (25).
    @Email
    @Size(max = 100, message = "Email must be at most 100 characters")
    private String newEmail;

    @Size(max = 25, message = "Username must be at most 25 characters")
    private String newUsername;

    // Was @NotNull. Changed to @NotBlank (B4 step 2) so a whitespace-only
    // password cannot pass - the other 3 password DTOs already use @NotBlank;
    // this one was the one left behind. Also capped at 72, same BCrypt reason
    // as the others.
    @NotBlank(message = "Current password is required")
    @Size(max = 72, message = "Password must be at most 72 characters")
    private String password;
}
