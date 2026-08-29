package com.example.IncidentService.kafka.producer;


import com.project.events.IncidentAssignedEvent;
import com.project.events.IncidentCreatedEvent;
import com.project.events.IncidentStatusUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IncidentEventProducer {
private final KafkaTemplate<String,Object> kafkaTemplate;
private static final String INCIDENT_CREATED_TOPIC = "incident.created";
    private static final String INCIDENT_ASSIGNED_TOPIC = "incident.assigned";
    private static final String INCIDENT_STATUS_UPDATED_TOPIC = "incident.status.updated";
    private static final String INCIDENT_RESOLVED_TOPIC = "incident.resolved";
    public void PublishIncidentCreated(String incidentId, IncidentCreatedEvent event){
        kafkaTemplate.send(INCIDENT_CREATED_TOPIC,incidentId,event);
    }
    public void PublishIncidentAssigned(String incidentId, IncidentAssignedEvent event)
    {
        kafkaTemplate.send(INCIDENT_ASSIGNED_TOPIC,incidentId,event);
    }

    public void PublishIncidentStatusUpdated(String incidentId, IncidentStatusUpdatedEvent event){
        kafkaTemplate.send(INCIDENT_STATUS_UPDATED_TOPIC,incidentId,event);
    }

//    public void PublishIncidentResolved(String incidentId, IncidentResolvedEvent event){
//        kafkaTemplate.send(INCIDENT_RESOLVED_TOPIC,incidentId,event);
//    }
}
