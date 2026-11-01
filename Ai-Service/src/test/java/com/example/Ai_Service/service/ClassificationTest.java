package com.example.Ai_Service.service;

import com.example.Ai_Service.dto.ClassificationResultDTO;
import com.example.Ai_Service.dto.ClassifyIncidentRequestDTO;
import com.example.Ai_Service.dto.IncidentValues;
import com.example.Ai_Service.entity.AiInsight;
import com.example.Ai_Service.exception.AiUnavailableException;
import com.example.Ai_Service.repository.AiInsightRepository;
import com.example.Ai_Service.repository.ClassificationSuggestionRepository;
import com.project.events.IncidentCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI plan phase 3 (2026-11-01): POST /ai/classify and how often the AI agrees. The REAL service and prompt files, a real
 * database (H2 in Postgres mode, schema from the REAL migration so it must match the entity) and a fake Gemini.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-classification;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:ai_insight_for_tests.sql,classpath:db/migration/V3__classification_suggestion.sql",
        "eureka.client.enabled=false",
        "internalsecret=dummy-test-only",
        "apikey=dummy-test-only"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // no test-wide transaction: we test the real ones
class ClassificationTest {

    private static final String GOOD = """
            {"category":"DATABASE","severity":"HIGH","priority":"MEDIUM","serviceName":"PAYMENT_SERVICE","summary":"Payments time out","confidence":0.9}""";

    @Autowired ClassificationSuggestionRepository suggestions;
    @Autowired AiInsightRepository insights;
    @Autowired DataSource dataSource;

    private final AtomicInteger geminiCalls = new AtomicInteger();
    private volatile String geminiSays = GOOD;
    private volatile RuntimeException geminiThrows;

    private final ChatModel fakeGemini = new ChatModel() {
        @Override
        public ChatResponse call(Prompt prompt) {
            geminiCalls.incrementAndGet();
            if (geminiThrows != null) {
                throw geminiThrows;
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage(geminiSays))));
        }
    };

    private ClassificationService service;

    @BeforeEach
    void setUp() {
        insights.deleteAll();
        suggestions.deleteAll();
        service = new ClassificationService(ChatClient.builder(fakeGemini).build(), new AiCallGuard(5), suggestions, insights,
                new ClassPathResource("prompts/classificationPrompts/classification-userPrompt.st"),
                new ClassPathResource("prompts/classificationPrompts/classification-systemPrompt.st"),
                0.35, 24);
    }

    private static ClassifyIncidentRequestDTO request(String title, String description) {
        ClassifyIncidentRequestDTO request = new ClassifyIncidentRequestDTO();
        request.setTitle(title);
        request.setDescription(description);
        return request;
    }

    private ClassificationResultDTO classify() {
        return service.classify(request("Payments slow", "Every payment times out"));
    }

    private static IncidentCreatedEvent.IncidentCreatedEventBuilder created() {
        return IncidentCreatedEvent.builder().incidentId(UUID.randomUUID())
                .title("Payments slow").description("Every payment times out")
                .category("DATABASE").severity("HIGH").priority("MEDIUM").serviceName("PAYMENT_SERVICE");
    }

    // ------------------------------------------------------------------ asking

    @Test
    void aGoodAnswerIsReturned() {
        ClassificationResultDTO answer = classify();

        assertEquals("DATABASE", answer.getCategory());
        assertEquals("HIGH", answer.getSeverity());
        assertEquals("MEDIUM", answer.getPriority());
        assertEquals("PAYMENT_SERVICE", answer.getServiceName());
        assertEquals("Payments time out", answer.getSummary());
        assertEquals(0.9, answer.getConfidence());
        assertTrue(answer.isConfident());
    }

    @Test
    void theSameTextAgainCostsNoSecondCall() {
        classify();
        ClassificationResultDTO again = service.classify(request("  Payments slow ", "Every payment times out  "));

        assertEquals(1, geminiCalls.get());
        assertEquals("DATABASE", again.getCategory());
    }

    @Test
    void differentTextIsAskedAgain() {
        classify();
        service.classify(request("Payments slow", "Only refunds time out"));

        assertEquals(2, geminiCalls.get());
    }

    @Test
    void anOldSuggestionIsDeletedAndAskedAgain() {
        classify();
        new JdbcTemplate(dataSource).update("UPDATE classification_suggestion SET created_at = ?", LocalDateTime.now().minusHours(25));

        classify();

        assertEquals(2, geminiCalls.get());
        assertEquals(1, suggestions.count());
    }

    @Test
    void textWithBracesDoesNotBreakThePrompt() {
        assertEquals("DATABASE", service.classify(request("Error {host}", "log says {\"a\":1}")).getCategory());
    }

    // ------------------------------------------------------------------ never trust the answer

    @Test
    void aCategoryOrServiceThatDoesNotExistBecomesTheSafeValue() {
        geminiSays = "{\"category\":\"DATABSE\",\"severity\":\"HIGH\",\"priority\":\"LOW\",\"serviceName\":\"payments\",\"confidence\":0.9}";

        ClassificationResultDTO answer = classify();

        assertEquals("OTHER", answer.getCategory());
        assertEquals("OTHERS", answer.getServiceName());
    }

    @Test
    void aSeverityOrPriorityThatDoesNotExistIsLeftEmptyForTheUser() {
        geminiSays = "{\"category\":\"API\",\"severity\":\"URGENT\",\"priority\":\"P1\",\"serviceName\":\"OTHERS\",\"confidence\":0.9}";

        ClassificationResultDTO answer = classify();

        assertNull(answer.getSeverity());
        assertNull(answer.getPriority());
    }

    @Test
    void aVeryUnsureAnswerUsesTheSafeValues() {
        geminiSays = "{\"category\":\"DATABASE\",\"severity\":\"HIGH\",\"priority\":\"LOW\",\"serviceName\":\"PAYMENT_SERVICE\",\"confidence\":0.2}";

        ClassificationResultDTO answer = classify();

        assertEquals("OTHER", answer.getCategory());
        assertEquals("OTHERS", answer.getServiceName());
        assertFalse(answer.isConfident());
    }

    @Test
    void aMissingConfidenceCountsAsUnsure() {
        geminiSays = "{\"category\":\"DATABASE\",\"severity\":\"HIGH\",\"priority\":\"LOW\",\"serviceName\":\"PAYMENT_SERVICE\"}";

        ClassificationResultDTO answer = classify();

        assertEquals(0, answer.getConfidence());
        assertFalse(answer.isConfident());
    }

    @Test
    void aConfidenceOutOfRangeIsCut() {
        geminiSays = "{\"category\":\"API\",\"severity\":\"LOW\",\"priority\":\"LOW\",\"serviceName\":\"OTHERS\",\"confidence\":7}";

        assertEquals(1, classify().getConfidence());
    }

    @Test
    void aHugeSummaryDoesNotBreakTheSave() {
        geminiSays = "{\"category\":\"API\",\"severity\":\"LOW\",\"priority\":\"LOW\",\"serviceName\":\"OTHERS\",\"confidence\":0.9,\"summary\":\"" + "x".repeat(3000) + "\"}";

        assertEquals(2000, classify().getSummary().length());
    }

    @Test
    void aGeminiFailureIsA503AndSavesNothing() {
        geminiThrows = new RuntimeException("429 quota exceeded");

        assertThrows(AiUnavailableException.class, this::classify);
        assertEquals(0, suggestions.count());
    }

    // ------------------------------------------------------------------ how often the AI agrees

    @Test
    void whenTheUserKeepsTheSuggestionItIsNoConflict() {
        classify();
        IncidentCreatedEvent event = created().build();

        service.compareWithWhatWasSubmitted(event);

        AiInsight insight = insights.findById(event.getIncidentId()).orElseThrow();
        assertFalse(insight.isConflict());
        assertEquals("DATABASE", insight.getAiCategory());
        assertEquals(0.9, insight.getConfidence());
    }

    @Test
    void whenTheUserChangesAnyOfTheFourFieldsItIsAConflict() {
        for (IncidentCreatedEvent event : List.of(
                created().category("NETWORK").build(),
                created().severity("LOW").build(),
                created().priority("CRITICAL").build(),
                created().serviceName("AUTH_SERVICE").build())) {
            classify();

            service.compareWithWhatWasSubmitted(event);

            assertTrue(insights.findById(event.getIncidentId()).orElseThrow().isConflict(), event.getCategory() + event.getSeverity());
        }
    }

    @Test
    void comparingNeverCallsGemini() {
        classify();
        geminiCalls.set(0);

        service.compareWithWhatWasSubmitted(created().build());
        service.compareWithWhatWasSubmitted(created().title("Nobody asked about this").build());

        assertEquals(0, geminiCalls.get());
    }

    @Test
    void anIncidentNobodyAskedAboutIsNotMeasured() {
        service.compareWithWhatWasSubmitted(created().build());

        assertEquals(0, insights.count());
    }

    @Test
    void textChangedAfterAskingIsNotMeasured() {
        classify();

        service.compareWithWhatWasSubmitted(created().description("Changed before submitting").build());

        assertEquals(0, insights.count());
    }

    @Test
    void theSuggestionIsUsedOnlyOnce() {
        classify();
        service.compareWithWhatWasSubmitted(created().build());
        assertEquals(0, suggestions.count());

        service.compareWithWhatWasSubmitted(created().build());   // a second incident with the same text

        assertEquals(1, insights.count());
    }

    @Test
    void anEventWithoutTextIsSkipped() {
        service.compareWithWhatWasSubmitted(created().title(null).build());

        assertEquals(0, insights.count());
    }

    // ------------------------------------------------------------------ the prompt and the allowed values agree

    @Test
    void thePromptListsEveryAllowedValue() throws Exception {
        String prompt = new ClassPathResource("prompts/classificationPrompts/classification-systemPrompt.st")
                .getContentAsString(StandardCharsets.UTF_8);

        for (String allowed : List.of(IncidentValues.CATEGORY, IncidentValues.SERVICE, IncidentValues.SEVERITY, IncidentValues.PRIORITY)) {
            for (String value : allowed.split("\\|")) {
                assertTrue(prompt.contains(value), value + " is allowed, but the AI is never told about it");
            }
        }
    }
}
