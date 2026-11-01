package com.example.ReportingService.controller;

import com.example.ReportingService.dto.EngineerPerformanceStatsDTO;
import com.example.ReportingService.dto.EngineerSummaryDTO;
import com.example.ReportingService.dto.EngineerSummaryRequestDTO;
import com.example.ReportingService.dto.EngineerWorkloadDTO;
import com.example.ReportingService.entity.EngineerPerformanceStats;
import com.example.ReportingService.service.EngineerPerformanceService;
import com.example.ReportingService.service.EngineerWorkloadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/reports")
@RequiredArgsConstructor
public class InternalReportingContoller {
    private final EngineerPerformanceService engineerPerformanceService;
private final EngineerWorkloadService engineerWorkloadService;
    @GetMapping("/engineer-performance/{engineerId}/{category}/{service}")
    public EngineerPerformanceStatsDTO getEngineerPerformanceStats(@PathVariable UUID engineerId,
                                                                   @PathVariable String category,
                                                                   @PathVariable String service){
    return engineerPerformanceService.engineerPerformanceStats(engineerId,category,service);
    }
   @GetMapping("/engineer-workload/{engineerId}")
    public EngineerWorkloadDTO    getEngineerWorkload(@PathVariable("engineerId") UUID engineerId){
        return engineerWorkloadService.engineerWorkloadStats(engineerId);
    }

    // AI plan phase 1: Ai-Service used to make the 2 calls above for each engineer, one after the other. Now one call.
    @PostMapping("/engineer-summaries")
    public List<EngineerSummaryDTO> getEngineerSummaries(@Valid @RequestBody EngineerSummaryRequestDTO request){
        return request.getEngineerIds().stream().map(engineerId -> {
            EngineerPerformanceStatsDTO stats = engineerPerformanceService.engineerPerformanceStats(
                    engineerId, request.getCategory(), request.getServiceName());
            return EngineerSummaryDTO.builder()
                    .engineerId(engineerId)
                    .totalIncidentsResolved(stats.getTotalIncidentsResolved())
                    .expertiseScore(stats.getExpertiseScore())
                    .slaSuccessRate(stats.getSlaSuccessRate())
                    .activeIncidentCount(engineerWorkloadService.engineerWorkloadStats(engineerId).getActiveIncidentCount())
                    .build();
        }).toList();
    }

}
