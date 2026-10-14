package com.example.AuthService.kafka.producer;

import com.project.events.UpdatingUserEvent;
import com.project.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// CHANGED 2026-10-13 (Outbox step 3): saves the event in the outbox instead of calling Kafka.
// Same topic, same key (the user id). Must run inside a transaction (UpdateCredentialsService has one).
//for  username and email
@Service
@RequiredArgsConstructor
public class UserUpdatedEventProducer {
public static final Logger log= LoggerFactory.getLogger(UserUpdatedEventProducer.class);

private final OutboxWriter outboxWriter;
    private static final String TOPIC = "user.Updated";

public void sendUserUpdatedEvent(UpdatingUserEvent event){
    outboxWriter.save(TOPIC,event.getUserId().toString(),event);
    log.debug("UserUpdated event saved to the outbox for userId={}", event.getUserId());
}
}
