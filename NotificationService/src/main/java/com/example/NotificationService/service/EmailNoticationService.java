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

    public void storeNotificationLog(UUID incidentId, UUID eventId, UUID performedby, List<UserCredentialDTO> usersToSendEmail, EventType eventType){
        // for storing the logs of Each User to whom we want to send the email in future, we first store in Database.
        for(UserCredentialDTO user:usersToSendEmail){


            // before sending the email
            NotificationLog UserNotificationlog=NotificationLog.builder()
                    .incidentId(incidentId)
                    .eventId(eventId)
                    .eventType(eventType)
                    .performedBy(performedby)
                    .recipientEmail(user.getEmail())
                    .recipientName(user.getUsername())
                    .createdAt(LocalDateTime.now())
                    .status(NotificationStatus.PENDING)
                    .build();
            try{
                //1.first send the mail(that we done in future)

                //2. after successfully sending the email we will update the status.

                UserNotificationlog.setStatus(NotificationStatus.SUCCESS);
                UserNotificationlog.setSentAt(LocalDateTime.now());
                UserNotificationlog.setMessage("Email Successfully sent to "+user.getEmail()+" for Incident Created Event with Incident Id: "+incidentId);
                UserNotificationlog.setFailureReason("No Failure");
                log.info("Email Successfully sent to {} for {} Event with Incident Id: {}", user.getEmail(), eventType,incidentId);
            }
            catch (Exception e){
                //3 if in try block we get any exception while sending the email then we will catch block
                UserNotificationlog.setStatus(NotificationStatus.FAILED);
                UserNotificationlog.setFailureReason(e.getMessage());
                UserNotificationlog.setMessage("Failed to send Email to "+user.getEmail()+" for "+ eventType + " Event with Incident Id: "+incidentId+" due to "+e.getMessage());
                log.info("Failed to send Email to {} for {} Event with Incident Id: {} due to {}", user.getEmail(),eventType, incidentId, e.getMessage());
            }
            //Since in kafka  multiple threads system : as multiple consumer at same time can consume the same event and try to save the same event for same user in database and if we do not handle that then we will get duplicate entry in database and also we will send multiple email to same user for same event which is not good for user experience .
            // A. so to avoid this we will use the unique constraint on the combination of eventId and recipientEmail in database level so that if multiple threads try to insert the same event for same user then only one thread will succeed and other thread will get exception and we can handle that exception to avoid sending email again for same event to same user.
            //B.  so we can use the try-catch
            // 1.  to handle that exception greasily
            // 2. also to avoid the retry entire function of kafka consumer because of this businesses issue
            try{
                notificationLogRepository.save(UserNotificationlog);
            }
            catch(DataIntegrityViolationException ex) {
                log.warn("Duplicate event ignored for eventId={} email={}",
                        eventId,user.getEmail());
            }
        }
    }

}
