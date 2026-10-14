package com.example.AuthService.kafka.producer;

import com.project.events.UserRegisteredEvent;
import com.project.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// CHANGED 2026-10-13 (Outbox step 3): this class no longer talks to Kafka.
//
// BEFORE: kafkaTemplate.send(...) INSIDE RegisteringUserService's @Transactional method, before the
//         commit. Two things could go wrong: the send worked but the commit failed (a GHOST user
//         event for a user that does not exist), or the commit worked but the send failed (a LOST
//         event: UserService never learns about the user, and that person can never create an incident).
// NOW:    outboxWriter.save(...) puts the event in the outbox_event table, in the SAME transaction
//         as the user row. common-outbox's relay sends it to Kafka about a second later.
//
// The Kafka KEY is now the user id. Before, this event had NO key. A key keeps all events of one
// user in order (user.registered always before that user's roles.update).
// These methods MUST run inside a transaction: outboxWriter.save refuses otherwise, on purpose.
@Service
@RequiredArgsConstructor
public class UserRegisteredEventProducer {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredEventProducer.class);

    private final OutboxWriter outboxWriter;
    private static final String TOPIC = "user.registered";

    public void publishUserRegisteredEvent(UserRegisteredEvent event) {
        outboxWriter.save(TOPIC, event.getUserid().toString(), event);
        log.debug("UserRegisteredEvent saved to the outbox, topic {}, userId {}", TOPIC, event.getUserid());
    }
}
