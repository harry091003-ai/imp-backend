package com.example.ReportingService.controller;

import com.example.ReportingService.dto.EngineerPerformanceStatsDTO;
import com.example.ReportingService.dto.EngineerWorkloadDTO;
import com.example.ReportingService.entity.EngineerPerformanceStats;
import com.example.ReportingService.service.EngineerPerformanceService;
import com.example.ReportingService.service.EngineerWorkloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

}
