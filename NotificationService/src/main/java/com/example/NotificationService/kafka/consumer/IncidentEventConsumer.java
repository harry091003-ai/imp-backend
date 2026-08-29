package com.example.NotificationService.kafka.consumer;

import com.example.NotificationService.Entity.EventType;
import com.example.NotificationService.Entity.NotificationLog;
import com.example.NotificationService.Entity.NotificationStatus;
import com.example.NotificationService.client.UserClient;
import com.example.NotificationService.dto.UserCredentialDTO;
import com.example.NotificationService.repository.NotificationLogRepository;
import com.example.NotificationService.service.EmailNoticationService;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IncidentEventConsumer {
    private final UserClient userClient;
//    private final NotificationLogRepository notificationLogRepository;
    private final EmailNoticationService emailNoticationService;
    private static final String INCIDENT_CREATED_TOPIC = "incident.created";
    private static final String INCIDENT_ASSIGNED_TOPIC = "incident.assigned";
    private static final String INCIDENT_STATUS_UPDATED_TOPIC = "incident.status.updated";
    private static final Logger log = LoggerFactory.getLogger(IncidentEventConsumer.class);
    @KafkaListener(topics =INCIDENT_CREATED_TOPIC,groupId = "notification-service-group")
    public void consumeIncidentCreatedEvent(IncidentCreatedEvent event){

        log.info("Received Incident Created Event: {}", event);

        // Here you can add logic to send notifications based on the incident creation event in future!!
        // so firstly we just get the email details of the who created the incident from the userservice using webclient:
        //we only want to send the email to the incident creator;
        // so we first add that incident creator in list because our batch method in incident client is taking list of user id and then we will call the batch method to get the email details of the creator of the incident from user service
        List<UUID> userid= List.of(event.getCreatedBy());
      List<UserCredentialDTO> usersTosendEmail=  userClient.getBatchOfUsers_Credentials(userid);
        log.info("Hi the incident Creation Event is Consumed and the Users to whom that Details need to be shared are follows :{}", usersTosendEmail);

      // for storing the logs of Each User to whom we want to send the email in future, we first store in Database.
        emailNoticationService.storeNotificationLog(event.getIncidentId(),event.getEventId(),event.getPerformedBy(),usersTosendEmail, EventType.INCIDENT_CREATED);

    }




    @KafkaListener(topics =INCIDENT_ASSIGNED_TOPIC,groupId ="notification-service-group")
    public void consumeIncidentAssignedEvent(IncidentAssignedEvent event) {
        log.info(" the Incident Assigned Event : {}", event);
        List<UUID> userid= List.of(event.getCreatedBy(),event.getAssignedBy(),event.getAssignedTo());
        List<UserCredentialDTO> usersTosendEmail=  userClient.getBatchOfUsers_Credentials(userid);
        log.info("Hi the incident is Assigned and the Users to whome that Details to be shared are :{}", usersTosendEmail);
        // for storing the logs of Each User to whom we want to send the email in future, we first store in Database.
        emailNoticationService.storeNotificationLog(event.getIncidentId(),event.getEventId(),event.getPerformedBy(),usersTosendEmail,EventType.INCIDENT_ASSIGNED);

    }

    @KafkaListener(topics =INCIDENT_STATUS_UPDATED_TOPIC,groupId = "notification-service-group")
    public void consumeIncidentStatusUpdatedEvent(IncidentStatusUpdatedEvent event) {
        log.info("Received Incident status Updated Event: {}", event);
        List<UUID>userId=new ArrayList<>();
        userId.add(event.getCreatedBy());
        if(event.getAssignedBy()!=null){userId.add(event.getAssignedBy());}
        if(event.getAssignedTo()!=null){userId.add(event.getAssignedTo());}

        List<UserCredentialDTO> usersTosendEmail=  userClient.getBatchOfUsers_Credentials(userId);

        // Here you can add logic to send notifications based on the incident Updated event in future!!
        log.info("Hi the incident Status is Updated and the Users to whom that Details to be shared are :{}", usersTosendEmail);
       emailNoticationService.storeNotificationLog(event.getIncidentId(),event.getEventId(),event.getPerformedBy(),usersTosendEmail,EventType.INCIDENT_STATUS_UPDATED);
    }

}


