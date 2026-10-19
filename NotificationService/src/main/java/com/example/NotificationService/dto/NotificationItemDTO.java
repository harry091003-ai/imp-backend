package com.example.NotificationService.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One notification, as the user sees it (step 7).
 * No email, name or event id on purpose: only what happened, about which incident, and when.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NotificationItemDTO {
    private UUID id;
    private UUID incidentId;
    private String type;        // e.g. SLA_ASSIGNMENT_BREACHED, INCIDENT_ASSIGNED
    private String status;      // RECORDED today (no email is sent yet)
    private String message;
    private LocalDateTime createdAt;
}
