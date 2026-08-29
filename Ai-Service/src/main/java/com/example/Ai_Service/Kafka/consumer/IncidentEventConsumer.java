package com.example.Ai_Service.Kafka.consumer;

import com.example.Ai_Service.service.AiProcessingService;
import com.example.Ai_Service.service.RagIntegrationService;
import com.project.events.IncidentCreatedEvent;
import com.project.events.IncidentResolutionReportEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IncidentEventConsumer {
    private static final String INCIDENT_CREATED_TOPIC = "incident.created";
    private static final String INCIDENT_ASSIGNED_TOPIC = "incident.assigned";
    private static final String INCIDENT_STATUS_UPDATED_TOPIC = "incident.status.updated";
    private static final String INCIDENT_REPORT_RESOLVED_TOPIC = "Incident-Resolved-Report-Topic";
    private static final Logger log = LoggerFactory.getLogger(IncidentEventConsumer.class);
    private final AiProcessingService aiProcessingService;
    private final RagIntegrationService ragIntegrationService;
    @KafkaListener(groupId ="Ai-service-group",topics = INCIDENT_CREATED_TOPIC)
    public  void ConsumeIncidentCreatedEvent(IncidentCreatedEvent event){
//        hamne abhe ki leye ye band kiya hai ki jab incident create hoga to automaticaly incident classification suggestion from ai ayega beacuse hamm abhi testing kar rahe hai
        log.info("hamne abhi ki leye incident created event consumer ko band kiya hai comment karke jisse hamko abhi ki leya ai classification response  nahi melege  ");
        //    aiProcessingService.processIncidents(event);
    }

    @KafkaListener(groupId ="Ai-service-group",topics = INCIDENT_REPORT_RESOLVED_TOPIC)
    public  void ConsumeIncidentResolvedEvent(IncidentResolutionReportEvent event){
        log.info("Received IncidentResolvedEvent for incidentId: {} and incident Title: {}", event.getIncidentId(),event.getTitle());
        ragIntegrationService.storingResolvedIncidentsInVectorDb(event);
    }


}
