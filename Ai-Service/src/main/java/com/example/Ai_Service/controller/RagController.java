package com.example.Ai_Service.controller;

import com.example.Ai_Service.dto.EngineerRecommendationRequestDTO;
import com.example.Ai_Service.dto.EngineerCandidateDTO;
import com.example.Ai_Service.dto.IncidentResolvementSuggestionDTO;
import com.example.Ai_Service.dto.IncidentSuggestionNeededDTO;
import com.example.Ai_Service.service.EngineerAssignmentRecommendationService;
import com.example.Ai_Service.service.RagIntegrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequiredArgsConstructor
@RequestMapping("/ai")
// B4 step 4: @Valid added to both endpoints. Without it, the rules on the DTOs never run.
// (Both are still GET-with-body - known issue #7, not changed here.)
public class RagController {

    private final RagIntegrationService ragIntegrationService;
    private final EngineerAssignmentRecommendationService engineerAssignmentRecommendationService;

    @GetMapping("/rag/suggestion")
    public ResponseEntity<IncidentResolvementSuggestionDTO> getResolutionSuggestion(@Valid @RequestBody IncidentSuggestionNeededDTO request) {
        IncidentResolvementSuggestionDTO responseFromLLM = ragIntegrationService.generateSuggestionForNewIncident(request);
        return ResponseEntity.ok(responseFromLLM);
    }

    @GetMapping("/EngineersSuggestion-ForIncidentAssignment")
    public ResponseEntity<List<EngineerCandidateDTO>> getRecommendedEngineersForThisIncident(@Valid @RequestBody EngineerRecommendationRequestDTO request) {

        return ResponseEntity.ok(engineerAssignmentRecommendationService.recommendedEngineers(request));
    }

}
