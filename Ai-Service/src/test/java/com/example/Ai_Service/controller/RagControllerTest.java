package com.example.Ai_Service.controller;

import com.example.Ai_Service.dto.IncidentResolvementSuggestionDTO;
import com.example.Ai_Service.service.ClassificationService;
import com.example.Ai_Service.service.EngineerAssignmentRecommendationService;
import com.example.Ai_Service.service.RagIntegrationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue 7 (2026-10-28): both AI endpoints were GET with a JSON body. Many clients, proxies and gateways
 * drop the body of a GET, so the request arrives empty. They are POST now.
 * Only the URL mapping is tested here: the controller with fake services (no Gemini call, no database).
 */
class RagControllerTest {

    private final RagIntegrationService rag = mock(RagIntegrationService.class);
    private final EngineerAssignmentRecommendationService engineers = mock(EngineerAssignmentRecommendationService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new RagController(rag, engineers, mock(ClassificationService.class))).build();

    private static final String BODY = "{\"title\":\"Disk full\",\"description\":\"The disk on db-1 is full\"}";

    @Test
    void suggestionIsAPost() throws Exception {
        when(rag.generateSuggestionForNewIncident(any())).thenReturn(IncidentResolvementSuggestionDTO.builder().build());

        mvc.perform(post("/ai/rag/suggestion").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        verify(rag).generateSuggestionForNewIncident(any());
    }

    @Test
    void engineerSuggestionIsAPost() throws Exception {
        when(engineers.recommendedEngineers(any(), any())).thenReturn(List.of());

        mvc.perform(post("/ai/EngineersSuggestion-ForIncidentAssignment")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        verify(engineers).recommendedEngineers(any(), any());
    }

    @Test
    void getIsRefusedOnBothAndNoAiCallIsMade() throws Exception {
        mvc.perform(get("/ai/rag/suggestion").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/ai/EngineersSuggestion-ForIncidentAssignment").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(rag, engineers);
    }
}
