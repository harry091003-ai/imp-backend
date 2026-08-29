package com.example.AuthService.kafka.producer;

import com.project.events.UserLoggedInEvent;
import com.project.events.UserLoggedOutAtEvent;
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
public class UserLoggedOutEventProducer {
    //for using logs
    private static final Logger log= LoggerFactory.getLogger(UserLoggedOutEventProducer.class);
    private final KafkaTemplate<String, UserLoggedOutAtEvent> kafkaTemplate;
    private static final String Topic="user.logged-out";
    public void sendUserLoggedOutEvent(UUID userId, UserLoggedOutAtEvent event){
        kafkaTemplate.send(Topic,userId.toString(),event);
        log.info("Published UserLoggedOutEvent for userId={}", userId);
    }
}
