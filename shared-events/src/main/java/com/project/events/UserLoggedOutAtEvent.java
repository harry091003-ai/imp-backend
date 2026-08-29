package com.project.events;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserLoggedOutAtEvent {
    private UUID userid;
    private LocalDateTime userLastLoggedOutAt;
}
