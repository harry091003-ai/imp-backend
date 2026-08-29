package com.example.UserService.kafka.consumer;

import com.example.UserService.entity.RoleName;
import com.example.UserService.entity.Status;
import com.example.UserService.entity.User;
import com.example.UserService.repository.RoleRepository;
import com.example.UserService.repository.UserRepository;
import com.project.events.UpdatingUserEvent;
import com.project.events.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserEventConsumer {
private final RoleRepository roleRepository;
private final UserRepository userRepository;
public UserEventConsumer(RoleRepository role_repository, UserRepository user_Repository){
    roleRepository=role_repository;
    userRepository=user_Repository;
}
    @KafkaListener(topics = "user.registered",groupId ="user-service-group" )
    private void consumeUserRegisteredEvent(UserRegisteredEvent userRegistered_InAuthService) {
System.out.println("Received UserRegisteredEvent from Kafka Topic: userRegistered");
        if(userRepository.existsByUsername(userRegistered_InAuthService.getUsername())){
            log.warn("Username already exists, skipping event");
            return;
        }

        if(userRepository.existsByEmail(userRegistered_InAuthService.getEmail())){
            log.warn("Email already exists, skipping event");
            return;
        }
        User user = User.builder()
                .userid(userRegistered_InAuthService.getUserid())
                .username(userRegistered_InAuthService.getUsername())
                .email(userRegistered_InAuthService.getEmail())
                .roles(userRegistered_InAuthService.getRoles().
                        stream().map(role -> roleRepository.findByRolename(RoleName.valueOf(role)).orElseThrow(()->new RuntimeException("Role Not Found:" + role)))
                        .collect(java.util.stream.Collectors.toSet()))
                .createdAt(userRegistered_InAuthService.getCreatedAt())
                .updatedAt(userRegistered_InAuthService.getUpdatedAt())
                .lastLoginAt(userRegistered_InAuthService.getLastLoginAt())
                .passwordUpdatedAt(userRegistered_InAuthService.getPasswordUpdatedAt())
                .status(Status.INACTIVE)
                .build();
        System.out.println("User Created inside The UserService from the UserRegisteredEvent: " + user);
        userRepository.save(user);
        System.out.println("Sucessfully save the User in the UserService Database");
    }

   @KafkaListener(topics="user.Updated",groupId ="user-service-group")
    private void consumeUserUpdatedEvent(UpdatingUserEvent updatingUserEvent){
    User user=userRepository.findById(updatingUserEvent.getUserId()).orElseThrow(()->new RuntimeException("User not found"));

       if (updatingUserEvent.getUpdatedEmail() != null) {
           user.setEmail(updatingUserEvent.getUpdatedEmail());
       }

       // Update Username field
       if (updatingUserEvent.getUpdatedUserName() != null) {
           user.setUsername(updatingUserEvent.getUpdatedUserName());
       }
       userRepository.save(user);
       System.out.println("User Details Updated Successfully in our UserService ");
   }
}
