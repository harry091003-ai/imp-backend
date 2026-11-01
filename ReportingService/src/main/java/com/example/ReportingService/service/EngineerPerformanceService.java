package com.example.ReportingService.service;

import com.example.ReportingService.dto.EngineerPerformanceStatsDTO;
import com.example.ReportingService.dto.EngineerWorkloadDTO;
import com.example.ReportingService.entity.EngineerPerformanceStats;
import com.example.ReportingService.entity.IncidentReport;
import com.example.ReportingService.repository.EngineerPerformanceStatsRepository;
import com.example.ReportingService.repository.IncidentReportRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
// This Service Handle  the Engineer Performance Stats by Using the data from the resolved incident reports

@Service
@RequiredArgsConstructor
public class EngineerPerformanceService {

    private final EngineerPerformanceStatsRepository engineerPerformanceStatsRepository;
private final IncidentReportRepository incidentReportRepository;

    @Transactional
    public void updateEngineerStats(IncidentReport incidentReport)
    { UUID engineerId=incidentReport.engineerToCredit();   // issue 62: not whoever pressed the button
        EngineerPerformanceStats engineerStats=engineerPerformanceStatsRepository.findByEngineerIdAndCategoryAndServiceName(engineerId,incidentReport.getCategory(),incidentReport.getServiceName())
                .orElse(EngineerPerformanceStats.builder()
                        .engineerId(engineerId)
                        .category(incidentReport.getCategory())
                        .serviceName(incidentReport.getServiceName())
                        .totalIncidentsResolved(0)
                        .avgResolutionTimeSeconds(0.0)
                        .slaSuccessRate(0.0)
                        .expertiseScore(0.0)
                        .avgSlaUtilization(0.0)
                .build());

        // Update total incidents resolved
        engineerStats.setTotalIncidentsResolved(engineerStats.getTotalIncidentsResolved()+1);

        // Update average resolution time using a Incremental Average formula (means updating the average without querying all old incidents again from DB)
        double oldAvgResolutionTimeSeconds =engineerStats.getAvgResolutionTimeSeconds();
        double CurrentIncidentResolutionTimeSeconds= incidentReport.getResolutionTimeSeconds();
        long totalIncidentsResolved=engineerStats.getTotalIncidentsResolved();
        double newAvgResolutionTimeSeconds=(oldAvgResolutionTimeSeconds*(totalIncidentsResolved-1)+(CurrentIncidentResolutionTimeSeconds*1))/totalIncidentsResolved;

        engineerStats.setAvgResolutionTimeSeconds(newAvgResolutionTimeSeconds);

        //Update SlaSuccessRate
        double oldSlaSuccessRate=engineerStats.getSlaSuccessRate();
        double currentSlaSuccess= incidentReport.isResolutionSlaBreached()?0:1;
       double newSlaSuccessRate=(oldSlaSuccessRate*(totalIncidentsResolved-1)+(currentSlaSuccess*1))/totalIncidentsResolved;
          engineerStats.setSlaSuccessRate(newSlaSuccessRate);


        // Calculating the avgSlaUtilization
//        Step1:Calculate  Time utilization of current incident
        long allowedSeconds= Duration.between(incidentReport.getCreatedAt(),incidentReport.getResolutionDeadline()).toSeconds();

        double current_TimeUtilized= Math.min((double)incidentReport.getResolutionTimeSeconds()/allowedSeconds,1.0);

//        Step2: Update the avgSlaUtilization using Incremental Average formula
        double old_AvgSlaUtilization=engineerStats.getAvgSlaUtilization();
        double new_AvgSlaUtilization=(old_AvgSlaUtilization*(totalIncidentsResolved-1)+(current_TimeUtilized*1))/totalIncidentsResolved;
        double final_AvgSlaUtilization=Math.min(1,new_AvgSlaUtilization);
        engineerStats.setAvgSlaUtilization(final_AvgSlaUtilization);


        // Update expertise score
        engineerStats.setExpertiseScore(calculateExpertiseScore(engineerStats));
      // Update last updated timestamp
        engineerStats.setLastUpdated(incidentReport.getResolvedAt());


        //finally saving the updated stats to DB
        engineerPerformanceStatsRepository.save(engineerStats);
    }

//for getting expertise Score , we are make formula that helps in  creating a normalized weighted score for an engineer
//steps:
// 1.Convert different metrics into same scale (0 → 1)
//2.Apply importance weights
//3.Convert final score into percentage
private double calculateExpertiseScore(
        EngineerPerformanceStats stats)
{
    double resolutionFactor =
            Math.min(
                    Math.log10(
                            stats.getTotalIncidentsResolved() + 1
                    ) / 3.0,
                    1.0
            );

    double slaFactor =
            stats.getSlaSuccessRate();
    double speedFactor = 1 - Math.min(
            stats.getAvgSlaUtilization(),
            1.0
    );
    return  (
            resolutionFactor * 0.35
                    + slaFactor * 0.40
                    + speedFactor * 0.25
    ) * 100;

}


public EngineerPerformanceStatsDTO engineerPerformanceStats(UUID engineerId, String category, String serviceName)
{   EngineerPerformanceStats EngineerStats= engineerPerformanceStatsRepository.findByEngineerIdAndCategoryAndServiceName(engineerId,category,serviceName).orElse(null);
    if(EngineerStats==null){return EngineerPerformanceStatsDTO.builder()
            .engineerId(engineerId)
            .expertiseScore(0)
            .slaSuccessRate(0)
            .avgSlaUtilization(1.0)
            .totalIncidentsResolved(0)
            .build();}
                       return    EngineerPerformanceStatsDTO
                                   .builder()
                                   .engineerId(EngineerStats.getEngineerId())
                                   .totalIncidentsResolved(EngineerStats.getTotalIncidentsResolved())
                                   .avgResolutionTimeSeconds(EngineerStats.getAvgResolutionTimeSeconds())
                                   .slaSuccessRate(EngineerStats.getSlaSuccessRate())
                                      .avgSlaUtilization(EngineerStats.getAvgSlaUtilization())
                                      .expertiseScore(EngineerStats.getExpertiseScore())
                                      .lastUpdated(EngineerStats.getLastUpdated())
                                   .build();

}



}
