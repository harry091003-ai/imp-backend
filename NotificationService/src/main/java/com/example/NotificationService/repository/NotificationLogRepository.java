package com.example.NotificationService.repository;

import com.example.NotificationService.Entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
@Repository
public interface NotificationLogRepository extends JpaRepository< NotificationLog,UUID> {

   boolean existsByEventIdAndRecipientEmail(UUID eventId,String ReceptentEmail);
}
