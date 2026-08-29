package com.example.UserService.kafka.consumer;

import com.example.UserService.entity.User;
import com.example.UserService.repository.UserRepository;
import com.project.events.UserLoggedInEvent;
import com.project.events.UserLoggedOutAtEvent;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class UserLoggedOutEventConsumer {
    private static final Logger log= LoggerFactory.getLogger(UserLoggedOutEventConsumer.class);
    private final UserRepository userRepository;
    @KafkaListener(topics ="user.logged-out",groupId = "user-service-group")
    public void LoggedOutEvent(UserLoggedOutAtEvent event) {
        User user = userRepository.findById(event.getUserid()).orElse(null);
        if (user == null) {
            log.warn("User not found for userId={}, skipping event", event.getUserid());
            return;
        }
        user.setLastLogoutAt(event.getUserLastLoggedOutAt());
        userRepository.save(user);
        log.info("Consumed UserLoggedOutEvent for userId={}", event.getUserid());
    }
    }
