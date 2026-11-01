package com.example.Ai_Service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
//Incident Assignment Engineer Recommendation request DTO
// B4 step 4: this DTO had no validation at all.
// Its title + description become a PAID Gemini embedding, so both are capped.
public class EngineerRecommendationRequestDTO {

    // Required. The service builds its search text as title + " " + description.
    // If either is missing, Java writes the word "null" there, and we would pay
    // Gemini to search for "null null". Limits match the incidents table.
    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be at most 255 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    // Optional - the search filter skips them when null.
    // 50 is plenty: the longest real value is NOTIFICATION_SERVICE (20 chars).
    @Pattern(regexp = IncidentValues.CATEGORY, message = IncidentValues.CATEGORY_MESSAGE)
    @Size(max = 50, message = "Category must be at most 50 characters")
    private String category;

    @Pattern(regexp = IncidentValues.SERVICE, message = IncidentValues.SERVICE_MESSAGE)
    @Size(max = 50, message = "Service name must be at most 50 characters")
    private String serviceName;

    // Not read by the service today. Capped anyway so it can't carry a huge value.
    @Size(max = 50, message = "Severity must be at most 50 characters")
    private String severity;
}
