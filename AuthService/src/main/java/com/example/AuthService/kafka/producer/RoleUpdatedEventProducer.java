package com.example.AuthService.kafka.producer;

import com.project.events.UpdatingRoleEvent;
import com.project.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

// CHANGED 2026-10-13 (Outbox step 3): saves the event in the outbox instead of calling Kafka.
//
// NEW PARAMETER: userId. This event used to be sent with NO Kafka key. The event itself only holds the
// username, so the user id is passed in separately and used as the key. Now all events of one user
// (registered, logged-in, logged-out, updated, roles) share a key and stay in order.
// The event object itself did not change, so UserService reads it exactly as before.
// Must run inside a transaction (UpdateRoleService has one around the whole change).
@Service
@RequiredArgsConstructor
public class RoleUpdatedEventProducer {

    private static final Logger log = LoggerFactory.getLogger(RoleUpdatedEventProducer.class);

    private final OutboxWriter outboxWriter;
    private static final String TOPIC = "roles.update";

    public void publishUpdatedRoleEvent(UUID userId, UpdatingRoleEvent event) {
        outboxWriter.save(TOPIC, userId.toString(), event);
        log.debug("UpdatingRoleEvent saved to the outbox, topic {}, userId {}", TOPIC, userId);
    }
}
