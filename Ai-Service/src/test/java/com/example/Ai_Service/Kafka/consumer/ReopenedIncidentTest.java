package com.example.Ai_Service.Kafka.consumer;

import com.example.Ai_Service.FakeEmbeddingModel;
import com.example.Ai_Service.service.AiCallGuard;
import com.example.Ai_Service.service.ClassificationService;
import com.example.Ai_Service.service.RagIntegrationService;
import com.example.Ai_Service.service.SimilarIncidentSearch;
import com.project.events.IncidentResolutionReportEvent;
import com.project.events.IncidentStatusUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * AI plan phase 2 (2026-11-01): a reopened incident's solution did not work, so nobody should be told to try it.
 * The REAL listener and service. The listener methods are called directly, the way Kafka would call them.
 */
class ReopenedIncidentTest {

    private SimpleVectorStore store;
    private IncidentEventConsumer consumer;

    @BeforeEach
    void setUp() {
        store = SimpleVectorStore.builder(new FakeEmbeddingModel()).build();
        AiCallGuard guard = new AiCallGuard(5);
        RagIntegrationService rag = new RagIntegrationService(store, ChatClient.builder(mock(ChatModel.class)).build(),
                new SimilarIncidentSearch(store, guard, 0.6), guard,
                new ClassPathResource("prompts/ragIntegrationPrompts/rag-userPrompt.st"),
                new ClassPathResource("prompts/ragIntegrationPrompts/rag-systemPrompt.st"),
                4, 10);
        consumer = new IncidentEventConsumer(mock(ClassificationService.class), rag);
    }

    private void resolved(UUID id, String rootCause) {
        consumer.ConsumeIncidentResolvedEvent(IncidentResolutionReportEvent.builder().incidentId(id)
                .title("database timeout").description("payment")
                .rootCause(rootCause).resolutionNotes("Raised the pool size to 50").build());
    }

    private void statusChanged(UUID id, String status) {
        consumer.ConsumeIncidentStatusUpdatedEvent(IncidentStatusUpdatedEvent.builder().incidentId(id).UpdatedStatus(status).build());
    }

    private List<Document> remembered() {
        return store.similaritySearch(SearchRequest.builder().query("anything").topK(10).similarityThreshold(0).build());
    }

    @Test
    void aReopenedIncidentLeavesTheMemory() {
        UUID id = UUID.randomUUID();
        resolved(id, "The connection pool was too small");

        statusChanged(id, "REOPENED");

        assertEquals(List.of(), remembered());
    }

    @Test
    void otherIncidentsStay() {
        UUID reopened = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        resolved(reopened, "The connection pool was too small");
        resolved(other, "The connection pool was too small");

        statusChanged(reopened, "REOPENED");

        assertEquals(List.of(other.toString()), remembered().stream().map(Document::getId).toList());
    }

    @Test
    void otherStatusChangesRemoveNothing() {
        UUID id = UUID.randomUUID();
        resolved(id, "The connection pool was too small");

        for (String status : List.of("OPEN", "ASSIGNED", "IN_PROGRESS", "RESOLVED", "CLOSED")) {
            statusChanged(id, status);
        }

        assertEquals(1, remembered().size());
    }

    @Test
    void reopeningAnIncidentThatWasNeverRememberedIsHarmless() {
        statusChanged(UUID.randomUUID(), "REOPENED");

        assertEquals(List.of(), remembered());
    }

    @Test
    void resolvedAgainItComesBackWithTheNewSolution() {
        UUID id = UUID.randomUUID();
        resolved(id, "The connection pool was too small");
        statusChanged(id, "REOPENED");

        resolved(id, "A slow query was holding the connections");

        assertEquals("A slow query was holding the connections", remembered().get(0).getMetadata().get("rootCause"));
    }

    // ------------------------------------------------------------------ clearing the old rows

    @Test
    void theMigrationClearsOnlyTheOldVectors() throws Exception {
        String sql = new ClassPathResource("db/migration/V2__clear_old_incident_vectors.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        List<String> statements = sql.lines().filter(line -> !line.isBlank() && !line.startsWith("--")).toList();
        assertEquals(List.of("DELETE FROM public.resolved_incidents_vectors;"), statements, "ai_insight must not be touched");
    }
}
