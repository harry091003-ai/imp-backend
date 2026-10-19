package com.example.ReportingService.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issues 19 and 59: the SLA times. Plain Java, no Spring, no database.
 * Written first: all 8 failed on the old test values.
 */
class SLAConfigurationTest {

    private static final List<String> FROM_MOST_TO_LEAST_URGENT = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");

    // ------------------------------------------------------------------ the agreed table (2026-10-17)

    @Test
    void assignmentTimesAreTheAgreedOnes() {
        assertEquals(Duration.ofMinutes(15).toSeconds(), SLAConfiguration.getAssignmentSLA_INSeconds("CRITICAL"));
        assertEquals(Duration.ofHours(1).toSeconds(), SLAConfiguration.getAssignmentSLA_INSeconds("HIGH"));
        assertEquals(Duration.ofHours(4).toSeconds(), SLAConfiguration.getAssignmentSLA_INSeconds("MEDIUM"));
        assertEquals(Duration.ofDays(1).toSeconds(), SLAConfiguration.getAssignmentSLA_INSeconds("LOW"));
    }

    @Test
    void resolutionTimesAreTheAgreedOnes() {
        assertEquals(Duration.ofHours(4).toSeconds(), SLAConfiguration.getResolutionSLA_INSeconds("CRITICAL"));
        assertEquals(Duration.ofDays(1).toSeconds(), SLAConfiguration.getResolutionSLA_INSeconds("HIGH"));
        assertEquals(Duration.ofDays(3).toSeconds(), SLAConfiguration.getResolutionSLA_INSeconds("MEDIUM"));
        assertEquals(Duration.ofDays(7).toSeconds(), SLAConfiguration.getResolutionSLA_INSeconds("LOW"));
    }

    // ------------------------------------------------------------------ rules that must always hold

    @Test
    void theMoreUrgentThePriorityTheShorterTheTime() {
        for (int i = 0; i < FROM_MOST_TO_LEAST_URGENT.size() - 1; i++) {
            String urgent = FROM_MOST_TO_LEAST_URGENT.get(i);
            String lessUrgent = FROM_MOST_TO_LEAST_URGENT.get(i + 1);
            assertTrue(SLAConfiguration.getAssignmentSLA_INSeconds(urgent) < SLAConfiguration.getAssignmentSLA_INSeconds(lessUrgent),
                    "assignment: " + urgent + " must be shorter than " + lessUrgent);
            assertTrue(SLAConfiguration.getResolutionSLA_INSeconds(urgent) < SLAConfiguration.getResolutionSLA_INSeconds(lessUrgent),
                    "resolution: " + urgent + " must be shorter than " + lessUrgent);
        }
    }

    @Test
    void resolvingMayTakeLongerThanAssigningForEveryPriority() {
        for (String priority : FROM_MOST_TO_LEAST_URGENT) {
            assertTrue(SLAConfiguration.getResolutionSLA_INSeconds(priority) > SLAConfiguration.getAssignmentSLA_INSeconds(priority),
                    priority + ": the resolution time must be longer than the assignment time");
        }
    }

    @Test
    void noPriorityIsBreachedTheMomentItIsCreated() {
        for (String priority : FROM_MOST_TO_LEAST_URGENT) {
            assertTrue(SLAConfiguration.getAssignmentSLA_INSeconds(priority) > 0, priority + " assignment");
            assertTrue(SLAConfiguration.getResolutionSLA_INSeconds(priority) > 0, priority + " resolution");
        }
    }

    @Test
    void everyPriorityHasAtLeastOneMinuteSoTheRiskTimerNeverDividesByZero() {
        // SLA_BreachingRiskSchedular works in whole minutes: elapsedMinutes / totalMinutes.
        for (String priority : FROM_MOST_TO_LEAST_URGENT) {
            assertTrue(SLAConfiguration.getAssignmentSLA_INSeconds(priority) >= 60, priority + " assignment");
            assertTrue(SLAConfiguration.getResolutionSLA_INSeconds(priority) >= 60, priority + " resolution");
        }
    }

    // ------------------------------------------------------------------ a priority we do not know

    @Test
    void anUnknownPriorityFailsLoudlyInsteadOfGettingASilentWrongTime() {
        // The old code answered "0 seconds" (resolution) or "1 hour" (assignment) here: that is how every CRITICAL
        // incident was "breached" at creation without anybody noticing.
        assertThrows(IllegalArgumentException.class, () -> SLAConfiguration.getResolutionSLA_INSeconds("URGENT"));
        assertThrows(IllegalArgumentException.class, () -> SLAConfiguration.getAssignmentSLA_INSeconds("URGENT"));
    }

    @Test
    void aMissingPriorityFailsWithTheSameClearError() {
        assertThrows(IllegalArgumentException.class, () -> SLAConfiguration.getResolutionSLA_INSeconds(null));
        assertThrows(IllegalArgumentException.class, () -> SLAConfiguration.getAssignmentSLA_INSeconds(null));
    }
}
