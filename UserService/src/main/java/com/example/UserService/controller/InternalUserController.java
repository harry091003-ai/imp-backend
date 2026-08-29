package com.example.UserService.controller;

import com.example.UserService.dto.*;
import com.example.UserService.entity.RoleName;
import com.example.UserService.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
@RequestMapping("/internal")
@RestController
public class InternalUserController {

    private final UserService service;
    public InternalUserController(UserService userservice) {
        service = userservice;
    }



    //----------------------------------------------internal api's For Interservice Communication -----------------------------
    @GetMapping("/validation/{userid}")
    public ResponseEntity<UserValidationDTO> LimitedUserInfo(@PathVariable UUID userid) {
        UserValidationDTO userValidationDTO = service.LimitedUserInfoForValidation(userid);
        return ResponseEntity.ok(userValidationDTO);
    }

    @GetMapping("/EngineersUnderSupervisor/{SupervisorId}")
    public ResponseEntity<List<UUID>> getEngineersUnderSupervisor(@PathVariable UUID SupervisorId) {
        List<UserResponseDTO> engineers = service.getEngineersUnderSupervisor(SupervisorId);
        return ResponseEntity.ok(engineers.stream().map(UserResponseDTO::getUserid).toList());
    }

    // for getting the UserCredential(mainly Email: used by Notification service for sending email)using Synchronous Interservice Communication
    @GetMapping("/UserSpecificDetails/{userId}")
    public ResponseEntity<UserCredentialDTO>getOwnSpecificDetails(@PathVariable UUID userid){

        UserCredentialDTO userCredentialDTO=service.GetUserCredentials(userid);

        return ResponseEntity.ok(userCredentialDTO);
    }
//instead of get we are using post because we are sending list of userids in the request body and get request does not support request body.
    @PostMapping("/UserSpecificDetails/batch")
    public ResponseEntity<List<UserCredentialDTO>>getBatchOfUsers_SpecificDetails(@RequestBody List<UUID> userIds){

        List<UserCredentialDTO> userCredentialDTOList=service.GetBatchOfUsers_Credentials(userIds);
        return ResponseEntity.ok(userCredentialDTOList);
     }



}

