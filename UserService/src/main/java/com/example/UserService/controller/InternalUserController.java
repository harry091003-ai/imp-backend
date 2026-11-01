package com.example.UserService.controller;

import com.example.UserService.dto.*;
import com.example.UserService.entity.RoleName;
import com.example.UserService.service.UserService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    // AI plan phase 1: Ai-Service checks all its candidate engineers in one call. An unknown id is simply not in the answer.
    @PostMapping("/validation/batch")
    public ResponseEntity<List<UserValidationDTO>> LimitedUsersInfo(
            @RequestBody @Size(max = 50, message = "At most 50 user ids per request") List<@NotNull UUID> userIds) {
        return ResponseEntity.ok(service.LimitedUsersInfoForValidation(userIds));
    }

    @GetMapping("/EngineersUnderSupervisor/{SupervisorId}")
    public ResponseEntity<List<UUID>> getEngineersUnderSupervisor(@PathVariable UUID SupervisorId) {
        List<UserResponseDTO> engineers = service.getEngineersUnderSupervisor(SupervisorId);
        return ResponseEntity.ok(engineers.stream().map(UserResponseDTO::getUserid).toList());
    }

    // for getting the UserCredential(mainly Email: used by Notification service for sending email)using Synchronous Interservice Communication
    // Issue 2 (2026-10-28): the URL says {userId} but the parameter is called userid, so Spring could never fill it
    // (always a 500). Naming the URL part fixes it without renaming the parameter.
    @GetMapping("/UserSpecificDetails/{userId}")
    public ResponseEntity<UserCredentialDTO>getOwnSpecificDetails(@PathVariable("userId") UUID userid){

        UserCredentialDTO userCredentialDTO=service.GetUserCredentials(userid);

        return ResponseEntity.ok(userCredentialDTO);
    }
//instead of get we are using post because we are sending list of userids in the request body and get request does not support request body.
    // B4 step 5: at most 50 ids, and no null ids.
    // The only caller (NotificationService) sends 1 to 3. Without a cap, one request
    // could ask for every user in the table in one go.
    @PostMapping("/UserSpecificDetails/batch")
    public ResponseEntity<List<UserCredentialDTO>>getBatchOfUsers_SpecificDetails(
            @RequestBody @Size(max = 50, message = "At most 50 user ids per request") List<@NotNull UUID> userIds){

        List<UserCredentialDTO> userCredentialDTOList=service.GetBatchOfUsers_Credentials(userIds);
        return ResponseEntity.ok(userCredentialDTOList);
     }



}

