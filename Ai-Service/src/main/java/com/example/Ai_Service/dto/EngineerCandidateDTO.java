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

    // Retrieval Signals: how many similar incidents solved from RAG
    private int similarIncidentCount;

    private double avgHistoricalResolutionTime;

    // Workload Signals
    private int activeIncidents;

    // Performance Signals
    private double expertiseScore;
    private double slaSuccessRate;


    private double finalRankingScore;
}