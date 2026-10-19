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

        // C1 step 1b: this file used to log the WHOLE event and the WHOLE recipient list.
        // Both are Lombok @Data, so that printed every field: incident title, description,
        // root cause (up to 2000 chars each), and every recipient's username and email.
        // Now only ids and a count. Still INFO: once per event, and it is how you see the
        // Kafka side working.
        log.info("Received incident.created event {} for incident {}", event.getEventId(), event.getIncidentId());

        // Here you can add logic to send notifications based on the incident creation event in future!!
        // so firstly we just get the email details of the who created the incident from the userservice using webclient:
        //we only want to send the email to the incident creator;
        // so we first add that incident creator in list because our batch method in incident client is taking list of user id and then we will call the batch method to get the email details of the creator of the incident from user service
        List<UUID> userid= List.of(event.getCreatedBy());
      List<UserCredentialDTO> usersTosendEmail=  userClient.getBatchOfUsers_Credentials(userid);
        log.info("Incident {}: {} recipient(s), user ids {}", event.getIncidentId(), usersTosendEmail.size(),
                recipientIds(usersTosendEmail));
      // for storing the logs of Each User to whom we want to send the email in future, we first store in Database.
        emailNoticationService.storeNotificationLog(event.getIncidentId(),event.getEventId(),event.getPerformedBy(),usersTosendEmail, EventType.INCIDENT_CREATED,
                "Incident " + event.getIncidentId() + " was created.");

    }




    @KafkaListener(topics =INCIDENT_ASSIGNED_TOPIC,groupId ="notification-service-group")
    public void consumeIncidentAssignedEvent(IncidentAssignedEvent event) {
        log.info("Received incident.assigned event {} for incident {}", event.getEventId(), event.getIncidentId());
        List<UUID> userid= List.of(event.getCreatedBy(),event.getAssignedBy(),event.getAssignedTo());
        List<UserCredentialDTO> usersTosendEmail=  userClient.getBatchOfUsers_Credentials(userid);
        log.info("Incident {}: {} recipient(s), user ids {}", event.getIncidentId(), usersTosendEmail.size(),
                recipientIds(usersTosendEmail));
        // for storing the logs of Each User to whom we want to send the email in future, we first store in Database.
        emailNoticationService.storeNotificationLog(event.getIncidentId(),event.getEventId(),event.getPerformedBy(),usersTosendEmail,EventType.INCIDENT_ASSIGNED,
                "Incident " + event.getIncidentId() + " was assigned.");

    }

    @KafkaListener(topics =INCIDENT_STATUS_UPDATED_TOPIC,groupId = "notification-service-group")
    public void consumeIncidentStatusUpdatedEvent(IncidentStatusUpdatedEvent event) {
        log.info("Received incident.status.updated event {} for incident {}", event.getEventId(), event.getIncidentId());
        List<UUID>userId=new ArrayList<>();
        userId.add(event.getCreatedBy());
        if(event.getAssignedBy()!=null){userId.add(event.getAssignedBy());}
        if(event.getAssignedTo()!=null){userId.add(event.getAssignedTo());}

        List<UserCredentialDTO> usersTosendEmail=  userClient.getBatchOfUsers_Credentials(userId);

        // Here you can add logic to send notifications based on the incident Updated event in future!!
        log.info("Incident {}: {} recipient(s), user ids {}", event.getIncidentId(), usersTosendEmail.size(),
                recipientIds(usersTosendEmail));
       emailNoticationService.storeNotificationLog(event.getIncidentId(),event.getEventId(),event.getPerformedBy(),usersTosendEmail,EventType.INCIDENT_STATUS_UPDATED,
                "Incident " + event.getIncidentId() + ": status changed from " + event.getOldStatus()
                        + " to " + event.getUpdatedStatus() + ".");
    }

    // Just the ids of the recipients - never their emails or usernames.
    private static List<UUID> recipientIds(List<UserCredentialDTO> users) {
        return users.stream().map(UserCredentialDTO::getUserid).toList();
    }

}


