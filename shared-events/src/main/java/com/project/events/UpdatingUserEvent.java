package com.project.events;

import lombok.*;

import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UpdatingUserEvent {
    private UUID userId;
    private String UpdatedUserName;
    private String UpdatedEmail;
}
