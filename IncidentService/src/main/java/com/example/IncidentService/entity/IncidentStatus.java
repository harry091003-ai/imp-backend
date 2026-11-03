package com.example.IncidentService.entity;

import java.util.EnumSet;
import java.util.Set;

public enum IncidentStatus {
    OPEN,
    IN_PROGRESS,
    ASSIGNED,
    RESOLVED,
    CLOSED,
    REOPENED;

    // BCR-03 (2026-11-02): which status may follow this one through a status change.
    // ASSIGNED is never in a list: only the assign endpoint sets it, together with an engineer.
    // The same status again is not a change, so it is never in its own list.
    // Who may make the change is checked separately, before this (IncidentService.updateIncidentStatusBasedOnRole).
    // The frontend shows the same table in src/features/incidents/components/statusFlow.ts.
    public Set<IncidentStatus> allowedNext() {
        return switch (this) {
            case OPEN, ASSIGNED, REOPENED -> EnumSet.of(IN_PROGRESS, RESOLVED);
            case IN_PROGRESS -> EnumSet.of(RESOLVED);
            case RESOLVED -> EnumSet.of(CLOSED, REOPENED);
            case CLOSED -> EnumSet.of(REOPENED);
        };
    }
}
