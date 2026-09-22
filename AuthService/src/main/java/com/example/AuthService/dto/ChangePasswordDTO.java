package com.example.AuthService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChangePasswordDTO {
    // max=72 added B4 step 2 - BCrypt silently ignores anything past 72 bytes.
    @NotBlank(message = "Current password is required")
    @Size(max = 72, message = "Current password must be at most 72 characters")
    private String oldpassword;

    @NotBlank(message = "New password is required")
    @Size(min = 6, max = 72, message = "New password must be between 6 and 72 characters")
    private String newpassword;
}
