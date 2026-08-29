package com.project.events;

import lombok.*;

import java.util.UUID;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SLA_BreachedEvent {
    private UUID incidentId;
    private String SLA_BreachedType;
}
