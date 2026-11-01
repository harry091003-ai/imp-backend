package com.example.Ai_Service.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngineerCandidateDTO {

    private UUID engineerId;

    // Retrieval Signals: how many similar incidents they solved (0 = joined as a team member), and the closest match (0 to 1)
    private int similarIncidentCount;
    private double bestSimilarity;

    // Workload Signals
    private int activeIncidents;

    // Performance Signals
    private double expertiseScore;
    private double slaSuccessRate;

    // False = ReportingService did not answer, so the 3 numbers above are unknown and the score used neutral values.
    private boolean statsAvailable;

    private double finalRankingScore;
}
