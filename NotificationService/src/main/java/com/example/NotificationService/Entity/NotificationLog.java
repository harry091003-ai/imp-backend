package com.example.NotificationService.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Entity
@Table( uniqueConstraints = @UniqueConstraint(
        columnNames = {"eventId", "recipientEmail"}))// this apply the unique constraint on the combination of eventId and recipientEmail, ensuring that each recipient receives only one notification per event.
public class NotificationLog {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID incidentId;
    private UUID performedBy;// to know who performed the action.
    @Column(nullable = false)
    private UUID eventId;// this will be a unique identifier for each event.

    @Enumerated(EnumType.STRING)
    private EventType eventType;

    private String recipientEmail;

    private String recipientName;

    @Enumerated(EnumType.STRING)
    private NotificationStatus status;

    private String message;

    private String failureReason;

    private LocalDateTime createdAt;

    private LocalDateTime sentAt;
}
