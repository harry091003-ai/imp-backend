package com.example.NotificationService.dto;

import lombok.*;

import java.util.UUID;
@Data
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