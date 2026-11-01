package com.example.Ai_Service;

import com.example.Ai_Service.exception.AiUnavailableException;
import com.example.Ai_Service.service.ClassificationService;
import com.example.Ai_Service.service.EngineerAssignmentRecommendationService;
import com.example.Ai_Service.service.RagIntegrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI plan, phase 1 (2026-11-01). The whole Ai-Service in the test world, with fake AI services (no Gemini call).
 *
 * The engineer recommendation shows each engineer's SLA rate and workload. ReportingService hides those numbers from
 * engineers on purpose, so here too only a supervisor or an admin may ask. The suggestion stays open to every user.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-endpoint-security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "internalsecret=dummy-test-only",
        "apikey=dummy-test-only",
        "spring.kafka.bootstrap-servers=localhost:1",
        "spring.kafka.listener.auto-startup=false"
})
@AutoConfigureMockMvc
class AiEndpointSecurityTest {

    private static final String ENGINEERS_URL = "/ai/EngineersSuggestion-ForIncidentAssignment";
    private static final String SUGGESTION_URL = "/ai/rag/suggestion";
    private static final String BODY = "{\"title\":\"Disk full\",\"description\":\"The disk on db-1 is full\"}";

    @Autowired
    MockMvc mvc;
    @MockitoBean
    RagIntegrationService rag;
    @MockitoBean
    EngineerAssignmentRecommendationService engineers;
    @MockitoBean
    ClassificationService classification;

    private static MockHttpServletRequestBuilder as(String url, String role, String body) {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Internal-Secret", "dummy-test-only")
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-Roles", role);
    }

    // ------------------------------------------------------------------ who may ask

    @Test
    void anEngineerCannotAskForTheEngineerRecommendation() throws Exception {
        mvc.perform(as(ENGINEERS_URL, "ROLE_ENGINEER", BODY)).andExpect(status().isForbidden());
    }

    @Test
    void aSupervisorCanAsk() throws Exception {
        mvc.perform(as(ENGINEERS_URL, "ROLE_SUPERVISOR", BODY)).andExpect(status().isOk());
    }

    @Test
    void anAdminCanAsk() throws Exception {
        mvc.perform(as(ENGINEERS_URL, "ROLE_ADMIN", BODY)).andExpect(status().isOk());
    }

    @Test
    void nobodyLoggedInCannotAsk() throws Exception {
        mvc.perform(post(ENGINEERS_URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void anEngineerCanStillAskForASuggestion() throws Exception {
        mvc.perform(as(SUGGESTION_URL, "ROLE_ENGINEER", BODY)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ category and service values

    private static String bodyWith(String field, String value) {
        return "{\"title\":\"Disk full\",\"description\":\"The disk on db-1 is full\",\"" + field + "\":\"" + value + "\"}";
    }

    @Test
    void aCategoryThatDoesNotExistIsRefusedOnBothEndpoints() throws Exception {
        // A typo used to miss the filter silently and search everything, on a paid call.
        mvc.perform(as(SUGGESTION_URL, "ROLE_ENGINEER", bodyWith("category", "databse"))).andExpect(status().isBadRequest());
        mvc.perform(as(ENGINEERS_URL, "ROLE_ADMIN", bodyWith("category", "databse"))).andExpect(status().isBadRequest());
    }

    @Test
    void aServiceThatDoesNotExistIsRefusedOnBothEndpoints() throws Exception {
        mvc.perform(as(SUGGESTION_URL, "ROLE_ENGINEER", bodyWith("serviceName", "payment"))).andExpect(status().isBadRequest());
        mvc.perform(as(ENGINEERS_URL, "ROLE_ADMIN", bodyWith("serviceName", "payment"))).andExpect(status().isBadRequest());
    }

    @Test
    void realValuesAreAccepted() throws Exception {
        mvc.perform(as(SUGGESTION_URL, "ROLE_ENGINEER", bodyWith("category", "DATABASE"))).andExpect(status().isOk());
        mvc.perform(as(SUGGESTION_URL, "ROLE_ENGINEER", bodyWith("serviceName", "PAYMENT_SERVICE"))).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ POST /ai/classify

    @Test
    void anEngineerCanAskForAClassificationBecauseAnyoneCanCreateAnIncident() throws Exception {
        mvc.perform(as("/ai/classify", "ROLE_ENGINEER", BODY)).andExpect(status().isOk());
    }

    @Test
    void nobodyLoggedInCannotAskForAClassification() throws Exception {
        mvc.perform(post("/ai/classify").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
    }

    @Test
    void aClassificationNeedsATitleAndADescription() throws Exception {
        mvc.perform(as("/ai/classify", "ROLE_ENGINEER", "{\"title\":\"Disk full\"}")).andExpect(status().isBadRequest());
        mvc.perform(as("/ai/classify", "ROLE_ENGINEER", "{\"description\":\"Disk full\"}")).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ AI failures

    @Test
    void anAiFailureIsACleanAnswerNotA500() throws Exception {
        when(rag.generateSuggestionForNewIncident(any())).thenThrow(new AiUnavailableException("AI_UNAVAILABLE",
                "The AI is not available right now. Please try again later.", HttpStatus.SERVICE_UNAVAILABLE, null));

        mvc.perform(as(SUGGESTION_URL, "ROLE_ENGINEER", BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("The AI is not available right now. Please try again later."));
    }
}
