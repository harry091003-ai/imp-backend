package com.example.Ai_Service.service;

import com.example.Ai_Service.FakeEmbeddingModel;
import com.example.Ai_Service.dto.IncidentResolvementSuggestionDTO;
import com.example.Ai_Service.dto.IncidentSuggestionNeededDTO;
import com.project.events.IncidentResolutionReportEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.io.ClassPathResource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI plan phase 2 (2026-11-01): what the AI remembers about a resolved incident. The REAL code with a fake embedding
 * model, an in-memory vector store and a fake Gemini.
 *
 *   - only the PROBLEM is embedded, because a new incident is a problem with no solution yet, so problem is compared with problem
 *   - the root cause and the resolution travel with it, and Gemini still gets them
 *   - junk is not remembered
 */
class ResolvedIncidentMemoryTest {

    private static final String GOOD_ROOT_CAUSE = "The connection pool was too small for the traffic";
    private static final String GOOD_NOTES = "Raised the pool size to 50 and restarted the service";

    private final FakeEmbeddingModel embedding = new FakeEmbeddingModel();
    private final AtomicReference<String> promptSent = new AtomicReference<>();
    private SimpleVectorStore store;
    private RagIntegrationService service;

    private final ChatModel fakeGemini = new ChatModel() {
        @Override
        public ChatResponse call(Prompt prompt) {
            promptSent.set(prompt.getContents());
            return new ChatResponse(List.of(new Generation(new AssistantMessage("{\"rootCause\":\"a\",\"resolutionNotes\":\"b\"}"))));
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

    private static IncidentResolutionReportEvent.IncidentResolutionReportEventBuilder resolved(UUID id) {
        return IncidentResolutionReportEvent.builder().incidentId(id)
                .title("database timeout").description("payment")
                .category("DATABASE").serviceName("PAYMENT_SERVICE")
                .severity("HIGH").priority("LOW").environment("PRODUCTION")
                .rootCause(GOOD_ROOT_CAUSE).resolutionNotes(GOOD_NOTES)
                .resolvedBy(UUID.randomUUID()).resolutionTimeInSeconds(60).resolvedAt(LocalDateTime.of(2026, 10, 1, 10, 0));
    }

    private List<Document> remembered() {
        return store.similaritySearch(SearchRequest.builder().query("anything").topK(10).similarityThreshold(0).build());
    }

    private static IncidentSuggestionNeededDTO question(String title, String description) {
        IncidentSuggestionNeededDTO request = new IncidentSuggestionNeededDTO();
        request.setTitle(title);
        request.setDescription(description);
        return request;
    }

    // ------------------------------------------------------------------ what is embedded

    @Test
    void onlyTheProblemIsEmbedded() {
        service.storingResolvedIncidentsInVectorDb(resolved(UUID.randomUUID()).build());

        String text = remembered().get(0).getText();
        assertTrue(text.contains("database timeout"), text);
        assertTrue(text.contains("payment"), text);
        assertFalse(text.contains("pool"), "the solution is not part of the search text: " + text);
    }

    @Test
    void aLongSolutionDoesNotWeakenTheMatch() {
        service.storingResolvedIncidentsInVectorDb(resolved(UUID.randomUUID())
                .rootCause("word ".repeat(100) + "cause").resolutionNotes("other ".repeat(100) + "notes").build());

        IncidentResolvementSuggestionDTO answer = service.generateSuggestionForNewIncident(question("database timeout", "payment"));

        assertEquals(IncidentResolvementSuggestionDTO.Status.OK, answer.getStatus(), "a long solution used to push the match below the bar");
        assertTrue(answer.getSources().get(0).getSimilarity() > 0.99, "the same problem: nearly 1");
    }

    // ------------------------------------------------------------------ what travels with it

    @Test
    void geminiStillGetsTheRootCauseAndTheResolution() {
        service.storingResolvedIncidentsInVectorDb(resolved(UUID.randomUUID()).build());

        service.generateSuggestionForNewIncident(question("database timeout", "payment"));

        assertTrue(promptSent.get().contains(GOOD_ROOT_CAUSE), promptSent.get());
        assertTrue(promptSent.get().contains(GOOD_NOTES), promptSent.get());
    }

    @Test
    void severityPriorityAndEnvironmentAreRemembered() {
        service.storingResolvedIncidentsInVectorDb(resolved(UUID.randomUUID()).build());

        Document document = remembered().get(0);
        assertEquals("HIGH", document.getMetadata().get("severity"));
        assertEquals("LOW", document.getMetadata().get("priority"));
        assertEquals("PRODUCTION", document.getMetadata().get("environment"));
    }

    @Test
    void anOlderEventWithoutTheNewFieldsIsStillStored() {
        // A message waiting in Kafka from before this change has no severity, priority or environment.
        service.storingResolvedIncidentsInVectorDb(resolved(UUID.randomUUID()).severity(null).priority(null).environment(null).build());

        assertEquals(1, remembered().size());
    }

    @Test
    void aMissingCategoryOrDateDoesNotLoseTheIncident() {
        service.storingResolvedIncidentsInVectorDb(resolved(UUID.randomUUID()).category(null).serviceName(null).resolvedAt(null).build());

        assertEquals(1, remembered().size());
    }

    @Test
    void aRepeatedEventOverwritesInsteadOfAddingASecondCopy() {
        UUID id = UUID.randomUUID();
        service.storingResolvedIncidentsInVectorDb(resolved(id).build());
        service.storingResolvedIncidentsInVectorDb(resolved(id).build());

        assertEquals(1, remembered().size());
    }

    // ------------------------------------------------------------------ junk is not remembered

    @Test
    void aJunkRootCauseIsNotRemembered() {
        service.storingResolvedIncidentsInVectorDb(resolved(UUID.randomUUID()).rootCause("asd").build());

        assertEquals(List.of(), remembered());
    }

    @Test
    void junkResolutionNotesAreNotRemembered() {
        service.storingResolvedIncidentsInVectorDb(resolved(UUID.randomUUID()).resolutionNotes("fixed").build());

        assertEquals(List.of(), remembered());
    }

    @Test
    void missingTextsAreNotRemembered() {
        service.storingResolvedIncidentsInVectorDb(resolved(UUID.randomUUID()).rootCause(null).build());
        service.storingResolvedIncidentsInVectorDb(resolved(UUID.randomUUID()).resolutionNotes("          ").build());

        assertEquals(List.of(), remembered());
    }

    @Test
    void junkCostsNoEmbeddingCall() {
        embedding.calls.set(0);

        service.storingResolvedIncidentsInVectorDb(resolved(UUID.randomUUID()).rootCause("asd").build());

        assertEquals(0, embedding.calls.get());
    }
}
