package com.example.UserService.dto;

import lombok.*;

import java.util.UUID;

// BCR-05: one answer row of POST /users/lookup. ONLY the id and the name: any logged-in user may ask,
// so no email, status or roles may ever be added here (UserLookupTest checks it).
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserNameDTO {
    private UUID userid;

    private String username;
}
