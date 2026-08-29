package com.example.ReportingService.kafka.producer;

import com.example.ReportingService.entity.IncidentReport;
import com.project.events.enumsUsedByEvents.SlaBreachingRiskEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SLA_BreachingRiskEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;
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
log.info("Publishing Assignment SLA Breaching Risk Event");
        kafkaTemplate.send(SLA_AssignmentBreachingRiskTopic,report.getIncidentId().toString(),event);
    }
    public void publishResolutionSlaBreachingRiskEvent(IncidentReport report,String PreviousRiskLevel,String NewRiskLevel,Double Riskscore){

        SlaBreachingRiskEvent event=SlaBreachingRiskEvent.builder()
                .incidentId(report.getIncidentId())
                .previousRiskLevel(PreviousRiskLevel)
                .currentRiskLevel(NewRiskLevel)
                .riskScore(Riskscore)
                .type("RESOLUTION_SLA_BREACHING_RISK")
                .build();
        log.info("Publishing Resolution SLA Breaching Risk Event");
        kafkaTemplate.send(SLA_ResolutionBreachingRiskTopic,report.getIncidentId().toString(),event);
    }


}
