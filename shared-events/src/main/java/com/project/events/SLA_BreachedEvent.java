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

    // Step 2: who to tell. assignedTo is null while nobody is assigned.
    // An older message without these fields still reads fine (both null).
    private UUID createdBy;
    private UUID assignedTo;
}
