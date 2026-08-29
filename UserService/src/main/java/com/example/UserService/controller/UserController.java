package com.example.UserService.controller;

import com.example.UserService.dto.*;
import com.example.UserService.entity.RoleName;
import com.example.UserService.entity.User;
import com.example.UserService.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;


import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService service;

    public UserController(UserService userservice) {
        service = userservice;
    }


    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERVISOR')")
    @GetMapping("/engineers")
    public ResponseEntity<List<UserResponseDTO>> getAllEngineers() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("User: " + auth.getName());
        System.out.println("Authorities: " + auth.getAuthorities());
        return ResponseEntity.ok(service.GetRoleSpecifiedUsers(RoleName.ROLE_ENGINEER));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/supervisors")
    public ResponseEntity<List<UserResponseDTO>> getAllSupervisors() {
        return ResponseEntity.ok(service.GetRoleSpecifiedUsers(RoleName.ROLE_SUPERVISOR));
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERVISOR')")
    @GetMapping("/{id}/engineer")
    public ResponseEntity<UserResponseDTO> getEngineerById(@PathVariable UUID id) {
        UserResponseDTO userResponseDTO = service.GetRoleSpecifiedUserById(RoleName.ROLE_ENGINEER, id);
        return ResponseEntity.ok(userResponseDTO);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}/supervisor")
    public ResponseEntity<UserResponseDTO> getSupervisorById(@PathVariable UUID id) {

        UserResponseDTO userResponseDTO = service.GetRoleSpecifiedUserById(RoleName.ROLE_SUPERVISOR, id);
        return ResponseEntity.ok(userResponseDTO);
    }


    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getOwnDetails(Authentication authentication) {
        UserResponseDTO UserDto = service.GetOwnDetails(authentication);
        return ResponseEntity.ok(UserDto);
    }

    @PreAuthorize("hasRole('ADMIN')or hasRole('SUPERVISOR')")
    @PutMapping("/{id}/statusEngineer")
    public ResponseEntity<StatusResponseDTO> updateEngineerStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequestDTO statusRequestDTO, Authentication auth) {
        StatusResponseDTO responseDTO = service.updateEngineerStatus(id, statusRequestDTO, auth);
        return (ResponseEntity.ok(responseDTO));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/statusSupervisor")
    public ResponseEntity<StatusResponseDTO> updateSupervisorStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequestDTO statusRequestDTO) {
        StatusResponseDTO responseDTO = service.updateSupervisorStatus(id, statusRequestDTO);
        return (ResponseEntity.ok(responseDTO));
    }


    // assigning or reassigning engineer to supervisor by admin
    //here reassigning means if  engineer has already a assigned  supervisor then we will first remove that supervisor  then reassign it to the new supervisor
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/engineer/supervisor")
    public ResponseEntity<MessageDTO> assignSupervisorToEngineer(@Valid @RequestBody AssignEngineerRequestDTO request) {
        MessageDTO message = service.assignOrReassignSupervisor(request.getEngineerId(), request.getSupervisorId());
        return ResponseEntity.ok(message);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/engineer/{id}/supervisor")
    public ResponseEntity<MessageDTO> removeAssignedSupervisorFromEngineer(@PathVariable UUID id) {
        MessageDTO message = service.removeSupervisor(id);
        return ResponseEntity.ok(message);
    }

    //for Getting supervisor of Engineer
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/engineer/{id}/supervisor")
    public ResponseEntity<SupervisorResponseDTO> getSupervisorOfThisEnginer(@PathVariable UUID id) {
        SupervisorResponseDTO supervisor = service.getSupervisorOfEngineer(id);
        return ResponseEntity.ok(supervisor);
    }

    //for Getting  All the Engineers Under this supervisor
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/supervisor/{id}/engineers")
    public ResponseEntity<List<UserResponseDTO>> getEngineersUnderThisSupervisor(@PathVariable UUID id) {
        List<UserResponseDTO> engineers = service.getEngineersUnderSupervisor(id);
        return ResponseEntity.ok(engineers);
    }

//search user using email ,username and get results based on user searching role in pageable format
    @GetMapping("/search")
    public ResponseEntity<Page<UserSearchResponseDTO>> searchUsers(
            @RequestParam String query,
            Pageable pageable,
            Authentication authentication
    ) {
        Page<UserSearchResponseDTO> response = service.search_Users(query, pageable, authentication);
        return ResponseEntity.ok(response);

    }
//----------------------------------------------For Interservice Communication-----------------------------
//    @GetMapping("/validation/{userid}")
//    public ResponseEntity<UserValidationDTO> LimitedUserInfo(@PathVariable UUID userid) {
//        UserValidationDTO userValidationDTO = service.LimitedUserInfoForValidation(userid);
//        return ResponseEntity.ok(userValidationDTO);
//    }
//
//    @PreAuthorize("hasRole('SUPERVISOR')")
//    @GetMapping("/EngineersUnderSupervisor/{SupervisorId}")
//    public ResponseEntity<List<UUID>> getEngineersUnderSupervisor(@PathVariable UUID SupervisorId) {
//        List<UserResponseDTO> engineers = service.getEngineersUnderSupervisor(SupervisorId);
//        return ResponseEntity.ok(engineers.stream().map(UserResponseDTO::getUserid).toList());
//    }
//@GetMapping("/SpecificDetails/{userId}")
//public ResponseEntity<UserCredentialDTO>getOwnSpecificDetails(@PathVariable UUID userid){
//
//    UserCredentialDTO userCredentialDTO=service.GetUserCredentials(userid);
//
//    return ResponseEntity.ok(userCredentialDTO);
//}

}