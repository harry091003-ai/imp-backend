package com.example.IncidentService.dto;

import lombok.*;

import java.util.UUID;

//mainly used for getting the email of the user from the UserService as its used for sending mail in Notification servcie.
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
