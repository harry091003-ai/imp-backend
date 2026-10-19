package com.example.NotificationService.repository;

import com.example.NotificationService.Entity.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
@Repository
public interface NotificationLogRepository extends JpaRepository< NotificationLog,UUID> {

   boolean existsByEventIdAndRecipientEmail(UUID eventId,String ReceptentEmail);

   // SLA notifications step 7: GET /notifications/me - one person's rows, newest first, one page at a time.
   // Uses the index (recipient_id, created_at) that Flyway V2 creates.
   Page<NotificationLog> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);
}
