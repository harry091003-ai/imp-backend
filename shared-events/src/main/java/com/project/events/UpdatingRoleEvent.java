package com.project.events;

import lombok.*;

import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdatingRoleEvent {
    private String username;
    private Set<String> role;
}
