package com.example.ReportingService.kafka.producer;

import com.example.ReportingService.entity.IncidentReport;
import com.project.events.SLA_BreachedEvent;
import com.project.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// Outbox step 4: saves the event in the outbox table instead of sending it. The caller (SLA_BreachedSchedular)
// runs this inside the SAME transaction that sets "event published = true" on the report. So the flag and the
// event are saved together, or not at all (this closes known issue 22). The relay sends it a moment later.
@Service
@RequiredArgsConstructor
@Slf4j
public class SLA_BreachedEventProducer {
    private final OutboxWriter outboxWriter;
    private static final String SLA_AssignmentBreachedTopic = "Sla-Assignment-Breach-Topic";
    private static final String SLA_ResolutionBreachedTopic = "Sla-Resolution-Breach-Topic";

    public void publishAssignmentBreachedEvent(IncidentReport incident) {
        SLA_BreachedEvent event = SLA_BreachedEvent.builder()
                .incidentId(incident.getIncidentId())
                .SLA_BreachedType("ASSIGNMENT_SLA_BREACHED")
                // SLA notifications step 2: who to tell (assignedTo is null here - nobody is assigned yet)
                .createdBy(incident.getCreatedBy())
                .assignedTo(incident.getAssignedTo())
                .build();
        outboxWriter.save(SLA_AssignmentBreachedTopic, incident.getIncidentId().toString(), event);
        log.info("Saved Assignment SLA breach event to the outbox for {}", incident.getIncidentId());
    }

    public void publishResolutionBreachedEvent(IncidentReport incident) {
        SLA_BreachedEvent event=SLA_BreachedEvent.builder()
                .incidentId(incident.getIncidentId())
                .SLA_BreachedType("RESOLUTION_SLA_BREACHED")
                // SLA notifications step 2: who to tell
                .createdBy(incident.getCreatedBy())
                .assignedTo(incident.getAssignedTo())
                .build();
        outboxWriter.save(SLA_ResolutionBreachedTopic,incident.getIncidentId().toString(),event);
        log.info("Saved Resolution SLA breach event to the outbox for {}", incident.getIncidentId());
    }
}
