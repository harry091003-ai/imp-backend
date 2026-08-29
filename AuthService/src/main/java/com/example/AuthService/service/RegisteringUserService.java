package com.example.AuthService.service;

import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.RoleName;
import com.example.AuthService.entity.User;
import com.example.AuthService.kafka.producer.UserRegisteredEventProducer;
import com.example.AuthService.repository.RoleRepository;
import com.example.AuthService.repository.UserRepository;
import com.project.events.UserRegisteredEvent;
import com.project.web.exception.ConflictException;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Transactional
@Service
public class RegisteringUserService {
private final PasswordEncoder passwordEncoder;
private final RoleRepository roleRepository;
private final UserRepository userRepository;
private final UserRegisteredEventProducer userRegisteredEventProducer;
//constructor
public RegisteringUserService(PasswordEncoder password_encoder, RoleRepository role_repository, UserRepository user_repository, UserRegisteredEventProducer user_EventProducer){
    passwordEncoder=password_encoder;
    roleRepository=role_repository;
    userRepository=user_repository;
    userRegisteredEventProducer =user_EventProducer;
}

    public void RegisteringTheUser(User user){

        // before doing anything we first checks user  is the user Already Exit??
        if(userRepository.existsByUsername(user.getUsername())){
            throw new ConflictException("USERNAME_TAKEN", "That username is already taken.");
        }

        if(userRepository.existsByEmail(user.getEmail())){
            throw new ConflictException("EMAIL_TAKEN", "That email is already registered.");
        }


//    Encode the password
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setLastLoginAt(null);
        user.setPasswordUpdatedAt(LocalDateTime.now());
        user.setFailedLoginAttempts(0);
        user.setAccountLocked(false);
        user.setEnabled(true);
// we will not make a new role object everytime a new user register as that was not the good approach , ao we are just fetching a role object from Db.
        // Deliberately left as a plain RuntimeException -> handled as a 500.
        // If ROLE_ENGINEER is missing from the roles table, that is OUR seed-data problem,
        // not something the caller did wrong. A 4xx here would blame the wrong side.
        Role role= roleRepository.findByRolename(RoleName.ROLE_ENGINEER).orElseThrow(()->new RuntimeException("role Does not Exist"));


       //make the user know the role
        System.out.println(user.getRoles());
//        if(user.getRoles()==null){}
        user.getRoles().add(role);

           userRepository.save(user);



//        After saving the user we will publish the event to kafka topic so for this we are

        //first: create the event  object
        UserRegisteredEvent event= UserRegisteredEvent.builder()
                .userid(user.getUserid())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(user.getRoles().stream().map(role1 ->role.getRolename().name()).collect(Collectors.toSet()))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .passwordUpdatedAt(user.getPasswordUpdatedAt())
                .build();

//       System.out.println("Before Publishing UserRegisteredEvent to Kafka: " + event);
    // second: publish the event to kafka topic using the  UserEventproducer that made
        userRegisteredEventProducer.publishUserRegisteredEvent(event);

    }


}
//passwordEncoder