package com.example.AuthService.kafka.producer;

import com.project.events.UserLoggedInEvent;
import com.project.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

// CHANGED 2026-10-13 (Outbox step 3): saves the event in the outbox instead of calling Kafka.
//
// IMPORTANT: the class-level @Transactional that used to be here is REMOVED, on purpose.
// With the outbox, the event row must be saved in the SAME transaction as the data. A @Transactional on
// THIS class would quietly start a SEPARATE little transaction just around the outbox insert whenever
// the caller had none - and the guarantee "both saved or neither" would be gone without any error.
// Now, if a caller has no transaction, outboxWriter.save refuses loudly instead.
@Service
@RequiredArgsConstructor
public class UserLoggedInEventProducer {
    //for using logs
    private static final Logger log= LoggerFactory.getLogger(UserLoggedInEventProducer.class);

    private final OutboxWriter outboxWriter;
    private static final String Topic="user.logged-in";

    public void sendUserLoggedInEvent(UUID userId,UserLoggedInEvent event){
        outboxWriter.save(Topic,userId.toString(),event);
        log.debug("UserLoggedInEvent saved to the outbox for userId={}", userId);
    }
}
