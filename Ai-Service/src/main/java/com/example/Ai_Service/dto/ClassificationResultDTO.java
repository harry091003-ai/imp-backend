package com.example.Ai_Service.dto;

import lombok.*;

// AI plan phase 3: the answer of POST /ai/classify. A suggestion for the form: the user decides.
// Every value is a real one, or empty: severity and priority are left empty if the AI gave an impossible value.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationResultDTO {

    private String category;
    private String severity;
    private String priority;
    private String serviceName;
    private String summary;
    private Double confidence;   // 0 to 1
    // False = too unsure, so category and service are the safe OTHER / OTHERS. The screen should not pre-fill them.
    private boolean confident;
}
