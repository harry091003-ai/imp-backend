package com.example.ReportingService.kafka.consumer;

import com.example.ReportingService.config.SLAConfiguration;
import com.example.ReportingService.entity.EngineerWorkload;
import com.example.ReportingService.entity.IncidentAuditLog;
import com.example.ReportingService.entity.IncidentReport;
import com.example.ReportingService.entity.IncidentStatus;
import com.example.ReportingService.kafka.producer.IncidentResolvedReportEventProducer;
import com.example.ReportingService.kafka.producer.SLA_BreachedEventProducer;
import com.example.ReportingService.repository.IncidentAuditLogRepository;
import com.example.ReportingService.repository.IncidentReportRepository;
import com.example.ReportingService.repository.ProcessedEventRepository;
import com.example.ReportingService.service.EngineerPerformanceService;
import com.example.ReportingService.service.EngineerWorkloadService;
import com.example.ReportingService.service.IdempotencyService;
import com.project.events.IncidentResolutionReportEvent;
import com.project.events.enumsUsedByEvents.EventType;
import com.project.events.IncidentAssignedEvent;
import com.project.events.IncidentCreatedEvent;
import com.project.events.IncidentStatusUpdatedEvent;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Transactional
@Service
@RequiredArgsConstructor
public class IncidentEventConsumer {

    private static final String INCIDENT_CREATED_TOPIC = "incident.created";
    private static final String INCIDENT_ASSIGNED_TOPIC = "incident.assigned";
    private static final String INCIDENT_STATUS_UPDATED_TOPIC = "incident.status.updated";
    private static final Logger log = LoggerFactory.getLogger(IncidentEventConsumer.class);
    private final IncidentReportRepository incidentReportRepository;
    private final IncidentAuditLogRepository incidentAuditLogRepository;
    private final IncidentResolvedReportEventProducer incidentResolvedReportEventProducer;
    private final EngineerPerformanceService engineerPerformanceService;
private  final IdempotencyService idempotencyService;
private final EngineerWorkloadService engineerWorkloadService;
    @KafkaListener(groupId = "Reporting-service-group",topics =INCIDENT_CREATED_TOPIC)
    public void consumeIncidentCreatedEvent(IncidentCreatedEvent event){
        //First we Check Idempotence
        // if the catch block here throw exception the retry will occurs.
        try{idempotencyService.isDuplicate(event.getEventId());
        }
        catch (DataIntegrityViolationException e) {
            return ;
        }
           //  calculating the deadlines
        LocalDateTime assignmentDeadline=event.getCreatedAt().plusSeconds(SLAConfiguration.getAssignmentSLA_INSeconds(event.getPriority()));
        LocalDateTime resolutionDeadline=event.getCreatedAt().plusSeconds(SLAConfiguration.getResolutionSLA_INSeconds(event.getPriority()));

        // Only executed for NEW events
        IncidentReport incidentReport=IncidentReport.builder()
                .incidentId(event.getIncidentId())
                .title(event.getTitle())
                .description(event.getDescription())
                .severity(event.getSeverity())
                .priority(event.getPriority())
                .createdBy(event.getCreatedBy())
                .status(IncidentStatus.OPEN.name())
                .serviceName(event.getServiceName())
                .category(event.getCategory())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getCreatedAt())
                .assignmentDeadline(assignmentDeadline)
                .resolutionDeadline(resolutionDeadline)
                .build();
        incidentReportRepository.save(incidentReport);
        log.info("incidentReport is saved to Db for IncidentCreatedEvent");


// now also saving the IncidentAuditLog
IncidentAuditLog incidentAuditLog=IncidentAuditLog.builder()
        .incidentId(event.getIncidentId())
        .eventId(event.getEventId())
        .eventType(EventType.INCIDENT_CREATED)
        .performedBy(event.getPerformedBy())
        .oldValue("no Incident With This IncidentId")
        .newValue("Incident created with this IncidentId")
        .timestamp(event.getCreatedAt())
        .build();

        incidentAuditLogRepository.save(incidentAuditLog);
        log.info("incidentAuditLog is saved to Db for IncidentCreatedEvent");

}

@KafkaListener(groupId ="Reporting-service-group",topics =INCIDENT_ASSIGNED_TOPIC)
    public void  consumeIncidentAssignedEvent(IncidentAssignedEvent event){
    //First we Check Idempotence
    // if the catch block here throw exception the retry will occurs.
    try{idempotencyService.isDuplicate(event.getEventId());
    }
    catch (DataIntegrityViolationException e) {
        return ;
    }

    // Only executed for NEW events (new evenid's)
   IncidentReport incidentReport=incidentReportRepository.findById(event.getIncidentId()).orElse(null);
    if (incidentReport == null) {
        log.warn("IncidentReport not found for incidentId={}, skipping event",
                event.getIncidentId());
        return;
    }
    //this AssignedTo value is used Later in incidentAuditLog:
    UUID assignedTo=incidentReport.getAssignedTo();

    incidentReport.setAssignedBy(event.getAssignedBy());
    incidentReport.setAssignedTo(event.getAssignedTo());
    incidentReport.setStatus(IncidentStatus.ASSIGNED.name());
    incidentReport.setAssignedAt(event.getAssignedAt());
    incidentReport.setUpdatedAt(event.getAssignedAt());
    long incidentAssignTime= Duration.between(incidentReport.getCreatedAt(),event.getAssignedAt()).toSeconds();
    incidentReport.setTimeToAssignSeconds(incidentAssignTime);
    //for checking and setting value of AssignmentSlaBreached
     long assignmentSLA_TimeLimit= SLAConfiguration.getAssignmentSLA_INSeconds(incidentReport.getPriority());
    incidentReport.setAssignmentSlaBreached(incidentAssignTime > assignmentSLA_TimeLimit);


    incidentReportRepository.save(incidentReport);
    log.info("incidentReport is saved to Db for IncidentAssignedEvent");

// now also saving the IncidentAuditLog
    IncidentAuditLog incidentAuditLog=IncidentAuditLog.builder()
            .incidentId(event.getIncidentId())
            .eventId(event.getEventId())
            .eventType(EventType.INCIDENT_ASSIGNED)
            .performedBy(event.getPerformedBy())
            .oldValue(assignedTo == null ? "UNASSIGNED" : "ASSIGNED")
            .newValue("ASSIGNED to " + event.getAssignedTo())
            .timestamp(event.getAssignedAt())
            .build();

    incidentAuditLogRepository.save(incidentAuditLog);
    log.info("incidentAuditLog is saved to Db for IncidentAssignedEvent");

    //now we also updating the Workload on that engineer by using the EngineerWorkloadService
    engineerWorkloadService.refreshAfterAssignment(incidentReport,event.getAssignedTo());
    // Issue 62: on a reassign the old engineer no longer has this incident.
    if (assignedTo != null && !assignedTo.equals(event.getAssignedTo())) {
        engineerWorkloadService.refreshActiveIncidentCount(assignedTo);
    }


}


    @KafkaListener(groupId = "Reporting-service-group",topics = INCIDENT_STATUS_UPDATED_TOPIC)
    public void consumerIncidentStatusUpdateEvent(IncidentStatusUpdatedEvent event)
    {
        //First we Check Idempotence
        // if the catch block here throw exception the retry will occurs.
        try{idempotencyService.isDuplicate(event.getEventId());
        }
        catch (DataIntegrityViolationException e) {
            return ;
        }

        // Only executed for NEW events (new evenid's)
        IncidentReport incidentReport=incidentReportRepository.findById(event.getIncidentId()).orElse(null);
        if (incidentReport == null) {
            log.warn("IncidentReport not found for incidentId={}, skipping event",
                    event.getIncidentId());
            return;
        }
        incidentReport.setStatus(event.getUpdatedStatus());
        incidentReport.setUpdatedAt(event.getUpdatedAt());
        // if the incident is resolved:
        if(event.getUpdatedStatus().equals("RESOLVED")){
            incidentReport.setResolvedAt(event.getUpdatedAt());
            long incidentResolvedTime=Duration.between(incidentReport.getCreatedAt(), incidentReport.getResolvedAt()).toSeconds();
            incidentReport.setResolutionTimeSeconds(incidentResolvedTime);
            incidentReport.setResolvedBy(event.getUpdatedBy());
            //checking and setting value of ResolutionSlaBreached
            long resolutionSLA_TimeLimit=SLAConfiguration.getResolutionSLA_INSeconds(incidentReport.getPriority());
            incidentReport.setResolutionSlaBreached(incidentResolvedTime>resolutionSLA_TimeLimit);
            //save update to db
            incidentReportRepository.save(incidentReport);

            // as Incident Is resolved so we need to update/insert the IncidentUpdateStats
            engineerPerformanceService.updateEngineerStats(incidentReport);

            //Now we produce the Incident Resolved Report Event that is consumed by Ai-Service as now we can see all required info collected from incident service and also that we calculated here together to ai service.
            //Outbox step 4: this now SAVES the event in the outbox, inside this listener's transaction (the
            //@Transactional on this class). If anything below fails, the event row rolls back with the report.
            incidentResolvedReportEventProducer.publishIncidentResolvedReportAsEvent(incidentReport,event);
        }
        incidentReportRepository.save(incidentReport);
        log.info("incidentReport is saved to Db for IncidentAssignedEvent");

        // Issue 62: any status change can change the engineer's count (RESOLVED, CLOSED, REOPENED), and it is the
        // ASSIGNED engineer's count, not the count of whoever pressed the button.
        if (incidentReport.getAssignedTo() != null) {
            engineerWorkloadService.refreshActiveIncidentCount(incidentReport.getAssignedTo());
        }


// now also saving the IncidentAuditLog
        IncidentAuditLog incidentAuditLog=IncidentAuditLog.builder()
                .incidentId(event.getIncidentId())
                .eventId(event.getEventId())
                .eventType(EventType.INCIDENT_STATUS_UPDATED)
                .performedBy(event.getPerformedBy())
                .oldValue(event.getOldStatus())
                .newValue(event.getUpdatedStatus())
                .timestamp(event.getUpdatedAt())
                .build();
        incidentAuditLogRepository.save(incidentAuditLog);
        log.info("incidentAuditLog is saved to Db for IncidentStatusUpdateEvent");

    }
}


