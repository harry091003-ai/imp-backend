package com.example.ReportingService.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

// This Entity Store  workload on engineers by Storing activeIncidentCount (Total incidents assigned to that engineer that are currently not resolved) from EngineerWorkload Service
@Entity
@Table(
        indexes = {
                @Index(
                        name = "idx_engineer_id",
                        columnList = "engineerId"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngineerWorkload {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID engineerId;

    // currently assigned unresolved incidents
    private int activeIncidentCount;

    // optional future field
    private boolean available;
   //optional future field
    private String shiftName;

    private LocalDateTime lastAssignedAt;

    private LocalDateTime updatedAt;
}