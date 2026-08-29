package com.example.Ai_Service.controller;

import com.example.Ai_Service.dto.EngineerRecommendationRequestDTO;
import com.example.Ai_Service.dto.EngineerCandidateDTO;
import com.example.Ai_Service.dto.IncidentResolvementSuggestionDTO;
import com.example.Ai_Service.dto.IncidentSuggestionNeededDTO;
import com.example.Ai_Service.service.EngineerAssignmentRecommendationService;
import com.example.Ai_Service.service.RagIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequiredArgsConstructor
@RequestMapping("/ai")
public class RagController {

    private final RagIntegrationService ragIntegrationService;
    private final EngineerAssignmentRecommendationService engineerAssignmentRecommendationService;

    @GetMapping("/rag/suggestion")
    public ResponseEntity<IncidentResolvementSuggestionDTO> getResolutionSuggestion(@RequestBody IncidentSuggestionNeededDTO request) {
        IncidentResolvementSuggestionDTO responseFromLLM = ragIntegrationService.generateSuggestionForNewIncident(request);
        return ResponseEntity.ok(responseFromLLM);
    }

    @GetMapping("/EngineersSuggestion-ForIncidentAssignment")
    public ResponseEntity<List<EngineerCandidateDTO>> getRecommendedEngineersForThisIncident(@RequestBody EngineerRecommendationRequestDTO request) {

        return ResponseEntity.ok(engineerAssignmentRecommendationService.recommendedEngineers(request));
    }

}
