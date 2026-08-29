package com.example.ReportingService.kafka.producer;

import com.example.ReportingService.entity.IncidentReport;
import com.project.events.SLA_BreachedEvent;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SLA_BreachedEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String SLA_AssignmentBreachedTopic = "Sla-Assignment-Breach-Topic";
    private static final String SLA_ResolutionBreachedTopic = "Sla-Resolution-Breach-Topic";

    public void publishAssignmentBreachedEvent(IncidentReport incident) {
        SLA_BreachedEvent event = SLA_BreachedEvent.builder()
                .incidentId(incident.getIncidentId())
                .SLA_BreachedType("ASSIGNMENT_SLA_BREACHED").build();
        kafkaTemplate.send(SLA_AssignmentBreachedTopic, incident.getIncidentId().toString(), event);
        log.info("Published Assignment SLA breach event for {}", incident.getIncidentId());
    }

    public void publishResolutionBreachedEvent(IncidentReport incident) {
        SLA_BreachedEvent event=SLA_BreachedEvent.builder()
                .incidentId(incident.getIncidentId())
                .SLA_BreachedType("RESOLUTION_SLA_BREACHED")
                .build();
        kafkaTemplate.send(SLA_ResolutionBreachedTopic,incident.getIncidentId().toString(),event);
        log.info("Published Resolution SLA breach event for {}", incident.getIncidentId());
    }
}
