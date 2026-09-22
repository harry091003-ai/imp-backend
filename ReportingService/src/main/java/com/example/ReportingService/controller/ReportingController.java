package com.example.ReportingService.controller;

import com.example.ReportingService.dto.*;
import com.example.ReportingService.service.EngineerPerformanceService;
import com.example.ReportingService.service.EngineerWorkloadService;
import com.example.ReportingService.service.ReportingService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportingController {

    private final ReportingService reportingService;

    // 1. Get single incidentReport
    @GetMapping("/incidents/{incidentId}")
    public ResponseEntity<IncidentReportResponseDTO> getIncident(
            @PathVariable UUID incidentId) {

        return ResponseEntity.ok(reportingService.getIncident(incidentId));
    }

    // 2. List incidents with filters
    @GetMapping("/incidents")
    public ResponseEntity<Page<IncidentReportResponseDTO>> getIncidents(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            Pageable pageable) {

        return ResponseEntity.ok(reportingService.getIncidents(status, severity,pageable));
    }

    // 3. List of Audit logs using Incident Id
    @GetMapping("/incidents/{incidentId}/audit")
    public ResponseEntity<List<IncidentAuditDTO>> getAuditLogs(
            @PathVariable UUID incidentId) {

        return ResponseEntity.ok(reportingService.getAuditLogs(incidentId));
    }

    // 4. Metrics
    @GetMapping("/metrics")
    public ResponseEntity<MetricsResponseDTO> getDailyMetrics() {

        return ResponseEntity.ok(reportingService.getMetrics());
    }

    //5. ServicesWithThereFailingCount
    @GetMapping("/top-services/failing")
    public ResponseEntity<List<TopFailingServicesDTO>>findTopFailingService(){
        List<TopFailingServicesDTO> services=   reportingService.getTopFailingServices();
        return ResponseEntity.ok(services);
    }

    // Incidents Happened on last x days for getting the trend of INCIDENT
    // this is useful like Management wants to see Are incidents increasing or decreasing.
    // we can see in graphically form are the incidents increasing or decreasing on daily basis.. eg: graph of last 7 days.
    // B4 step 5: days must be 1 to 365.
    // Before: days=-5 looked into the future (empty answer), and a huge number scanned the whole table.
@GetMapping("/trends")
    public ResponseEntity<List<IncidentsHappeningTrendDTO>>IncidentsHappenedTrend(
            @RequestParam(defaultValue ="7") @Min(value = 1, message = "days must be at least 1")
            @Max(value = 365, message = "days must be at most 365") int days){
    List<IncidentsHappeningTrendDTO>trends= reportingService.getIncidentsHappenedTrends(days);
return ResponseEntity.ok(trends);
    }


    //---------------------------------
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
