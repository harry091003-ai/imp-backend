package com.example.ReportingService.schedular.slaBreachingRisk;

import com.example.ReportingService.entity.IncidentReport;
import com.example.ReportingService.entity.SLA_RiskLevel;
import com.example.ReportingService.kafka.producer.SLA_BreachingRiskEventProducer;
import com.example.ReportingService.repository.IncidentReportRepository;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class SLA_BreachingRiskSchedular {
    private final IncidentReportRepository incidentReportRepository;
    private final SLA_BreachingRiskEventProducer SLA_BreachingRiskEventProducer;
    // Outbox step 4: one short transaction per page: the new risk levels AND their outbox event rows are
    // saved together, or neither. Before, the event was sent first and the level saved after - a crash in
    // between sent the event but kept the old level, so the next run sent the same event again.
    private final TransactionTemplate transactionTemplate;

    private static final Logger log= LoggerFactory.getLogger(SLA_BreachingRiskSchedular.class);
@Scheduled(fixedDelay = 60000)
@SchedulerLock(name="monitorAssignment_SLA_Breaching_Risk",lockAtLeastFor = "PT50s",lockAtMostFor = "PT2M")
    public void monitorAssignmentRisk(){
    log.info("Starting SLA Assignment Risk Monitoring");
  LocalDateTime BatchLastEntry_AssignedDeadline=LocalDateTime.now().minusYears(1);
    UUID BatchLastEntry_IncidentId= new UUID(0L,0L);
    List<IncidentReport> incidents;
    LocalDateTime now = LocalDateTime.now();
    // Only incidents whose deadline is within next 30 mins
    LocalDateTime deadlineWindow = now.plusMinutes(30);
    PageRequest pageRequest=PageRequest.of(0,50);
    do {
        // the cursor values must be final to be used inside the transaction below
        LocalDateTime cursorDeadline = BatchLastEntry_AssignedDeadline;
        UUID cursorIncidentId = BatchLastEntry_IncidentId;
        // Outbox step 4: ONE transaction for this page (read, change levels, save events + levels)
        incidents = transactionTemplate.execute(status -> {
        List<IncidentReport> modifiedIncidents = new ArrayList<>();

        List<IncidentReport> page = incidentReportRepository
                .findAssignmentSLARiskIncidents(
                        deadlineWindow,
                        cursorDeadline,cursorIncidentId,pageRequest
                );
        log.info("incidents fetched for Assignment SLA Breaching Risk  → {}",page.size());
        for (IncidentReport incident : page) {

            processAssignmentRisk(incident, now,modifiedIncidents);
        }
        // we are only saving the modified incidents only
        if(!modifiedIncidents.isEmpty()){
            incidentReportRepository.saveAll(modifiedIncidents);
        }
        return page;
        });

        //Update the BatchLastEntry_CreatedAt and BatchLastEntry_IncidentId for then next batch as its used in cursor pagination
          if(!incidents.isEmpty()){
              BatchLastEntry_AssignedDeadline=incidents.getLast().getAssignmentDeadline();
                BatchLastEntry_IncidentId=incidents.getLast().getIncidentId();
          }
    } while (!incidents.isEmpty());
}

    private void processAssignmentRisk(
            IncidentReport incident,
            LocalDateTime now,List<IncidentReport> modifiedIncidents
    ) {
log.info("Enter the ProcessAssignmentRisk method ");
        long totalMinutes = Duration.between(
                incident.getCreatedAt(),
                incident.getAssignmentDeadline()
        ).toMinutes();

        long elapsedMinutes = Duration.between(
                incident.getCreatedAt(),
                now
        ).toMinutes();
        double riskScore =
                (double) elapsedMinutes / totalMinutes;
        riskScore = Math.min(riskScore, 1.0);
        SLA_RiskLevel newLevel = calculateRiskLevel(riskScore);

        String previousLevel =
                incident.getAssignmentRiskLevel().name();
        // Only act if level changed
        if (!newLevel.name().equals(previousLevel)) {
log.info("as the AssignmentSLA new level {} and old level is {} so we need to publish the event ",newLevel.name(),previousLevel);
            log.info(" also the Calculated riskScore={} and newLevel={} for incidentId={} that gets stores in our db as the lisk level changed",riskScore,newLevel.name(),incident.getIncidentId());

            log.info(
                    "AssignmentSLA Risk level changed for incidentId={} from {} to {}",
                    incident.getIncidentId(),
                    previousLevel,
                    newLevel
            );

            incident.setAssignmentRiskScore(riskScore);

            incident.setAssignmentRiskLevel(newLevel);

            // publish event
            SLA_BreachingRiskEventProducer.publishAssignmentSlaBreachingRiskEvent(
                    incident,
                    previousLevel,
                    newLevel.name(),
                    riskScore
            );
 log.info("Published SLA Assignment Breaching Risk Event with New Risk Level {} and Risk Score {} ",newLevel.name(),riskScore);
            // as the RiskLevel and RiskScore got changed for this incident so we need to  save the updated changes for that incident in db
            modifiedIncidents.add(incident);

        }
    }


    private SLA_RiskLevel calculateRiskLevel(double riskScore) {

        if (riskScore >= 0.90) {
            return SLA_RiskLevel.CRITICAL;
        }

        if (riskScore >= 0.70) {
            return SLA_RiskLevel.HIGH;
        }

        return SLA_RiskLevel.NONE;
    }


    //-----------------For Monitoring Resolution SLA Risk-----------

    @Scheduled(fixedDelay = 60000)
    @SchedulerLock(name="monitorResolution_SLA_Breaching_Risk",lockAtLeastFor = "PT50s",lockAtMostFor = "PT2M")
    public void monitorResolutionRisk(){
        log.info("Starting SLA Resolution Risk Monitoring");
        LocalDateTime BatchLastEntry_ResolutionDeadline=LocalDateTime.now().minusYears(1);
        UUID BatchLastEntry_IncidentId= new UUID(0L,0L);
        List<IncidentReport> incidents;
        LocalDateTime now = LocalDateTime.now();
        // Only Fetching incidents whose deadline is within next 30 mins
        LocalDateTime deadlineWindow = now.plusMinutes(30);
        PageRequest pageRequest=PageRequest.of(0,50);
        do {
            // the cursor values must be final to be used inside the transaction below
            LocalDateTime cursorDeadline = BatchLastEntry_ResolutionDeadline;
            UUID cursorIncidentId = BatchLastEntry_IncidentId;
            // Outbox step 4: ONE transaction for this page (read, change levels, save events + levels)
            incidents = transactionTemplate.execute(status -> {
            List<IncidentReport> modifiedIncidents = new ArrayList<>();

            List<IncidentReport> page = incidentReportRepository
                    .findResolutionSLARiskIncidents(
                            deadlineWindow,
                            cursorDeadline,cursorIncidentId,pageRequest
                    );
            log.info("incidents fetched for Resolution SLA Breaching Risk  → {}",page.size());
            for (IncidentReport incident : page) {

                processResolutionRisk(incident, now,modifiedIncidents);
            }
            // we are only saving the modified incidents only
            if(!modifiedIncidents.isEmpty()){
                incidentReportRepository.saveAll(modifiedIncidents);
            }
            return page;
            });

            //Update the BatchLastEntry_ResolutionDeadline and BatchLastEntry_IncidentId for then next batch as its used in cursor pagination
            if(!incidents.isEmpty()){
                BatchLastEntry_ResolutionDeadline=incidents.getLast().getResolutionDeadline();
                BatchLastEntry_IncidentId=incidents.getLast().getIncidentId();
            }
        } while (!incidents.isEmpty());
    }

    private void processResolutionRisk(
            IncidentReport incident,
            LocalDateTime now,List<IncidentReport> modifiedIncidents
    ) {
        log.info("Enter the processResolutionRisk method ");
        long totalMinutes = Duration.between(
                incident.getCreatedAt(),
                incident.getResolutionDeadline()
        ).toMinutes();

        long elapsedMinutes = Duration.between(
                incident.getCreatedAt(),
                now
        ).toMinutes();
        double riskScore =
                (double) elapsedMinutes / totalMinutes;
        riskScore = Math.min(riskScore, 1.0);
        SLA_RiskLevel newLevel = calculateRiskLevel(riskScore);

        String previousLevel =
                incident.getResolutionRiskLevel().name();
        // Only act if level changed
        if (!newLevel.name().equals(previousLevel)) {
            log.info("as the ResolutionSLA new level {} and old level is {} are different  so we need to publish the event ",newLevel.name(),previousLevel);
            log.info(" also the Calculated ResolutionSLA riskScore={} and newLevel={} for incidentId={} that gets stores in our db as the lisk level changed",riskScore,newLevel.name(),incident.getIncidentId());

            log.info(
                    " ResolutionSLA Risk level changed for incidentId={} from {} to {}",
                    incident.getIncidentId(),
                    previousLevel,
                    newLevel
            );

            incident.setResolutionRiskScore(riskScore);

            incident.setResolutionRiskLevel(newLevel);

            // publish event
            SLA_BreachingRiskEventProducer.publishResolutionSlaBreachingRiskEvent(
                    incident,
                    previousLevel,
                    newLevel.name(),
                    riskScore
            );
            log.info("Published SLA Resolution Breaching Risk Event with New Risk Level {} and Risk Score {} ",newLevel.name(),riskScore);
            // as the RiskLevel and RiskScore got changed for this incident so we need to  save the updated changes for that incident in db
            modifiedIncidents.add(incident);

        }
    }

}

