package com.project.events;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class IncidentCreatedEvent {
        private UUID incidentId;
        private String title;
        private String description;
        private UUID createdBy;
        private String severity;
        private String priority;
       private UUID performedBy;// to know who performed the action.
         private UUID eventId;// this will be a unique identifier for each event.for handling idempotency
        private LocalDateTime createdAt;
        private String message; // Optional field to include any additional information about the incident creation
        private String serviceName;
        private String category;

}
