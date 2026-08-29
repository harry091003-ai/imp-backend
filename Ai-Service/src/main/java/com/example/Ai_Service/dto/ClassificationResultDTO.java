package com.example.Ai_Service.dto;

import lombok.Data;
// this DTO is used  for getting the result from ai in this format
@Data
public class ClassificationResultDTO {

    private String category;
    private String severity;
    private String priority;
    private String serviceName;
    private String summary;
    private Double confidence;
}
