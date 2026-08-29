package com.example.AuthService.kafka.producer;

import com.project.events.UpdatingUserEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

//for  username and email
@Service
@RequiredArgsConstructor
public class UserUpdatedEventProducer {
public static final Logger log= LoggerFactory.getLogger(UserUpdatedEventProducer.class);

private final KafkaTemplate<String, UpdatingUserEvent> kafkaTemplate;
    private static final String TOPIC = "user.Updated";
public void sendUserUpdatedEvent(UpdatingUserEvent event){
    kafkaTemplate.send(TOPIC,event.getUserId().toString(),event);
    log.info("UserUpdate event is successfully send from UserUpdatedEventProducer inside Authservice ");
}

}
