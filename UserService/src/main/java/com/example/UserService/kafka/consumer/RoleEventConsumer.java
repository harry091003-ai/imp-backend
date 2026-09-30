package com.example.UserService.kafka.consumer;


import com.example.UserService.entity.Role;
import com.example.UserService.entity.RoleName;
import com.example.UserService.entity.User;
import com.example.UserService.repository.RoleRepository;
import com.example.UserService.repository.UserRepository;
import com.project.events.UpdatingRoleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RoleEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RoleEventConsumer.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    public RoleEventConsumer(RoleRepository role_repository, UserRepository user_Repository){
        roleRepository=role_repository;
        userRepository=user_Repository;
    }

    @KafkaListener(topics ="roles.update",groupId ="user-service-group" )
    public void UpdatingRoles(UpdatingRoleEvent event){

        String username= event.getUsername();
        //        Fetching User From Db
        User user = userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException(" User Not found"));
        Set<Role> roles = event.getRole()
                .stream()
                .map(RoleName::valueOf)//converts String Rolename from client to EnumRolename
                .map(roleEnum -> roleRepository.findByRolename(roleEnum)
                        .orElseThrow(() -> new RuntimeException("Role not found: " + roleEnum)))
                .collect(Collectors.toSet());
//        assigning the new roles to User
        user.setRoles(roles);
        userRepository.save(user);
        // C1 step 1: was 2 System.out.println lines (the Role objects "for just checking", then
        // "success"). Now one line: the user id and the role names.
        log.debug("Roles updated for user {}: {}", user.getUserid(), event.getRole());
    }
}
