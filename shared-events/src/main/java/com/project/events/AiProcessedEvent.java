package com.project.events;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class AiProcessedEvent {
    private UUID incidentId;
    private String aiCategory;
    private String aiSeverity;
    private String aiPriority;
    private String aiServiceName;
    private double confidence;
    private boolean conflict;
    private String aiSummary;

}
