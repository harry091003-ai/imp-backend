package com.example.Ai_Service.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI plan phase 1 (2026-11-01): the ranking score, plain Java.
 * The old similarity part stopped at 0.35 with the 4 matches it could ever see, so a 35% weight was really about 12%.
 */
class EngineerRankingTest {

    private final EngineerRanking ranking = new EngineerRanking(0.35, 0.35, 0.20, 0.10);

    @Test
    void everySignalCanReachOne() {
        assertEquals(1.0, EngineerRanking.similarity(1.0, 3), 0.0001);
        assertEquals(1.0, EngineerRanking.freeTime(0), 0.0001);
    }

    @Test
    void thePerfectEngineerScoresOneHundred() {
        assertEquals(100, ranking.score(1, 1, 1, 1), 0.0001);
    }

    @Test
    void theWeightsMeanWhatTheySay() {
        assertEquals(35, ranking.score(1, 0, 0, 0), 0.0001);
        assertEquals(35, ranking.score(0, 1, 0, 0), 0.0001);
        assertEquals(20, ranking.score(0, 0, 1, 0), 0.0001);
        assertEquals(10, ranking.score(0, 0, 0, 1), 0.0001);
    }

    @Test
    void noSimilarCaseMeansNoSimilarityPoints() {
        assertEquals(0, EngineerRanking.similarity(0.9, 0), 0.0001);
    }

    @Test
    void aCloserMatchCountsMoreThanAnotherWeakOne() {
        assertTrue(EngineerRanking.similarity(0.95, 1) > EngineerRanking.similarity(0.65, 2));
    }

    @Test
    void moreMatchesHelpButStopAtThree() {
        assertTrue(EngineerRanking.similarity(0.8, 2) > EngineerRanking.similarity(0.8, 1));
        assertEquals(EngineerRanking.similarity(0.8, 3), EngineerRanking.similarity(0.8, 9), 0.0001);
    }

    @Test
    void tenOrMoreActiveIncidentsMeansNoFreeTime() {
        assertEquals(0, EngineerRanking.freeTime(10), 0.0001);
        assertEquals(0, EngineerRanking.freeTime(25), 0.0001);
        assertEquals(0.5, EngineerRanking.freeTime(5), 0.0001);
    }
}
