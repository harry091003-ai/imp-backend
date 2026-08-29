package com.example.AuthService.kafka.producer;

import com.project.events.UserLoggedInEvent;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
@Service
@RequiredArgsConstructor
@Transactional
public class UserLoggedInEventProducer {
    //for using logs
    private static final Logger log= LoggerFactory.getLogger(UserLoggedInEventProducer.class);

    private final KafkaTemplate<String, UserLoggedInEvent> kafkaTemplate;
    private static final String Topic="user.logged-in";
    public void sendUserLoggedInEvent(UUID userId,UserLoggedInEvent event){
        kafkaTemplate.send(Topic,userId.toString(),event);
        log.info("Published UserLoggedInEvent for userId={}", userId);
    }

}
