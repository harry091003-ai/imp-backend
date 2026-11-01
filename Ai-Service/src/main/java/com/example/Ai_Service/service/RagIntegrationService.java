package com.example.Ai_Service.service;


import com.example.Ai_Service.dto.IncidentResolvementSuggestionDTO;
import com.example.Ai_Service.dto.IncidentSuggestionNeededDTO;
import com.example.Ai_Service.dto.SimilarIncidentSourceDTO;
import com.example.Ai_Service.exception.AiUnavailableException;
import com.project.events.IncidentResolutionReportEvent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RagIntegrationService {
    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final SimilarIncidentSearch search;
    private final AiCallGuard guard;
    private final int topK;
    private final int minTextLength;
    private static final Logger log = LoggerFactory.getLogger(RagIntegrationService.class);
    private final PromptTemplate userPromptTemplate;
    private final SystemPromptTemplate systemPromptTemplate;

    public RagIntegrationService(VectorStore vectorStore, ChatClient chatClient, SimilarIncidentSearch search, AiCallGuard guard,
                                 @Value("classpath:/prompts/ragIntegrationPrompts/rag-userPrompt.st") Resource userPromptResource,
                                 @Value("classpath:/prompts/ragIntegrationPrompts/rag-systemPrompt.st") Resource systemPromptResource,
                                 @Value("${ai.search.suggestion-top-k:4}") int topK,
                                 @Value("${ai.memory.min-text-length:10}") int minTextLength) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.search = search;
        this.guard = guard;
        this.topK = topK;
        this.minTextLength = minTextLength;
        this.userPromptTemplate = PromptTemplate.builder().resource(userPromptResource).build();
        this.systemPromptTemplate = SystemPromptTemplate.builder().resource(systemPromptResource).build();
    }

    // AI plan phase 2 (2026-11-01). What the AI remembers about a resolved incident:
    //
    //   event ---- root cause or notes too short ("asd", "fixed") ----> skipped: junk in the memory gives junk advice
    //     |
    //   the PROBLEM (title + description) ---> Gemini embedding ---> this is what a search compares
    //   root cause, resolution, category ... ---> metadata          ---> this travels with it, and Gemini reads it later
    //
    // Why only the problem: a new incident is a problem with no solution yet. When the solution was in the embedded text
    // too, a long solution pushed a true match below the bar.
    public void storingResolvedIncidentsInVectorDb(IncidentResolutionReportEvent event){
        if (tooShort(event.getRootCause()) || tooShort(event.getResolutionNotes())) {
            log.info("Incident {} is not remembered: its root cause or resolution notes are under {} characters",
                    event.getIncidentId(), minTextLength);
            return;
        }

        Map<String, Object> metadata = new HashMap<>();
        // A value can be missing (an older message, an optional field), and a Document refuses a null value.
        putIfPresent(metadata, "incidentId", event.getIncidentId());
        putIfPresent(metadata, "title", event.getTitle());
        putIfPresent(metadata, "category", event.getCategory());
        putIfPresent(metadata, "service", event.getServiceName());
        putIfPresent(metadata, "severity", event.getSeverity());
        putIfPresent(metadata, "priority", event.getPriority());
        putIfPresent(metadata, "environment", event.getEnvironment());
        putIfPresent(metadata, "rootCause", event.getRootCause());
        putIfPresent(metadata, "resolutionNotes", event.getResolutionNotes());
        putIfPresent(metadata, "resolvedById", event.getResolvedBy());
        putIfPresent(metadata, "resolutionTimeInSeconds", event.getResolutionTimeInSeconds());
        putIfPresent(metadata, "assignmentSlaBreached", event.isAssignmentSlaBreached());
        putIfPresent(metadata, "resolutionSlaBreached", event.isResolutionSlaBreached());
        putIfPresent(metadata, "resolvedAt", event.getResolvedAt());

        // The document id is the INCIDENT id, on purpose (Track B2). Kafka can deliver the same event twice, and
        // the vector store does INSERT ... ON CONFLICT (id) DO UPDATE, so a repeat overwrites instead of adding a second copy.
        // An incident resolved again later (reopened, then solved differently) also overwrites, so the latest solution is kept.
        Document doc = new Document(event.getIncidentId().toString(), cleanText(event.getTitle() + "\n" + event.getDescription()), metadata);
        vectorStore.add(List.of(doc));
        log.info("Incident {} added to the AI memory", event.getIncidentId());
    }

    // AI plan phase 2: a reopened incident means its solution did not work, so it must not be suggested to anyone.
    // It comes back when the incident is resolved again.
    public void removeFromMemory(UUID incidentId){
        vectorStore.delete(List.of(incidentId.toString()));
        log.info("Incident {} removed from the AI memory, because it was reopened", incidentId);
    }

    private boolean tooShort(String text) {
        return text == null || text.trim().length() < minTextLength;
    }

    private static void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value instanceof LocalDateTime ? value.toString() : value);
        }
    }

    private static String cleanText(String text) {
        return text
                .replaceAll("[ \\t]+", " ")   // clean spaces/tabs
                .replaceAll("\\n+", "\n")     // keep single newline
                .trim();
    }
//----------------------------------------

    // What Gemini must answer. Only these two fields: the status and the sources are facts, and they come from our code.
    private record LlmAnswer(String rootCause, String resolutionNotes) {
    }

    // AI plan phase 1: RAG by hand, so the search runs ONCE. It used to run by hand to choose the filter, and then AGAIN
    // inside RetrievalAugmentationAdvisor: 2 paid embedding calls, and the 2 searches could disagree.
    //
    //   search similar incidents ---- none ----> NOT_ENOUGH_SIMILAR_INCIDENTS, Gemini is never called
    //        | found
    //   put them in the prompt ---> Gemini ---> root cause + resolution + the sources it was based on
    public IncidentResolvementSuggestionDTO generateSuggestionForNewIncident(IncidentSuggestionNeededDTO  request){
        log.info("Suggestion requested for category={}, service={}", request.getCategory(), request.getServiceName());

        SimilarIncidentSearch.Result found = search.search(request.getTitle(), request.getDescription(),
                request.getCategory(), request.getServiceName(), topK);
        if (found.documents().isEmpty()) {
            log.info("No similar incidents found, so Gemini is not called: without context it could only guess");
            return IncidentResolvementSuggestionDTO.builder()
                    .status(IncidentResolvementSuggestionDTO.Status.NOT_ENOUGH_SIMILAR_INCIDENTS)
                    .sources(List.of())
                    .build();
        }

        Message userMessage=userPromptTemplate.createMessage(Map.of(
                "title",request.getTitle(),
                "description",request.getDescription(),
                "context",contextFrom(found.documents())));
        Message systemMessage=systemPromptTemplate.createMessage();
        Prompt prompt=new Prompt(systemMessage,userMessage);

        LlmAnswer answer = guard.call("suggestion from Gemini",
                () -> chatClient.prompt(prompt).call().entity(LlmAnswer.class));
        if (answer == null) {
            throw new AiUnavailableException("AI_UNAVAILABLE", "The AI is not available right now. Please try again later.",
                    HttpStatus.SERVICE_UNAVAILABLE, null);
        }

        return IncidentResolvementSuggestionDTO.builder()
                .status(IncidentResolvementSuggestionDTO.Status.OK)
                .rootCause(answer.rootCause())
                .resolutionNotes(answer.resolutionNotes())
                .sameCategoryAndService(found.sameCategoryAndService())
                .sources(found.documents().stream().map(RagIntegrationService::sourceFrom).toList())
                .build();
    }

    private static String contextFrom(List<Document> documents) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            context.append("Past incident ").append(i + 1).append(":\n").append(document.getText()).append("\n");
            if (textOf(document, "rootCause") != null) {
                context.append("Root cause: ").append(textOf(document, "rootCause")).append("\n");
            }
            if (textOf(document, "resolutionNotes") != null) {
                context.append("Resolution: ").append(textOf(document, "resolutionNotes")).append("\n");
            }
            context.append("\n");
        }
        return context.toString().trim();
    }

    private static SimilarIncidentSourceDTO sourceFrom(Document document) {
        return SimilarIncidentSourceDTO.builder()
                .incidentId(document.getId())
                .title(textOf(document, "title"))
                .similarity(document.getScore() == null ? 0 : document.getScore())
                .resolvedBy(textOf(document, "resolvedById"))
                .resolvedAt(textOf(document, "resolvedAt"))
                .build();
    }

    private static String textOf(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? null : value.toString();
    }

}
