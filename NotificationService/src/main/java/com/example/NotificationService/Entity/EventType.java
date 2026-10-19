package com.example.NotificationService.Entity;

public enum EventType {
    INCIDENT_CREATED,
    INCIDENT_STATUS_UPDATED,
    INCIDENT_METADATA_UPDATED,
    INCIDENT_ASSIGNED,
    INCIDENT_RESOLVED,
    INCIDENT_REOPENED,
    USER_REGISTERED,
    USER_UPDATED,
    // ADDED 2026-10-17 (SLA notifications step 4). The database list gets the same 4 words in Flyway V2.
    // Saved as TEXT (@Enumerated STRING), so the order here does not matter - but every word here MUST also be
    // in the database list, or MySQL refuses the row (checked by NotificationMigrationV2Test).
    SLA_ASSIGNMENT_BREACHED,
    SLA_RESOLUTION_BREACHED,
    SLA_ASSIGNMENT_AT_RISK,
    SLA_RESOLUTION_AT_RISK
}
