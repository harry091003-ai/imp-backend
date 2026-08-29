package com.example.Ai_Service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiInsight {

    @Id
    private UUID incidentId;

    private String aiCategory;
    private String aiSeverity;
    private String aiPriority;
    private String aiServiceName;

    private boolean conflict;

    private Double confidence;

    @Column(length = 2000)
    private String summary;
}