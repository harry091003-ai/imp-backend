package com.example.AuthService.kafka.producer;

import com.project.events.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor  // means all the required field automatically gets Injected via constructor like here kafkaTemplate
public class UserRegisteredEventProducer {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredEventProducer.class);
    //  we define this kafkaTemplate field so that we use it in our class, also we add the additional thinks <key,value>
    //  here value :UserRegisteredEvent , means we can only send this (UserRegisteredEvent) event object
//    similarly for key:string that we only use the string type key.
//    although we are only using value here not using key,but must use key as it helps in Partitioning (VERY IMPORTANT)
//    without using <k,v>  any can be added so that crash the consumers
    private final KafkaTemplate<String, UserRegisteredEvent> kafkaTemplate;

    private static final String TOPIC = "user.registered";


    public void publishUserRegisteredEvent(UserRegisteredEvent event) {
        kafkaTemplate.send(TOPIC, event);
        // C1 step 1: was System.out.println("... Published Successfully ...").
        // Reworded: send() only HANDS the event to Kafka. Kafka confirms later, and could still fail.
        log.debug("UserRegisteredEvent handed to Kafka, topic {}, userId {}", TOPIC, event.getUserid());
    }
}
