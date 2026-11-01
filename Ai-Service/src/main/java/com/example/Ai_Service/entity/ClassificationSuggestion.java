package com.example.Ai_Service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

import java.time.LocalDateTime;

// AI plan phase 3: what the AI suggested for a text, waiting to see what the user really chooses.
// The key is a hash of the title + description, because no incident (and no incident id) exists yet.
// The row is deleted when the incident is created, or after ai.classify.keep-hours.
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationSuggestion {

    @Id
    @Column(length = 64)
    private String textHash;

    private String aiCategory;
    private String aiSeverity;
    private String aiPriority;
    private String aiServiceName;

    private Double confidence;

    @Column(length = 2000)
    private String summary;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
