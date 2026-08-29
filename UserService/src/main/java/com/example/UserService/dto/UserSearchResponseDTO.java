package com.example.UserService.dto;

import lombok.*;

import java.util.UUID;
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserSearchResponseDTO {
    private UUID userid;

    private String username;

    private String email;
    private UUID supervisorId;
}
