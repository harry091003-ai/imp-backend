package com.example.ReportingService.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngineerWorkloadDTO {
    private UUID engineerId;
    private int activeIncidentCount;
    private LocalDateTime lastAssignedAt;
    private LocalDateTime updatedAt;
}
