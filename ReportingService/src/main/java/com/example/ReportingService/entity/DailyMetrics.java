package com.example.ReportingService.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

import java.time.LocalDate;
//This Entity avoids heavy queries → fast dashboards
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
@Entity
public class DailyMetrics {

    @Id
    private LocalDate date;

    private long totalIncidents;

    private long openIncidents;
    private long resolvedIncidents;

    private long highSeverityCount;

    private double avgResolutionTime;

    private long assignmentSLA_BreachedCount;
    private  long resolutionSLA_BreachedCount;
}

