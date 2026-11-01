package com.example.Ai_Service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// AI plan phase 1: the ranking score, with no Spring, database or AI inside, so it is easy to test.
// Every input is 0 to 1 and each one can really reach 1, so the weights mean what they say. (The old similarity part was
// log10(count + 1) / 2. With at most 4 matches it stopped at 0.35, so a 35% weight was really about 12%.)
@Component
public class EngineerRanking {

    // One good match counts most. Up to 3 matches show it was not a one-off.
    private static final int MATCHES_FOR_FULL_EXPERIENCE = 3;

    private final double similarityWeight;
    private final double expertiseWeight;
    private final double slaWeight;
    private final double freeTimeWeight;

    public EngineerRanking(@Value("${ai.ranking.weight.similarity:0.35}") double similarityWeight,
                           @Value("${ai.ranking.weight.expertise:0.35}") double expertiseWeight,
                           @Value("${ai.ranking.weight.sla:0.20}") double slaWeight,
                           @Value("${ai.ranking.weight.free-time:0.10}") double freeTimeWeight) {
        this.similarityWeight = similarityWeight;
        this.expertiseWeight = expertiseWeight;
        this.slaWeight = slaWeight;
        this.freeTimeWeight = freeTimeWeight;
    }

    /** How strongly this engineer's past cases match. 0 = no similar case. */
    public static double similarity(double bestMatch, int matches) {
        if (matches == 0) {
            return 0;
        }
        double experience = Math.min(matches, MATCHES_FOR_FULL_EXPERIENCE) / (double) MATCHES_FOR_FULL_EXPERIENCE;
        return bestMatch * 0.7 + experience * 0.3;
    }

    /** 0 active incidents = 1 (completely free). 10 or more = 0. */
    public static double freeTime(int activeIncidents) {
        return 1 - Math.min(activeIncidents / 10.0, 1.0);
    }

    /** 0 to 100. */
    public double score(double similarity, double expertise, double sla, double freeTime) {
        return (similarity * similarityWeight + expertise * expertiseWeight + sla * slaWeight + freeTime * freeTimeWeight) * 100;
    }
}
