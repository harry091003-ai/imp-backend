package com.example.Ai_Service.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
//Incident Assignment Engineer Recommendation request DTO
public class EngineerRecommendationRequestDTO {

    private String title;

    private String description;

    private String category;

    private String serviceName;

    private String severity;
}
