package com.example.ReportingService.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
@Entity
// This Table is made to handle the idempotence (for all the other tables including this table)
public class ProcessedEvent {
    @Id
    private UUID eventId;

    private LocalDateTime processedAt;

}
