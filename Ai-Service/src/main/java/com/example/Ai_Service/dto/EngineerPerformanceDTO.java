package com.example.Ai_Service.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class EngineerPerformanceDTO {

    private double expertiseScore;

    private double slaSuccessRate;

    private double avgResolutionTimeSeconds;
}