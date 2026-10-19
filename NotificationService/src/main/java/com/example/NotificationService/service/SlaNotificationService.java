package com.example.NotificationService.service;

import com.example.NotificationService.Entity.EventType;
import com.example.NotificationService.Entity.NotificationLog;
import com.example.NotificationService.Entity.NotificationStatus;
import com.example.NotificationService.client.UserClient;
import com.example.NotificationService.dto.UserCredentialDTO;
import com.example.NotificationService.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decides who is told about an SLA alert, and saves one row per person (step 5).
 * - Who: the creator, the assigned engineer (if any), and the supervisor of the engineer
 *   (or of the creator, when nobody is assigned yet). Each person once.
 * - Order: ask UserService first (no database transaction open), then save.
 * - No email yet: each row is RECORDED, plus one log line with the user id only.
 * - UserService down? The error goes back to Kafka, which retries. A retry is safe: repeats are skipped.
 */
@Service
@RequiredArgsConstructor
public class SlaNotificationService {
    private static final Logger log = LoggerFactory.getLogger(SlaNotificationService.class);

    private final UserClient userClient;
    private final NotificationLogRepository notificationLogRepository;

    public void notifyPeople(UUID eventId, UUID incidentId, UUID createdBy, UUID assignedTo, EventType type, String message) {
        if (createdBy == null) {
            // A message sent before the SLA events carried createdBy/assignedTo (step 2). Nobody to tell.
            log.warn("SLA event {} for incident {} names nobody (an old message?) - nothing recorded", type, incidentId);
            return;
        }

        // 1. The creator and the engineer, in one call to UserService.
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(createdBy);
        if (assignedTo != null) {
            ids.add(assignedTo);
        }
        Map<UUID, UserCredentialDTO> people = new LinkedHashMap<>();
        addAll(people, userClient.getBatchOfUsers_Credentials(new ArrayList<>(ids)));

        // 2. The supervisor: of the engineer, or of the creator when nobody is assigned.
        UserCredentialDTO whoseSupervisor = people.get(assignedTo != null ? assignedTo : createdBy);
        UUID supervisorId = whoseSupervisor == null ? null : whoseSupervisor.getSupervisorId();
        if (supervisorId != null && !people.containsKey(supervisorId)) {
            addAll(people, userClient.getBatchOfUsers_Credentials(List.of(supervisorId)));
        }

        // 3. One row + one log line per person (the map holds each person once).
        for (UserCredentialDTO person : people.values()) {
            record(eventId, incidentId, type, message, person);
        }
    }

    private static void addAll(Map<UUID, UserCredentialDTO> people, List<UserCredentialDTO> found) {
        for (UserCredentialDTO user : found) {
            people.putIfAbsent(user.getUserid(), user);
        }
    }

    private void record(UUID eventId, UUID incidentId, EventType type, String message, UserCredentialDTO person) {
        // The inbox: the same message for the same person is saved once (Kafka may deliver a message again).
        if (notificationLogRepository.existsByEventIdAndRecipientEmail(eventId, person.getEmail())) {
            log.info("SLA notification {} for user {} already recorded - repeated message skipped", type, person.getUserid());
            return;
        }
        NotificationLog row = NotificationLog.builder()
                .eventId(eventId)
                .incidentId(incidentId)
                .eventType(type)
                .recipientId(person.getUserid())
                .recipientEmail(person.getEmail())
                .recipientName(person.getUsername())
                .status(NotificationStatus.RECORDED)
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();   // sentAt stays empty: nothing was sent
        try {
            notificationLogRepository.save(row);
            log.info("SLA notification {} recorded for user {} (incident {}) - no email sent", type, person.getUserid(), incidentId);
        } catch (DataIntegrityViolationException alreadyThere) {
            // The database's own guard (unique event_id + recipient_email) - a second copy at the very same moment.
            log.warn("SLA notification {} for user {} was already saved - duplicate ignored", type, person.getUserid());
        }
    }
}
