package com.project.events.enumsUsedByEvents;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaBreachingRiskEvent {

    private UUID incidentId;

    private String previousRiskLevel;

    private String currentRiskLevel;

    private Double riskScore;

    private String type; // ASSIGNMENT/RESOLUTION

}