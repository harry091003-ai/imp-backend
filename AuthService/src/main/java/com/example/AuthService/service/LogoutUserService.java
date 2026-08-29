package com.example.AuthService.service;

import com.example.AuthService.entity.User;
import com.example.AuthService.kafka.producer.UserLoggedOutEventProducer;
import com.example.AuthService.repository.UserRepository;
import com.project.events.UserLoggedInEvent;
import com.project.events.UserLoggedOutAtEvent;
import com.project.web.exception.NotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class LogoutUserService {
    private final UserRepository userRepository;
private final UserLoggedOutEventProducer userLoggedOutEventProducer;
    public LogoutUserService(UserRepository userRepository,UserLoggedOutEventProducer userLoggedOutEventProducer) {
        this.userRepository = userRepository;
        this.userLoggedOutEventProducer=userLoggedOutEventProducer;
    }
// this method we use when we want to logout the current user
    public void LogoutUser(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("Userid is: " + auth.getName());

        String UserId=auth.getName();
        User user = userRepository.findById(UUID.fromString(UserId))
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + UserId));

        user.setTokenVersion(user.getTokenVersion()+1);
        System.out.println("User token version is : " + user.getTokenVersion());
        // update the LastLogout time
        user.setLastLogoutAt(LocalDateTime.now());
       User updatedUser= userRepository.save(user);
        //Using KafkaEvent Producer For Updating the Logged-out Field Of our User in UserService Db
         UserLoggedOutAtEvent event=UserLoggedOutAtEvent.builder()
                 .userid(updatedUser.getUserid())
                 .userLastLoggedOutAt(updatedUser.getLastLogoutAt())
                 .build();
         userLoggedOutEventProducer.sendUserLoggedOutEvent(updatedUser.getUserid(),event);
        System.out.println("Token Version Updated sucessfully so u are successfully logged out " );
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
        System.out.println("admin sucessfully update Token Version Updated  of" + user.getUsername()+" so user is  successfully logged out  " );
    }
}
