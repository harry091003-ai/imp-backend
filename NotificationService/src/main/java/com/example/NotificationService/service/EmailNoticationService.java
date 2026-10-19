package com.example.NotificationService.service;

import com.example.NotificationService.Entity.EventType;
import com.example.NotificationService.Entity.NotificationLog;
import com.example.NotificationService.Entity.NotificationStatus;
import com.example.NotificationService.dto.UserCredentialDTO;
import com.example.NotificationService.repository.NotificationLogRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class EmailNoticationService {
    private final NotificationLogRepository notificationLogRepository;
     private final static Logger log= LoggerFactory.getLogger(EmailNoticationService.class);
    // function: For storing the logs of Each User to whom we want to send the email in future,in Database.
    // also make sure to handle Single and multi thread issues.

    // Step 6: the caller gives the message, because only it knows what happened.
    public void storeNotificationLog(UUID incidentId, UUID eventId, UUID performedby, List<UserCredentialDTO> usersToSendEmail, EventType eventType,
                                     String message){
        // for storing the logs of Each User to whom we want to send the email in future, we first store in Database.
//        if(true){ throw new  RuntimeException("This is a test exception to check the retry mechanism in kafka consumer for incident created event");}
        for(UserCredentialDTO user:usersToSendEmail){


            // Step 6: an honest row.
            // - recipientId: so "my notifications" can find it
            // - RECORDED: saved, no email sent (sentAt stays empty)
            // - the message comes from the caller, without an email address
            // Real email sending will go here later.
            NotificationLog UserNotificationlog=NotificationLog.builder()
                    .incidentId(incidentId)
                    .eventId(eventId)
                    .eventType(eventType)
                    .performedBy(performedby)
                    .recipientId(user.getUserid())
                    .recipientEmail(user.getEmail())
                    .recipientName(user.getUsername())
                    .createdAt(LocalDateTime.now())
                    .status(NotificationStatus.RECORDED)
                    .message(message)
                    .build();

            //Since in kafka  multiple threads system : as multiple consumer at same time can consume the same event and try to save the same event for same user in database and if we do not handle that then we will get duplicate entry in database and also we will send multiple email to same user for same event which is not good for user experience .
            // A. so to avoid this we will use the unique constraint on the combination of eventId and recipientEmail in database level so that if multiple threads try to insert the same event for same user then only one thread will succeed and other thread will get exception and we can handle that exception to avoid sending email again for same event to same user.
            //B.  so we can use the try-catch
            // 1.  to handle that exception greasily
            // 2. also to avoid the retry entire function of kafka consumer because of this businesses issue
            try{
                notificationLogRepository.save(UserNotificationlog);
                // user id only in the log, never the email (Track C1 rule). Honest wording: nothing was sent.
                log.info("Notification {} recorded for user {} (incident {}) - no email sent", eventType, user.getUserid(), incidentId);
            }
            catch(DataIntegrityViolationException ex) {
                log.warn("Duplicate event ignored for eventId={} user={}",
                        eventId,user.getUserid());
            }
        }
    }

}
