package com.example.ReportingService.kafka.producer;


import com.example.ReportingService.entity.IncidentReport;
import com.example.ReportingService.repository.IncidentReportRepository;
import com.project.events.IncidentResolutionReportEvent;
import com.project.events.IncidentStatusUpdatedEvent;
import com.project.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

// Outbox step 4: this class no longer talks to Kafka. It SAVES the event in the outbox table, inside the
// caller's transaction (the Kafka listener in IncidentEventConsumer). The background relay sends it a moment
// later. So: report saved + event saved, or neither. No transaction here on purpose - the caller owns it.
@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentResolvedReportEventProducer {
    private final OutboxWriter outboxWriter;
    private  final IncidentReportRepository incidentReportRepository;
    private static final String IncidentResolvedReportTopic = "Incident-Resolved-Report-Topic";
    public void publishIncidentResolvedReportAsEvent(IncidentReport incidentReport,IncidentStatusUpdatedEvent incidentStatusUpdatedEvent) {
        long resolutionTimeInSeconds= Duration.between(incidentReport.getCreatedAt(), incidentStatusUpdatedEvent.getResolvedAt()).toSeconds();
        IncidentResolutionReportEvent Event=IncidentResolutionReportEvent.builder()
                .incidentId(incidentStatusUpdatedEvent.getIncidentId())
                .title(incidentStatusUpdatedEvent.getTitle())
                .description(incidentStatusUpdatedEvent.getDescription())
                .category(incidentStatusUpdatedEvent.getCategory())
                .serviceName(incidentStatusUpdatedEvent.getServiceName())
                .severity(incidentReport.getSeverity())
                .priority(incidentReport.getPriority())
                .environment(incidentStatusUpdatedEvent.getEnvironment())
                .rootCause(incidentStatusUpdatedEvent.getRootCause())
                .resolutionNotes(incidentStatusUpdatedEvent.getResolutionNotes())
                .resolutionTimeInSeconds(resolutionTimeInSeconds)
                // Issue 62: Ai-Service recommends engineers from this field, so it must not be a supervisor who pressed the button.
                .resolvedBy(incidentReport.engineerToCredit())
                .assignmentSlaBreached(incidentReport.isAssignmentSlaBreached())
                .resolutionSlaBreached(incidentReport.isResolutionSlaBreached())
                .resolvedAt(incidentStatusUpdatedEvent.getResolvedAt())
                .build();

        // same topic and key (incident id) as before; Ai-Service reads it exactly as it did
        outboxWriter.save(IncidentResolvedReportTopic,incidentStatusUpdatedEvent.getIncidentId().toString(),Event);
}

}
