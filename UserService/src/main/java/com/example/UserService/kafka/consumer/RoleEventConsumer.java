package com.example.UserService.kafka.consumer;


import com.example.UserService.entity.Role;
import com.example.UserService.entity.RoleName;
import com.example.UserService.entity.User;
import com.example.UserService.repository.RoleRepository;
import com.example.UserService.repository.UserRepository;
import com.project.events.UpdatingRoleEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class RoleEventConsumer {

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
        System.out.println("roles  fetching from db for just checking" + roles);
//        assigning the new roles to User
        user.setRoles(roles);
        userRepository.save(user);
        System.out.println("Role is successfully updated to our User-Microservice");
    }
}
