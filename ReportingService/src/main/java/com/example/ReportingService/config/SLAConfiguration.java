package com.example.ReportingService.config;

import org.springframework.context.annotation.Configuration;

import java.time.Duration;

// SLA = how long an incident may take. Fixed 2026-10-17 (issues 19, 59): it held test values
// (30-50 seconds), and CRITICAL had none (0 seconds = breached at once).
//
//   Priority   assigned within   resolved within
//   CRITICAL   15 minutes        4 hours
//   HIGH       1 hour            1 day
//   MEDIUM     4 hours           3 days
//   LOW        1 day             7 days
//
// Old rows keep their old deadlines (not recalculated, issue 66).
@Configuration
public class SLAConfiguration {

    public static long getResolutionSLA_INSeconds(String priority){
        return switch (known(priority)) {
            case "CRITICAL" -> Duration.ofHours(4).toSeconds();
            case "HIGH"     -> Duration.ofDays(1).toSeconds();
            case "MEDIUM"   -> Duration.ofDays(3).toSeconds();
            case "LOW"      -> Duration.ofDays(7).toSeconds();
            default -> throw unknown(priority);   // cannot happen: known() already checked
        };
    }

    public static long getAssignmentSLA_INSeconds(String priority){
        return switch (known(priority)) {
            case "CRITICAL" -> Duration.ofMinutes(15).toSeconds();
            case "HIGH"     -> Duration.ofHours(1).toSeconds();
            case "MEDIUM"   -> Duration.ofHours(4).toSeconds();
            case "LOW"      -> Duration.ofDays(1).toSeconds();
            default -> throw unknown(priority);
        };
    }

    // An unknown priority fails loudly (Kafka retries, then the dead-letter topic) - no silent wrong time.
    // A new priority in IncidentService must be added to BOTH methods above.
    private static String known(String priority) {
        if (priority == null) {
            throw unknown(null);
        }
        return priority;
    }

    private static IllegalArgumentException unknown(String priority) {
        return new IllegalArgumentException("No SLA rule for priority: " + priority
                + ". Known priorities: CRITICAL, HIGH, MEDIUM, LOW");
    }
}
