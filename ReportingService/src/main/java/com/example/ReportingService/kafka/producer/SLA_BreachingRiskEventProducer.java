package com.example.ReportingService.kafka.producer;

import com.example.ReportingService.entity.IncidentReport;
import com.project.events.enumsUsedByEvents.SlaBreachingRiskEvent;
import com.project.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// Outbox step 4: saves the event in the outbox table instead of sending it. The caller (SLA_BreachingRiskSchedular)
// runs this inside the SAME transaction that saves the new risk level. So "level changed" and "event about it"
// are saved together, or not at all. The relay sends it a moment later.
@Service
@RequiredArgsConstructor
@Slf4j
public class SLA_BreachingRiskEventProducer {
    private final OutboxWriter outboxWriter;
    private static final String SLA_AssignmentBreachingRiskTopic = "Sla-Assignment-BreachingRisk-Topic";
    private static final String SLA_ResolutionBreachingRiskTopic = "Sla-Resolution-BreachingRisk-Topic";

    public void publishAssignmentSlaBreachingRiskEvent(IncidentReport report,String PreviousRiskLevel,String NewRiskLevel,Double Riskscore){

        SlaBreachingRiskEvent event=SlaBreachingRiskEvent.builder()
                .incidentId(report.getIncidentId())
                .previousRiskLevel(PreviousRiskLevel)
                .currentRiskLevel(NewRiskLevel)
                .riskScore(Riskscore)
                .type("ASSIGNMENT_SLA_BREACHING_RISK")
                .build();
log.info("Saving Assignment SLA Breaching Risk Event to the outbox");
        outboxWriter.save(SLA_AssignmentBreachingRiskTopic,report.getIncidentId().toString(),event);
    }
    public void publishResolutionSlaBreachingRiskEvent(IncidentReport report,String PreviousRiskLevel,String NewRiskLevel,Double Riskscore){

        SlaBreachingRiskEvent event=SlaBreachingRiskEvent.builder()
                .incidentId(report.getIncidentId())
                .previousRiskLevel(PreviousRiskLevel)
                .currentRiskLevel(NewRiskLevel)
                .riskScore(Riskscore)
                .type("RESOLUTION_SLA_BREACHING_RISK")
                .build();
        log.info("Saving Resolution SLA Breaching Risk Event to the outbox");
        outboxWriter.save(SLA_ResolutionBreachingRiskTopic,report.getIncidentId().toString(),event);
    }


}
