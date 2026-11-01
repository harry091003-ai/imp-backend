package com.example.Ai_Service.service;

import com.example.Ai_Service.dto.ClassificationResultDTO;
import com.example.Ai_Service.dto.ClassifyIncidentRequestDTO;
import com.example.Ai_Service.dto.IncidentValues;
import com.example.Ai_Service.entity.AiInsight;
import com.example.Ai_Service.entity.ClassificationSuggestion;
import com.example.Ai_Service.exception.AiUnavailableException;
import com.example.Ai_Service.repository.AiInsightRepository;
import com.example.Ai_Service.repository.ClassificationSuggestionRepository;
import com.project.events.IncidentCreatedEvent;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

// AI plan phase 3 (2026-11-01). The screen asks while the user fills the form, and the USER decides.
//
//   POST /ai/classify ---> same text asked recently? ---> yes: the saved answer, no paid call
//        | no
//   Gemini ---> check every value ---> save it (key = hash of the text) ---> show it to the user
//
//   later: incident.created ---> a saved suggestion for this text? ---> compare it with what the user really chose
//                                                                       ---> one ai_insight row. Gemini is NOT called
//
// How often the AI agrees: SELECT conflict, COUNT(*) FROM ai_insight GROUP BY conflict;
//
// It replaces the old classifier, which ran after the incident was created, on a paid call for every incident, with the
// call inside a database transaction, and whose result nobody read.
@Service
public class ClassificationService {

    private static final Logger log = LoggerFactory.getLogger(ClassificationService.class);
    private static final int MAX_SUMMARY_LENGTH = 2000;   // the column's size

    // What Gemini must answer. Nothing here is trusted until it has been checked.
    private record LlmClassification(String category, String severity, String priority, String serviceName,
                                     String summary, Double confidence) {
    }

    private final ChatClient chatClient;
    private final AiCallGuard guard;
    private final ClassificationSuggestionRepository suggestionRepository;
    private final AiInsightRepository insightRepository;
    private final PromptTemplate userPromptTemplate;
    private final SystemPromptTemplate systemPromptTemplate;
    private final double minConfidence;
    private final long keepHours;

    public ClassificationService(ChatClient chatClient, AiCallGuard guard,
                                 ClassificationSuggestionRepository suggestionRepository, AiInsightRepository insightRepository,
                                 @Value("classpath:/prompts/classificationPrompts/classification-userPrompt.st") Resource userPromptResource,
                                 @Value("classpath:/prompts/classificationPrompts/classification-systemPrompt.st") Resource systemPromptResource,
                                 @Value("${ai.classify.min-confidence:0.35}") double minConfidence,
                                 @Value("${ai.classify.keep-hours:24}") long keepHours) {
        this.chatClient = chatClient;
        this.guard = guard;
        this.suggestionRepository = suggestionRepository;
        this.insightRepository = insightRepository;
        this.userPromptTemplate = PromptTemplate.builder().resource(userPromptResource).build();
        this.systemPromptTemplate = SystemPromptTemplate.builder().resource(systemPromptResource).build();
        this.minConfidence = minConfidence;
        this.keepHours = keepHours;
    }

    public ClassificationResultDTO classify(ClassifyIncidentRequestDTO request) {
        // Suggestions nobody turned into an incident: this table stays small with no timer of its own.
        suggestionRepository.deleteOlderThan(LocalDateTime.now().minusHours(keepHours));

        String textHash = hashOf(request.getTitle(), request.getDescription());
        return suggestionRepository.findById(textHash).map(this::toDto).orElseGet(() -> {
            ClassificationSuggestion suggestion = checked(askGemini(request), textHash);
            // Saved after the call, not around it: never hold a database transaction while waiting for Gemini.
            suggestionRepository.save(suggestion);
            return toDto(suggestion);
        });
    }

    // No Gemini call here. If the user changed the text after asking, the hash differs and this incident is simply not measured.
    @Transactional
    public void compareWithWhatWasSubmitted(IncidentCreatedEvent event) {
        if (event.getTitle() == null || event.getDescription() == null) {
            return;
        }
        String textHash = hashOf(event.getTitle(), event.getDescription());
        suggestionRepository.findById(textHash).ifPresent(suggestion -> {
            boolean conflict = !Objects.equals(suggestion.getAiCategory(), event.getCategory())
                    || !Objects.equals(suggestion.getAiSeverity(), event.getSeverity())
                    || !Objects.equals(suggestion.getAiPriority(), event.getPriority())
                    || !Objects.equals(suggestion.getAiServiceName(), event.getServiceName());
            insightRepository.save(AiInsight.builder()
                    .incidentId(event.getIncidentId())
                    .aiCategory(suggestion.getAiCategory())
                    .aiSeverity(suggestion.getAiSeverity())
                    .aiPriority(suggestion.getAiPriority())
                    .aiServiceName(suggestion.getAiServiceName())
                    .summary(suggestion.getSummary())
                    .confidence(suggestion.getConfidence())
                    .conflict(conflict)
                    .build());
            suggestionRepository.delete(suggestion);
            log.info("Incident {}: the user {} the AI's suggestion", event.getIncidentId(), conflict ? "changed" : "kept");
        });
    }

    private LlmClassification askGemini(ClassifyIncidentRequestDTO request) {
        Message systemMessage = systemPromptTemplate.createMessage();
        Message userMessage = userPromptTemplate.createMessage(Map.of("title", request.getTitle(), "description", request.getDescription()));
        Prompt prompt = new Prompt(systemMessage, userMessage);

        LlmClassification answer = guard.call("classification from Gemini",
                () -> chatClient.prompt(prompt).call().entity(LlmClassification.class));
        if (answer == null) {
            throw new AiUnavailableException("AI_UNAVAILABLE", "The AI is not available right now. Please try again later.",
                    HttpStatus.SERVICE_UNAVAILABLE, null);
        }
        return answer;
    }

    // A model can answer anything. A value that does not exist would fill the form with something IncidentService refuses.
    private ClassificationSuggestion checked(LlmClassification answer, String textHash) {
        double confidence = answer.confidence() == null ? 0 : Math.max(0, Math.min(1, answer.confidence()));
        boolean confident = confidence >= minConfidence;

        String summary = answer.summary();
        if (summary != null && summary.length() > MAX_SUMMARY_LENGTH) {
            summary = summary.substring(0, MAX_SUMMARY_LENGTH);
        }
        return ClassificationSuggestion.builder()
                .textHash(textHash)
                // Too unsure: the safe "don't know" values, as the old classifier did.
                .aiCategory(confident && isOneOf(IncidentValues.CATEGORY, answer.category()) ? answer.category() : "OTHER")
                .aiServiceName(confident && isOneOf(IncidentValues.SERVICE, answer.serviceName()) ? answer.serviceName() : "OTHERS")
                // These two have no "don't know" value, so an impossible one is left empty for the user to choose.
                .aiSeverity(isOneOf(IncidentValues.SEVERITY, answer.severity()) ? answer.severity() : null)
                .aiPriority(isOneOf(IncidentValues.PRIORITY, answer.priority()) ? answer.priority() : null)
                .summary(summary)
                .confidence(confidence)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private static boolean isOneOf(String allowedValues, String value) {
        return value != null && value.matches(allowedValues);
    }

    private ClassificationResultDTO toDto(ClassificationSuggestion suggestion) {
        return ClassificationResultDTO.builder()
                .category(suggestion.getAiCategory())
                .severity(suggestion.getAiSeverity())
                .priority(suggestion.getAiPriority())
                .serviceName(suggestion.getAiServiceName())
                .summary(suggestion.getSummary())
                .confidence(suggestion.getConfidence())
                .confident(suggestion.getConfidence() >= minConfidence)
                .build();
    }

    private static String hashOf(String title, String description) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((title.trim() + "\n" + description.trim()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java", e);   // cannot happen
        }
    }
}
