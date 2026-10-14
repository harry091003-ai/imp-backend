package com.example.AuthService.kafka.producer;

import com.project.events.UserLoggedOutAtEvent;
import com.project.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

// CHANGED 2026-10-13 (Outbox step 3): saves the event in the outbox instead of calling Kafka.
//
// The class-level @Transactional that used to be here is REMOVED, on purpose (same reason as
// UserLoggedInEventProducer): it would start a SEPARATE transaction just around the outbox insert and
// silently break "the data and its event are saved together". The CALLER owns the transaction.
@Service
@RequiredArgsConstructor
public class UserLoggedOutEventProducer {
    //for using logs
    private static final Logger log= LoggerFactory.getLogger(UserLoggedOutEventProducer.class);

    private final OutboxWriter outboxWriter;
    private static final String Topic="user.logged-out";

    public void sendUserLoggedOutEvent(UUID userId, UserLoggedOutAtEvent event){
        outboxWriter.save(Topic,userId.toString(),event);
        log.debug("UserLoggedOutEvent saved to the outbox for userId={}", userId);
    }
}
