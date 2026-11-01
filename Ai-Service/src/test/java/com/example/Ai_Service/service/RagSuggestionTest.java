package com.example.Ai_Service.service;

import com.example.Ai_Service.FakeEmbeddingModel;
import com.example.Ai_Service.dto.IncidentResolvementSuggestionDTO;
import com.example.Ai_Service.dto.IncidentSuggestionNeededDTO;
import com.example.Ai_Service.exception.AiUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI plan phase 1 (2026-11-01): POST /ai/rag/suggestion on the REAL code and the REAL prompt files.
 * A fake embedding model, an in-memory vector store and a fake Gemini: no network, no cost.
 */
class RagSuggestionTest {

    private static final String GOOD_ANSWER = "{\"rootCause\":\"pool too small\",\"resolutionNotes\":\"raise the pool\"}";

    private final FakeEmbeddingModel embedding = new FakeEmbeddingModel();
    private SimpleVectorStore store;
    private RagIntegrationService service;

    private final AtomicInteger geminiCalls = new AtomicInteger();
    private final AtomicReference<String> promptSent = new AtomicReference<>();
    private volatile String geminiSays = GOOD_ANSWER;
    private volatile RuntimeException geminiThrows;

    private final ChatModel fakeGemini = new ChatModel() {
        @Override
        public ChatResponse call(Prompt prompt) {
            geminiCalls.incrementAndGet();
            promptSent.set(prompt.getContents());
            if (geminiThrows != null) {
                throw geminiThrows;
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage(geminiSays))));
        }
    };

    @BeforeEach
    void setUp() {
        store = SimpleVectorStore.builder(embedding).build();
        AiCallGuard guard = new AiCallGuard(5);
        service = new RagIntegrationService(store, ChatClient.builder(fakeGemini).build(),
                new SimilarIncidentSearch(store, guard, 0.6), guard,
                new ClassPathResource("prompts/ragIntegrationPrompts/rag-userPrompt.st"),
                new ClassPathResource("prompts/ragIntegrationPrompts/rag-systemPrompt.st"),
                4, 10);
    }

    private String pastIncident(String text, String category, String serviceName) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("category", category);
        metadata.put("service", serviceName);
        metadata.put("resolvedById", "engineer-1");
        metadata.put("resolvedAt", "2026-10-01T10:00");
        store.add(List.of(new Document(id, text, metadata)));
        embedding.calls.set(0);
        return id;
    }

    private static IncidentSuggestionNeededDTO request(String title, String description, String category, String serviceName) {
        IncidentSuggestionNeededDTO request = new IncidentSuggestionNeededDTO();
        request.setTitle(title);
        request.setDescription(description);
        request.setCategory(category);
        request.setServiceName(serviceName);
        return request;
    }

    // ------------------------------------------------------------------ the normal path

    @Test
    void aSimilarIncidentGivesASuggestionWithItsSource() {
        String id = pastIncident("database timeout payment", "DATABASE", "PAYMENT_SERVICE");

        IncidentResolvementSuggestionDTO answer = service.generateSuggestionForNewIncident(
                request("database timeout", "payment", "DATABASE", "PAYMENT_SERVICE"));

        assertEquals(IncidentResolvementSuggestionDTO.Status.OK, answer.getStatus());
        assertEquals("pool too small", answer.getRootCause());
        assertEquals("raise the pool", answer.getResolutionNotes());
        assertTrue(answer.isSameCategoryAndService());
        assertEquals(1, answer.getSources().size());
        assertEquals(id, answer.getSources().get(0).getIncidentId());
        assertEquals("engineer-1", answer.getSources().get(0).getResolvedBy());
        assertEquals("2026-10-01T10:00", answer.getSources().get(0).getResolvedAt());
        assertTrue(answer.getSources().get(0).getSimilarity() > 0.9, "the same words: nearly 1");
    }

    @Test
    void geminiReceivesThePastIncidentsAndTheNewOne() {
        pastIncident("database timeout payment", "DATABASE", "PAYMENT_SERVICE");

        service.generateSuggestionForNewIncident(request("database timeout", "payment", null, null));

        assertTrue(promptSent.get().contains("Past incident 1:"), promptSent.get());
        assertTrue(promptSent.get().contains("database timeout payment"), promptSent.get());
        assertTrue(promptSent.get().contains("Title:database timeout"), promptSent.get());
    }

    @Test
    void theSearchRunsOnlyOnce() {
        pastIncident("database timeout payment", "DATABASE", "PAYMENT_SERVICE");

        service.generateSuggestionForNewIncident(request("database timeout", "payment", "DATABASE", "PAYMENT_SERVICE"));

        assertEquals(1, embedding.calls.get(), "one paid embedding call, it used to be two");
        assertEquals(1, geminiCalls.get());
    }

    @Test
    void textWithBracesDoesNotBreakThePrompt() {
        // {} is what a prompt template uses for its own variables, and users paste log lines and JSON.
        pastIncident("database timeout {host} payment", "DATABASE", "PAYMENT_SERVICE");

        IncidentResolvementSuggestionDTO answer = service.generateSuggestionForNewIncident(
                request("database timeout {host}", "payment {\"a\":1}", null, null));

        assertEquals(IncidentResolvementSuggestionDTO.Status.OK, answer.getStatus());
    }

    // ------------------------------------------------------------------ nothing similar

    @Test
    void nothingSimilarMeansGeminiIsNotCalled() {
        pastIncident("printer paper jam office", "HARDWARE", "OTHERS");

        IncidentResolvementSuggestionDTO answer = service.generateSuggestionForNewIncident(
                request("database timeout", "payment", null, null));

        assertEquals(IncidentResolvementSuggestionDTO.Status.NOT_ENOUGH_SIMILAR_INCIDENTS, answer.getStatus());
        assertNull(answer.getRootCause());
        assertEquals(List.of(), answer.getSources());
        assertEquals(0, geminiCalls.get(), "no context means it could only guess, so no paid call");
    }

    // ------------------------------------------------------------------ the fallback

    @Test
    void whenTheFilterFindsNothingTheSearchLooksEverywhereWithTheSameBar() {
        // 3 shared words out of 3 and 7 = a similarity of about 0.65: above the 0.6 bar, below the old fallback bar of 0.7.
        pastIncident("database timeout payment refund invoice ledger batch", "DATABASE", "PAYMENT_SERVICE");

        IncidentResolvementSuggestionDTO answer = service.generateSuggestionForNewIncident(
                request("database timeout", "payment", "NETWORK", "AUTH_SERVICE"));

        assertEquals(IncidentResolvementSuggestionDTO.Status.OK, answer.getStatus());
        assertFalse(answer.isSameCategoryAndService(), "the answer says it had to look further");
    }

    @Test
    void aMatchInTheSameCategoryWinsOverABetterMatchElsewhere() {
        String same = pastIncident("database timeout payment refund invoice", "DATABASE", "PAYMENT_SERVICE");
        pastIncident("database timeout payment", "NETWORK", "AUTH_SERVICE");

        IncidentResolvementSuggestionDTO answer = service.generateSuggestionForNewIncident(
                request("database timeout", "payment", "DATABASE", "PAYMENT_SERVICE"));

        assertEquals(1, answer.getSources().size());
        assertEquals(same, answer.getSources().get(0).getIncidentId());
        assertTrue(answer.isSameCategoryAndService());
    }

    // ------------------------------------------------------------------ Gemini failures

    @Test
    void aGeminiErrorIsA503() {
        pastIncident("database timeout payment", "DATABASE", "PAYMENT_SERVICE");
        geminiThrows = new RuntimeException("429 quota exceeded");

        AiUnavailableException error = assertThrows(AiUnavailableException.class, () ->
                service.generateSuggestionForNewIncident(request("database timeout", "payment", null, null)));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertEquals("AI_UNAVAILABLE", error.getErrorCode());
        assertFalse(error.getMessage().contains("quota"), "the details stay in the log");
    }

    @Test
    void anAnswerThatIsNotJsonIsA503() {
        pastIncident("database timeout payment", "DATABASE", "PAYMENT_SERVICE");
        geminiSays = "Sorry, I cannot help with that.";

        assertThrows(AiUnavailableException.class, () ->
                service.generateSuggestionForNewIncident(request("database timeout", "payment", null, null)));
    }
}
