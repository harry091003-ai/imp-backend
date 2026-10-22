package com.example.IncidentService.entity;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false,length = 2000)
    private String description;

    // means how serious is the bug in terms of impact on the system or users
    @Enumerated(value=EnumType.STRING)
    private Severity severity;

    @Builder.Default
    @Enumerated(value=EnumType.STRING)
    private IncidentStatus incidentStatus=IncidentStatus.OPEN;

    @Enumerated(value = EnumType.STRING)
    private Priority priority;

    @Column(nullable = false)
    private UUID createdBy; //  created by any User enginer/supervisor/admin

    private UUID assignedTo; // assigned to any User enginer ...and its nullable initially
    private UUID assignedBy; // assigned by any User supervisor/admin ...and its nullable initially

    @Enumerated(EnumType.STRING)
    private ServiceName serviceName;   // e.g. "payment-service", "user-service"

    // that handle  the incident Category
    @Enumerated(EnumType.STRING)
    private IncidentCategory category;

    //for Handling technical Environment:like Production,Staging,Development etc
    @Enumerated(value=EnumType.STRING)
    private Environment environment;

    // as it can be Technical or NonTechnical :
    // free text: "DB Cluster A", "Wind Turbine #12" ...so use it for specific identifier
    private String affectedSource;

    //for Handling Non-Tech Environment so we are using Locations like Plant A / Room 101 / Data Center / Zone B.
    private String location;

    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private String rootCause;
    private String resolutionNotes;

    // Issue 64 (bug 3): optimistic locking. Every save runs "UPDATE ... WHERE id=? AND version=<what I read>"
    // and adds 1. If someone else saved first, the version no longer matches, nothing is written and Spring
    // throws OptimisticLockingFailureException (common-web turns it into 409 CONCURRENT_UPDATE).
    // Hibernate fills it in; never set it by hand. A new incident starts at 0 (Flyway V4).
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
