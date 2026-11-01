package com.example.Ai_Service.dto;

import lombok.*;

// One past incident the suggestion was based on. Plain text values: they come from the vector store's metadata.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimilarIncidentSourceDTO {

    private String incidentId;
    private String title;
    private double similarity;   // 0 to 1
    private String resolvedBy;
    private String resolvedAt;
}
