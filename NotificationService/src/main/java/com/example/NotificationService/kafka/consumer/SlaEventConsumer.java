package com.example.NotificationService.kafka.consumer;

import com.example.NotificationService.Entity.EventType;
import com.example.NotificationService.service.SlaNotificationService;
import com.project.events.SLA_BreachedEvent;
import com.project.events.enumsUsedByEvents.SlaBreachingRiskEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Reads the 4 SLA topics (SLA notifications, step 5).
 * - ReportingService sends them when an incident is late, or about to be late.
 * - This class only reads the message. SlaNotificationService decides who is told.
 * - Event id = the X-Event-Id header from the outbox. It stops a repeat from being saved twice.
 */
@Service
@RequiredArgsConstructor
public class SlaEventConsumer {

    /** The same name as OutboxHeaders.EVENT_ID in common-outbox (that module is not a dependency of this service). */
    static final String EVENT_ID_HEADER = "X-Event-Id";

    // The same topic names ReportingService sends to (SLA_BreachedEventProducer, SLA_BreachingRiskEventProducer).
    static final String ASSIGNMENT_BREACH_TOPIC = "Sla-Assignment-Breach-Topic";
    static final String RESOLUTION_BREACH_TOPIC = "Sla-Resolution-Breach-Topic";
    static final String ASSIGNMENT_RISK_TOPIC = "Sla-Assignment-BreachingRisk-Topic";
    static final String RESOLUTION_RISK_TOPIC = "Sla-Resolution-BreachingRisk-Topic";
    private static final String GROUP = "notification-service-group";

    private final SlaNotificationService slaNotificationService;

    @KafkaListener(topics = ASSIGNMENT_BREACH_TOPIC, groupId = GROUP)
    public void consumeAssignmentBreach(@Payload SLA_BreachedEvent event,
                                        @Header(name = EVENT_ID_HEADER, required = false) byte[] eventIdHeader) {
        slaNotificationService.notifyPeople(
                eventId(eventIdHeader, ASSIGNMENT_BREACH_TOPIC, event.getIncidentId(), ""),
                event.getIncidentId(), event.getCreatedBy(), event.getAssignedTo(), EventType.SLA_ASSIGNMENT_BREACHED,
                "Incident " + event.getIncidentId() + ": assignment SLA breached - it was not assigned in time.");
    }

    @KafkaListener(topics = RESOLUTION_BREACH_TOPIC, groupId = GROUP)
    public void consumeResolutionBreach(@Payload SLA_BreachedEvent event,
                                        @Header(name = EVENT_ID_HEADER, required = false) byte[] eventIdHeader) {
        slaNotificationService.notifyPeople(
                eventId(eventIdHeader, RESOLUTION_BREACH_TOPIC, event.getIncidentId(), ""),
                event.getIncidentId(), event.getCreatedBy(), event.getAssignedTo(), EventType.SLA_RESOLUTION_BREACHED,
                "Incident " + event.getIncidentId() + ": resolution SLA breached - it was not resolved in time.");
    }

    @KafkaListener(topics = ASSIGNMENT_RISK_TOPIC, groupId = GROUP)
    public void consumeAssignmentRisk(@Payload SlaBreachingRiskEvent event,
                                      @Header(name = EVENT_ID_HEADER, required = false) byte[] eventIdHeader) {
        slaNotificationService.notifyPeople(
                eventId(eventIdHeader, ASSIGNMENT_RISK_TOPIC, event.getIncidentId(), event.getCurrentRiskLevel()),
                event.getIncidentId(), event.getCreatedBy(), event.getAssignedTo(), EventType.SLA_ASSIGNMENT_AT_RISK,
                riskMessage(event, "assignment"));
    }

    @KafkaListener(topics = RESOLUTION_RISK_TOPIC, groupId = GROUP)
    public void consumeResolutionRisk(@Payload SlaBreachingRiskEvent event,
                                      @Header(name = EVENT_ID_HEADER, required = false) byte[] eventIdHeader) {
        slaNotificationService.notifyPeople(
                eventId(eventIdHeader, RESOLUTION_RISK_TOPIC, event.getIncidentId(), event.getCurrentRiskLevel()),
                event.getIncidentId(), event.getCreatedBy(), event.getAssignedTo(), EventType.SLA_RESOLUTION_AT_RISK,
                riskMessage(event, "resolution"));
    }

    /**
     * The id that stops a repeat.
     * - Normal case: the X-Event-Id header (Kafka gives headers as bytes; we turn them into a UUID).
     * - No header, or a bad one: a fixed id made from topic + incident + detail. Same message = same id.
     */
    static UUID eventId(byte[] header, String topic, UUID incidentId, String detail) {
        if (header != null) {
            try {
                return UUID.fromString(new String(header, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException notAnId) {
                // fall through: a header that is not an id is treated like a missing one
            }
        }
        return UUID.nameUUIDFromBytes((topic + ":" + incidentId + ":" + detail).getBytes(StandardCharsets.UTF_8));
    }

    /** e.g. "Incident 123...: assignment SLA at risk (HIGH -> CRITICAL, 95% of the time used)." Under 255 chars. */
    static String riskMessage(SlaBreachingRiskEvent event, String kind) {
        String used = event.getRiskScore() == null ? "" : ", " + Math.round(event.getRiskScore() * 100) + "% of the time used";
        return "Incident " + event.getIncidentId() + ": " + kind + " SLA at risk (" + event.getPreviousRiskLevel()
                + " -> " + event.getCurrentRiskLevel() + used + ").";
    }
}
