package com.example.UserService.dto;

import lombok.*;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserRoleResponseDTO {
    private UUID userId;
    private String username;
    private Set<String> rolename;
    private String message;
}
