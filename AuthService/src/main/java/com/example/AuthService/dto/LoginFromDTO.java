package com.example.AuthService.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginFromDTO {
    // NOTE: this field is never read. LoginUserService authenticates by email
    // only (checked - grep finds no request.getUsername() anywhere in it). A
    // caller is still required to send something here because of @NotBlank.
    // Pre-existing behaviour; not this step's job to change, just documenting it.
    @NotBlank
    private String username;

    // max=100 added B4 step 2, matches users.email (varchar(100)).
    @NotBlank
    @Email
    @Size(max = 100, message = "Email must be at most 100 characters")
    private String email;

    // max=72 added B4 step 2 - BCrypt silently ignores anything past 72 bytes,
    // so capping it here means the whole password actually gets checked.
    @NotBlank
    @Size(min = 6, max = 72, message = "Password must be between 6 and 72 characters")
    private String password;
}
