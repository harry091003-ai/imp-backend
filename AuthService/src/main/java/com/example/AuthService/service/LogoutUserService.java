package com.example.AuthService.service;

import com.example.AuthService.entity.User;
import com.example.AuthService.kafka.producer.UserLoggedOutEventProducer;
import com.example.AuthService.repository.UserRepository;
import com.project.events.UserLoggedInEvent;
import com.project.events.UserLoggedOutAtEvent;
import com.project.web.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class LogoutUserService {
    private static final Logger log = LoggerFactory.getLogger(LogoutUserService.class);

    private final UserRepository userRepository;
private final UserLoggedOutEventProducer userLoggedOutEventProducer;
    public LogoutUserService(UserRepository userRepository,UserLoggedOutEventProducer userLoggedOutEventProducer) {
        this.userRepository = userRepository;
        this.userLoggedOutEventProducer=userLoggedOutEventProducer;
    }
// this method we use when we want to logout the current user
    public void LogoutUser(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        String UserId=auth.getName();
        User user = userRepository.findById(UUID.fromString(UserId))
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + UserId));

        user.setTokenVersion(user.getTokenVersion()+1);
        // update the LastLogout time
        user.setLastLogoutAt(LocalDateTime.now());
       User updatedUser= userRepository.save(user);
        //Using KafkaEvent Producer For Updating the Logged-out Field Of our User in UserService Db
         UserLoggedOutAtEvent event=UserLoggedOutAtEvent.builder()
                 .userid(updatedUser.getUserid())
                 .userLastLoggedOutAt(updatedUser.getLastLogoutAt())
                 .build();
         userLoggedOutEventProducer.sendUserLoggedOutEvent(updatedUser.getUserid(),event);
        // C1 step 1: 3 System.out.println lines in this method (user id, token version, "success")
        // became this ONE line. Token version is a plain counter, fine to log.
        log.debug("User {} logged out, token version is now {}", UserId, updatedUser.getTokenVersion());
    }
    public void LogoutUserByAdmin(UUID userId){

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + userId));
        user.setTokenVersion(user.getTokenVersion()+1);
        // update the LastLogout time
        user.setLastLogoutAt(LocalDateTime.now());
      User updatedUser=  userRepository.save(user);
        //Using KafkaEvent Producer For Updating the Logged-out Field Of our User in UserService Db
        UserLoggedOutAtEvent event=UserLoggedOutAtEvent.builder()
                .userid(updatedUser.getUserid())
                .userLastLoggedOutAt(updatedUser.getLastLogoutAt())
                .build();
        userLoggedOutEventProducer.sendUserLoggedOutEvent(updatedUser.getUserid(),event);
        // C1 step 1: was System.out.println with the username. Trimmed to the user id.
        log.debug("User {} logged out by an admin, token version is now {}", userId,
                updatedUser.getTokenVersion());
    }
}
