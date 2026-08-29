package com.example.UserService.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserCredentialDTO {
    private UUID userid;

    private String username;

    private String email;

}