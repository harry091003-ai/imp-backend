package com.example.Ai_Service.controller;

import com.example.Ai_Service.dto.EngineerRecommendationRequestDTO;
import com.example.Ai_Service.dto.EngineerCandidateDTO;
import com.example.Ai_Service.dto.IncidentResolvementSuggestionDTO;
import com.example.Ai_Service.dto.IncidentSuggestionNeededDTO;
import com.example.Ai_Service.dto.ClassificationResultDTO;
import com.example.Ai_Service.dto.ClassifyIncidentRequestDTO;
import com.example.Ai_Service.service.ClassificationService;
import com.example.Ai_Service.service.EngineerAssignmentRecommendationService;
import com.example.Ai_Service.service.RagIntegrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequiredArgsConstructor
@RequestMapping("/ai")
// B4 step 4: @Valid added to both endpoints. Without it, the rules on the DTOs never run.
// Issue 7 (2026-10-28): both were GET with a JSON body. Many clients and proxies drop a GET's body, so they are POST now.
public class RagController {

    private final RagIntegrationService ragIntegrationService;
    private final EngineerAssignmentRecommendationService engineerAssignmentRecommendationService;
    private final ClassificationService classificationService;

    // AI plan phase 3: the screen asks once the title and description are typed (not on every key: the gateway allows 5 AI
    // calls a minute). It is a suggestion. The user decides, and creates the incident as usual.
    @PostMapping("/classify")
    public ResponseEntity<ClassificationResultDTO> classifyIncident(@Valid @RequestBody ClassifyIncidentRequestDTO request) {
        return ResponseEntity.ok(classificationService.classify(request));
    }

    @PostMapping("/rag/suggestion")
    public ResponseEntity<IncidentResolvementSuggestionDTO> getResolutionSuggestion(@Valid @RequestBody IncidentSuggestionNeededDTO request) {
        IncidentResolvementSuggestionDTO responseFromLLM = ragIntegrationService.generateSuggestionForNewIncident(request);
        return ResponseEntity.ok(responseFromLLM);
    }

    @PostMapping("/EngineersSuggestion-ForIncidentAssignment")
    public ResponseEntity<List<EngineerCandidateDTO>> getRecommendedEngineersForThisIncident(@Valid @RequestBody EngineerRecommendationRequestDTO request,
                                                                                             Authentication auth) {

        return ResponseEntity.ok(engineerAssignmentRecommendationService.recommendedEngineers(request, auth));
    }

}
