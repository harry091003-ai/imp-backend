package com.example.NotificationService.service;

import com.example.NotificationService.Entity.NotificationLog;
import com.example.NotificationService.dto.MyNotificationsResponseDTO;
import com.example.NotificationService.dto.NotificationItemDTO;
import com.example.NotificationService.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Reads one person's notifications, newest first, one page at a time (step 7).
 * Old rows saved before step 6 have no recipient_id, so they never show up.
 */
@Service
@RequiredArgsConstructor
public class MyNotificationsService {

    private final NotificationLogRepository notificationLogRepository;

    public MyNotificationsResponseDTO getMyNotifications(UUID userId, int page, int size) {
        Page<NotificationLog> found = notificationLogRepository
                .findByRecipientIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        return MyNotificationsResponseDTO.builder()
                .total(found.getTotalElements())
                .page(page)
                .size(size)
                .items(found.getContent().stream().map(MyNotificationsService::toItem).toList())
                .build();
    }

    private static NotificationItemDTO toItem(NotificationLog row) {
        return NotificationItemDTO.builder()
                .id(row.getId())
                .incidentId(row.getIncidentId())
                .type(row.getEventType() == null ? null : row.getEventType().name())
                .status(row.getStatus() == null ? null : row.getStatus().name())
                .message(row.getMessage())
                .createdAt(row.getCreatedAt())
                .build();
    }
}
