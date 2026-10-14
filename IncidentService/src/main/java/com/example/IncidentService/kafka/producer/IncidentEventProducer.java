package com.example.IncidentService.kafka.producer;


import com.project.events.IncidentAssignedEvent;
import com.project.events.IncidentCreatedEvent;
import com.project.events.IncidentStatusUpdatedEvent;
import com.project.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// CHANGED 2026-10-13 (Outbox step 2): this class no longer talks to Kafka.
//
// BEFORE: kafkaTemplate.send(...) right after the incident was saved. A crash (or Kafka being down)
//         between "saved" and "sent" lost the event for good, and nobody noticed.
// NOW:    outboxWriter.save(...) puts the event in the outbox_event table, in the SAME database
//         transaction as the incident. common-outbox's relay sends it to Kafka about a second later.
//
// So these methods MUST be called inside a transaction (IncidentService does that with
// transactionTemplate). Outside one, outboxWriter.save refuses with an error - on purpose.
// Same method names, same topics, same keys, same event objects: the consumers see no difference.
@Service
@RequiredArgsConstructor
public class IncidentEventProducer {
private final OutboxWriter outboxWriter;
private static final String INCIDENT_CREATED_TOPIC = "incident.created";
    private static final String INCIDENT_ASSIGNED_TOPIC = "incident.assigned";
    private static final String INCIDENT_STATUS_UPDATED_TOPIC = "incident.status.updated";
    private static final String INCIDENT_RESOLVED_TOPIC = "incident.resolved";
    public void PublishIncidentCreated(String incidentId, IncidentCreatedEvent event){
        outboxWriter.save(INCIDENT_CREATED_TOPIC,incidentId,event);
    }
    public void PublishIncidentAssigned(String incidentId, IncidentAssignedEvent event)
    {
        outboxWriter.save(INCIDENT_ASSIGNED_TOPIC,incidentId,event);
    }

    public void PublishIncidentStatusUpdated(String incidentId, IncidentStatusUpdatedEvent event){
        outboxWriter.save(INCIDENT_STATUS_UPDATED_TOPIC,incidentId,event);
    }

//    public void PublishIncidentResolved(String incidentId, IncidentResolvedEvent event){
//        kafkaTemplate.send(INCIDENT_RESOLVED_TOPIC,incidentId,event);
//    }
}
