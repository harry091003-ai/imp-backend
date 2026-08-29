package com.example.ReportingService.kafka.producer;


import com.example.ReportingService.entity.IncidentReport;
import com.example.ReportingService.repository.IncidentReportRepository;
import com.project.events.IncidentResolutionReportEvent;
import com.project.events.IncidentStatusUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentResolvedReportEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;
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
                .rootCause(incidentStatusUpdatedEvent.getRootCause())
                .resolutionNotes(incidentStatusUpdatedEvent.getResolutionNotes())
                .resolutionTimeInSeconds(resolutionTimeInSeconds)
                .resolvedBy(incidentStatusUpdatedEvent.getPerformedBy())
                .assignmentSlaBreached(incidentReport.isAssignmentSlaBreached())
                .resolutionSlaBreached(incidentReport.isResolutionSlaBreached())
                .resolvedAt(incidentStatusUpdatedEvent.getResolvedAt())
                .build();

        kafkaTemplate.send(IncidentResolvedReportTopic,incidentStatusUpdatedEvent.getIncidentId().toString(),Event);
}

}
