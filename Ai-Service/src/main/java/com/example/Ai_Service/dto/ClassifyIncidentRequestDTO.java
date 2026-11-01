package com.example.Ai_Service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// AI plan phase 3: the text the user has typed so far. It goes into a PAID Gemini call, so it has limits.
// The limits match the incidents table (title 255, description 2000).
@Data
public class ClassifyIncidentRequestDTO {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be at most 255 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;
}
