package com.example.Ai_Service.service;

import com.example.Ai_Service.dto.EngineerCandidateDTO;
import com.example.Ai_Service.dto.EngineerRecommendationRequestDTO;
import com.example.Ai_Service.dto.EngineerSummaryDTO;
import com.example.Ai_Service.dto.EngineerSummaryRequestDTO;
import com.example.Ai_Service.dto.UserValidationDTO;
import com.example.Ai_Service.exception.AiUnavailableException;
import com.example.Ai_Service.feignClients.ReportingServiceFeignClient;
import com.example.Ai_Service.feignClients.UserServiceFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// AI plan phase 1 (2026-11-01). Who should get this incident?
//
//   a supervisor's team (admin: everyone)
//        |
//   engineers who solved similar incidents, inside that team
//        |  fewer than 3 found: the rest of the team joins, with no similarity points
//   only ACTIVE users with the ENGINEER role
//        |
//   their numbers from ReportingService (if it is down: neutral numbers and statsAvailable=false)
//        |
//   sorted by score, best first
//
// A supervisor may only assign to their own team (issue 56), so anyone else in this list would be a useless answer.
@Service
@Slf4j
public class EngineerAssignmentRecommendationService {

    // Neutral = "we do not know", not "bad". A new engineer must not lose points for having no history.
    private static final double NEUTRAL = 0.5;
    private static final int MAX_IDS_PER_CALL = 50;   // the limit of both batch endpoints

    private final SimilarIncidentSearch search;
    private final ReportingServiceFeignClient reportingServiceFeignClient;
    private final UserServiceFeignClient userServiceFeignClient;
    private final EngineerRanking ranking;
    private final int topK;
    private final int minCandidates;
    private final int maxResults;

    public EngineerAssignmentRecommendationService(SimilarIncidentSearch search,
                                                   ReportingServiceFeignClient reportingServiceFeignClient,
                                                   UserServiceFeignClient userServiceFeignClient,
                                                   EngineerRanking ranking,
                                                   @Value("${ai.search.recommendation-top-k:10}") int topK,
                                                   @Value("${ai.ranking.min-candidates:3}") int minCandidates,
                                                   @Value("${ai.ranking.max-results:5}") int maxResults) {
        this.search = search;
        this.reportingServiceFeignClient = reportingServiceFeignClient;
        this.userServiceFeignClient = userServiceFeignClient;
        this.ranking = ranking;
        this.topK = topK;
        this.minCandidates = minCandidates;
        this.maxResults = maxResults;
    }

    public List<EngineerCandidateDTO> recommendedEngineers(EngineerRecommendationRequestDTO request, Authentication auth) {
        log.debug("Recommending engineers for category {}, service {}", request.getCategory(), request.getServiceName());

        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        List<UUID> team = isAdmin ? List.of() : teamOf(UUID.fromString(auth.getName()));
        if (!isAdmin && team.isEmpty()) {
            return List.of();   // nobody they could assign: no reason to pay for a search
        }

        SimilarIncidentSearch.Result found = search.search(request.getTitle(), request.getDescription(),
                request.getCategory(), request.getServiceName(), topK);
        Map<UUID, EngineerCandidateDTO> candidates = candidatesFrom(found.documents());

        if (!isAdmin) {
            candidates.keySet().retainAll(team);
            if (candidates.size() < minCandidates) {
                team.stream().limit(MAX_IDS_PER_CALL).forEach(engineerId -> candidates.computeIfAbsent(engineerId, this::noSimilarCase));
            }
        }
        if (candidates.isEmpty()) {
            return List.of();
        }

        keepOnlyAssignableEngineers(candidates);
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<UUID, EngineerSummaryDTO> summaries = summariesOf(candidates, request);
        boolean statsAvailable = summaries != null;
        candidates.values().forEach(candidate ->
                score(candidate, statsAvailable ? summaries.get(candidate.getEngineerId()) : null, statsAvailable));

        return candidates.values().stream()
                .sorted(Comparator.comparingDouble(EngineerCandidateDTO::getFinalRankingScore).reversed())
                .limit(maxResults)
                .toList();
    }

    private List<UUID> teamOf(UUID supervisorId) {
        try {
            return userServiceFeignClient.getEngineersUnderSupervisor(supervisorId);
        } catch (RuntimeException e) {
            throw userServiceDown(e);
        }
    }

    // Old documents may name a supervisor or someone who has left, so the list is always checked.
    private void keepOnlyAssignableEngineers(Map<UUID, EngineerCandidateDTO> candidates) {
        List<UserValidationDTO> users;
        try {
            users = userServiceFeignClient.validateUsers(new ArrayList<>(candidates.keySet()));
        } catch (RuntimeException e) {
            throw userServiceDown(e);
        }
        List<UUID> assignable = users.stream()
                .filter(user -> "ACTIVE".equals(user.getStatus()) && user.getRoles() != null && user.getRoles().contains("ROLE_ENGINEER"))
                .map(UserValidationDTO::getUserid)
                .toList();
        candidates.keySet().retainAll(assignable);
    }

    // We cannot tell who may be assigned without UserService. Guessing could show the wrong people, so this one fails.
    private static AiUnavailableException userServiceDown(RuntimeException cause) {
        log.warn("UserService call failed: {}", cause.getClass().getSimpleName());
        return new AiUnavailableException("USER_SERVICE_UNAVAILABLE", "Could not check the engineers right now. Please try again later.",
                HttpStatus.SERVICE_UNAVAILABLE, cause);
    }

    // Null = ReportingService did not answer. The recommendation still works, without its numbers.
    private Map<UUID, EngineerSummaryDTO> summariesOf(Map<UUID, EngineerCandidateDTO> candidates, EngineerRecommendationRequestDTO request) {
        try {
            return reportingServiceFeignClient.getEngineerSummaries(EngineerSummaryRequestDTO.builder()
                            .category(request.getCategory())
                            .serviceName(request.getServiceName())
                            .engineerIds(new ArrayList<>(candidates.keySet()))
                            .build())
                    .stream().collect(Collectors.toMap(EngineerSummaryDTO::getEngineerId, Function.identity()));
        } catch (RuntimeException e) {
            log.warn("ReportingService call failed, ranking without its numbers: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private void score(EngineerCandidateDTO candidate, EngineerSummaryDTO summary, boolean statsAvailable) {
        candidate.setStatsAvailable(statsAvailable);
        boolean hasHistory = summary != null && summary.getTotalIncidentsResolved() > 0;
        double expertise = NEUTRAL;
        double sla = NEUTRAL;
        double freeTime = NEUTRAL;
        if (statsAvailable) {
            // No history in this kind of incident: no expertise points, which is true. But an unknown SLA rate is not a bad one.
            expertise = hasHistory ? summary.getExpertiseScore() / 100.0 : 0;
            sla = hasHistory ? summary.getSlaSuccessRate() : NEUTRAL;
            int active = summary == null ? 0 : summary.getActiveIncidentCount();
            freeTime = EngineerRanking.freeTime(active);
            candidate.setActiveIncidents(active);
            candidate.setExpertiseScore(hasHistory ? summary.getExpertiseScore() : 0);
            candidate.setSlaSuccessRate(hasHistory ? summary.getSlaSuccessRate() : 0);
        }
        double similarity = EngineerRanking.similarity(candidate.getBestSimilarity(), candidate.getSimilarIncidentCount());
        candidate.setFinalRankingScore(ranking.score(similarity, expertise, sla, freeTime));
    }

    private EngineerCandidateDTO noSimilarCase(UUID engineerId) {
        return EngineerCandidateDTO.builder().engineerId(engineerId).similarIncidentCount(0).build();
    }

    private Map<UUID, EngineerCandidateDTO> candidatesFrom(List<Document> documents) {
        Map<UUID, EngineerCandidateDTO> candidates = new LinkedHashMap<>();
        for (Document document : documents) {
            Object resolvedBy = document.getMetadata().get("resolvedById");
            if (resolvedBy == null) {
                continue;
            }
            EngineerCandidateDTO candidate = candidates.computeIfAbsent(UUID.fromString(resolvedBy.toString()), this::noSimilarCase);
            candidate.setSimilarIncidentCount(candidate.getSimilarIncidentCount() + 1);
            double match = document.getScore() == null ? 0 : document.getScore();
            candidate.setBestSimilarity(Math.max(candidate.getBestSimilarity(), match));
        }
        return new HashMap<>(candidates);
    }
}
