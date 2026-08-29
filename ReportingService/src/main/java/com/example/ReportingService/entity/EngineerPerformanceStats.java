package com.example.ReportingService.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;
// This Entity Stores the Engineer Performance Stats by Using EngineerPerformanceService


@Entity
@Table(
//        here we are apply index for fast retrieval
        indexes = {
                @Index(name = "idx_engineer_category_service",
                        columnList = "engineerId,category,serviceName")
        }
//        here we need to apply the unique constraint on the combination of engineerId, category, and serviceName because we want to maintain only one record for this combination...
//        as  there may be chance as duplicate rows gets created under concurrent Kafka processing.
        ,uniqueConstraints = {
                @UniqueConstraint(name = "uc_engineer_category_service",
                        columnNames = {"engineerId", "category", "serviceName"})

}

)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngineerPerformanceStats {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID engineerId;

    private String category;

    private String serviceName;

    // total resolved incidents
    private long totalIncidentsResolved;

    // avg resolution time
    private double avgResolutionTimeSeconds;

    // successful SLA rate (0 to 1)
    private double slaSuccessRate;


   // 0 to 1 score
    private Double avgSlaUtilization;

    // expertise score out of 100%
    private double expertiseScore;

    //

    private LocalDateTime lastUpdated;
}