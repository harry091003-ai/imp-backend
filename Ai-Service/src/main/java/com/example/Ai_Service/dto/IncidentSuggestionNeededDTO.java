package com.example.Ai_Service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

// B4 step 4: this DTO had no validation at all.
// Its text goes straight into a PAID Gemini call, so every field now has a limit.
@Data
public class IncidentSuggestionNeededDTO {

    // Required. RagIntegrationService puts title and description into Map.of(...),
    // and Map.of crashes on a null value - so a missing one was a 500.
    // Limits match the incidents table (title 255, description 2000).
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

}
