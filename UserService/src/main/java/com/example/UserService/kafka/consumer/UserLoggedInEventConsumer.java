package com.example.UserService.kafka.consumer;
//import com.example.AuthService.kafka.producer.UserLoggedInEventProducer;
import com.example.UserService.entity.User;
import com.example.UserService.repository.UserRepository;
import com.project.events.UserLoggedInEvent;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class UserLoggedInEventConsumer {
    //for using logs
    private static final Logger log= LoggerFactory.getLogger(UserLoggedInEventConsumer.class);

    private final UserRepository userRepository;
@KafkaListener(topics ="user.logged-in",groupId = "user-service-group")
    public void LoggedInEvent(UserLoggedInEvent event){
    User user = userRepository.findById(event.getUserid()).orElse(null);

    if (user == null) {
        log.warn("User not found for userId={}, skipping event", event.getUserid());
        return;
    }
        user.setLastLoginAt(event.getUserLastLoggedInAt());
        userRepository.save(user);
       log.info("Consumed UserLoggedInEvent for userId={}", event.getUserid());
    }
}
