package com.example.Ai_Service.dto;

import lombok.Data;

@Data
public class IncidentSuggestionNeededDTO {
    private String title;
    private String description;
    private String category;
    private String serviceName;

}
