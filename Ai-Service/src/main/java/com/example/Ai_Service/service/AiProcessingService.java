package com.example.Ai_Service.service;

import com.example.Ai_Service.Kafka.producer.AiResponseProducer;
import com.example.Ai_Service.dto.ClassificationResultDTO;
import com.example.Ai_Service.entity.AiInsight;
import com.example.Ai_Service.repository.AiInsightRepository;
import com.project.events.IncidentCreatedEvent;
import com.project.events.AiProcessedEvent;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@Transactional
@RequiredArgsConstructor
public class AiProcessingService {

    private final ClassificationService classificationService;
    private final AiInsightRepository aiInsightRepository;
    private final AiResponseProducer aiResponseProducer;

    private static final Logger log = LoggerFactory.getLogger(AiProcessingService.class);
// gives the suggestion for the classification of the incident...
    public void processIncidents(IncidentCreatedEvent event) {

        // ==============================
        // 1. IDEMPOTENCY CHECK
        // ==============================

        boolean alreadyProcessed = aiInsightRepository.existsById(event.getIncidentId());
        if (alreadyProcessed) {
            log.info("Skipping already processed incidentId={}", event.getIncidentId());
            return;
        }

        // ==============================
        // 2. CALL AI MODEL
        // ==============================
        ClassificationResultDTO aiResult = classificationService.classifyIncident(event);

        log.info("AI Raw Result for incidentId={} → {}", event.getIncidentId(), aiResult);

        // ==============================
        // 3. BASIC VALIDATION
        // ==============================
        if (aiResult == null || aiResult.getCategory() == null) {
            log.warn("Invalid AI response for incidentId={}", event.getIncidentId());
            return;
        }

        // ==============================
        // 4. CONFLICT DETECTION (USER vs AI)
        // ==============================
        boolean categoryConflict = isConflict(event.getCategory(), aiResult.getCategory());
        boolean severityConflict = isConflict(event.getSeverity(), aiResult.getSeverity());
        boolean serviceConflict = isConflict(event.getServiceName(), aiResult.getServiceName());

        boolean conflict = categoryConflict || severityConflict || serviceConflict;

        log.info("Conflict → category={}, severity={}, service={}",
                categoryConflict, severityConflict, serviceConflict);

        // ==============================
        // 5. CONFIDENCE CALCULATION (AI + SYSTEM MATCH)
        // ==============================
        int matched = 0;
        int compared = 0;

        // CATEGORY
        if (event.getCategory() != null && aiResult.getCategory() != null) {
            compared++;
            if (event.getCategory().equalsIgnoreCase(aiResult.getCategory())) matched++;
        }

        // SEVERITY
        if (event.getSeverity() != null && aiResult.getSeverity() != null) {
            compared++;
            if (event.getSeverity().equalsIgnoreCase(aiResult.getSeverity())) matched++;
        }

        // SERVICE
        if (event.getServiceName() != null && aiResult.getServiceName() != null) {
            compared++;
            if (event.getServiceName().equalsIgnoreCase(aiResult.getServiceName())) matched++;
        }

        double mathConfidence = (compared == 0) ? 0 : (double) matched / compared;

        double finalConfidence;
        if (compared == 0) {
            // No ground truth → reduce blind trust
            finalConfidence = aiResult.getConfidence() * 0.5;
        } else {
            // Combine AI + actual match
            finalConfidence = (mathConfidence * 0.7 + aiResult.getConfidence() * 0.3);
        }

        log.info("Confidence → ai={}, math={}, combined={}",
                aiResult.getConfidence(), mathConfidence, finalConfidence);

        // ==============================
        // 6. KEYWORD SIGNAL BOOST (NON-DESTRUCTIVE)
        // ==============================
        double boost = keywordBoost(event.getDescription(), aiResult.getCategory());

        finalConfidence = Math.min(1.0, finalConfidence + boost);

        log.info("Keyword boost applied={}, finalConfidence={}", boost, finalConfidence);

        // ==============================
        // 7. LOW CONFIDENCE FALLBACK (VALIDATION LAYER)
        // ==============================
        if (finalConfidence < 0.35) {
            log.warn("Low confidence detected → forcing safe fallback");

            aiResult.setCategory("OTHER");
            aiResult.setServiceName("OTHERS");
        }

        // ==============================
        // 8. SAVE AI INSIGHT
        // ==============================
        AiInsight insight = AiInsight.builder()
                .incidentId(event.getIncidentId())
                .aiCategory(aiResult.getCategory())
                .aiSeverity(aiResult.getSeverity())
                .aiPriority(aiResult.getPriority())
                .aiServiceName(aiResult.getServiceName())
                .summary(aiResult.getSummary())
                .confidence(finalConfidence)
                .conflict(conflict)
                .build();

        aiInsightRepository.save(insight);

        log.info("Saved AI Insight → {}", insight);

        // ==============================
        // 9. PUBLISH AI RESULT EVENT
        // ==============================
        AiProcessedEvent processedEvent = AiProcessedEvent.builder()
                .incidentId(event.getIncidentId())
                .aiCategory(aiResult.getCategory())
                .aiSeverity(aiResult.getSeverity())
                .aiPriority(aiResult.getPriority())
                .aiServiceName(aiResult.getServiceName())
                .confidence(finalConfidence)
                .aiSummary(aiResult.getSummary())
                .conflict(conflict)
                .build();

        aiResponseProducer.publishClassificationResult(processedEvent);

        log.info("Published AiProcessedEvent → {}", processedEvent);
    }

    // ==============================
    // HELPER: CONFLICT CHECK
    // ==============================
    private boolean isConflict(String eventValue, String aiValue) {
        return eventValue != null &&
                aiValue != null &&
                !eventValue.equalsIgnoreCase(aiValue);
    }

    // ==============================
    // HELPER: KEYWORD BOOST (SOFT SIGNAL)
    // ==============================
    private double keywordBoost(String text, String category) {

        if (text == null || category == null) return 0.0;

        text = text.toLowerCase();

        switch (category) {

            case "DATABASE":
                if (text.contains("db") || text.contains("database") || text.contains("query") || text.contains("timeout")) {
                    return 0.10;
                }
                break;

            case "NETWORK":
                if (text.contains("network") || text.contains("unreachable") || text.contains("latency")) {
                    return 0.10;
                }
                break;

            case "SAFETY":
                if (text.contains("temperature") || text.contains("overheating")) {
                    return 0.15;
                }
                break;

            case "INFRASTRUCTURE":
                if (text.contains("kubernetes") || text.contains("pod") || text.contains("restart")) {
                    return 0.10;
                }
                break;
        }

        return 0.0;
    }


}