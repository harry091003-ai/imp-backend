package com.example.Ai_Service.Kafka.consumer;

import com.example.Ai_Service.service.ClassificationService;
import com.example.Ai_Service.service.RagIntegrationService;
import com.project.events.IncidentCreatedEvent;
import com.project.events.IncidentResolutionReportEvent;
import com.project.events.IncidentStatusUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IncidentEventConsumer {
    private static final String INCIDENT_CREATED_TOPIC = "incident.created";
    private static final String INCIDENT_STATUS_UPDATED_TOPIC = "incident.status.updated";
    private static final String INCIDENT_REPORT_RESOLVED_TOPIC = "Incident-Resolved-Report-Topic";
    private static final Logger log = LoggerFactory.getLogger(IncidentEventConsumer.class);
    private final ClassificationService classificationService;
    private final RagIntegrationService ragIntegrationService;
    @KafkaListener(groupId ="Ai-service-group",topics = INCIDENT_CREATED_TOPIC)
    public  void ConsumeIncidentCreatedEvent(IncidentCreatedEvent event){
        // AI plan phase 3: no Gemini call here. It compares the suggestion from POST /ai/classify with what the user chose.
        classificationService.compareWithWhatWasSubmitted(event);
    }

    // AI plan phase 2: a reopened incident's solution did not work, so it leaves the memory until it is resolved again.
    @KafkaListener(groupId ="Ai-service-group",topics = INCIDENT_STATUS_UPDATED_TOPIC)
    public void ConsumeIncidentStatusUpdatedEvent(IncidentStatusUpdatedEvent event){
        if ("REOPENED".equals(event.getUpdatedStatus())) {
            ragIntegrationService.removeFromMemory(event.getIncidentId());
        }
    }

    @KafkaListener(groupId ="Ai-service-group",topics = INCIDENT_REPORT_RESOLVED_TOPIC)
    public  void ConsumeIncidentResolvedEvent(IncidentResolutionReportEvent event){
        log.info("Received IncidentResolvedEvent for incidentId: {} and incident Title: {}", event.getIncidentId(),event.getTitle());
        ragIntegrationService.storingResolvedIncidentsInVectorDb(event);
    }


}
