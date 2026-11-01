package com.example.Ai_Service.dto;

import lombok.*;

import java.util.List;

// AI plan phase 1: the answer now says what happened (status) and what it is based on (sources), so a person can check it.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentResolvementSuggestionDTO {

    public enum Status { OK, NOT_ENOUGH_SIMILAR_INCIDENTS }

    private Status status;
    private String rootCause;
    private String resolutionNotes;
    // True = the sources have the same category and service. False = the search had to look at everything.
    private boolean sameCategoryAndService;
    private List<SimilarIncidentSourceDTO> sources;
}
