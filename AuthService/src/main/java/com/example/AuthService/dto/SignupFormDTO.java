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

    // max=25 added B4 step 2 - the users.username column is varchar(25). Without
    // this, a 26-character username passed validation, then MySQL rejected the
    // insert, and the client got a confusing 409 instead of a clean 400.
    @NotBlank
    @Size(max = 25, message = "Username must be at most 25 characters")
    private String username;

    // max=100 added B4 step 2, same reason: users.email is varchar(100).
    @NotBlank
    @Email
    @Size(max = 100, message = "Email must be at most 100 characters")
    private String email;

    // max=72 added B4 step 2. BCrypt only reads the first 72 BYTES of a password
    // and silently ignores the rest - it does not throw an error. Two different
    // long passwords that share the same first 72 bytes would hash identically.
    // Capping the input here means what the user typed is what actually gets
    // checked, and stops someone sending a multi-MB "password" just to burn CPU.
    @NotBlank
    @Size(min = 6, max = 72, message = "Password must be between 6 and 72 characters")
    private String password;


}
