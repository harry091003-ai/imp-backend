package com.example.Ai_Service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IncidentResolvementSuggestionDTO {
    private String rootCause;
    private String resolutionNotes;
    private String anyError;
}
