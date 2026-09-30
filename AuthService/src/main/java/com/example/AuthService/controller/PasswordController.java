package com.example.AuthService.controller;

import com.example.AuthService.dto.ChangePasswordDTO;
import com.example.AuthService.dto.SignupResponseDTO;
import com.example.AuthService.service.ChangePasswordService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/password")
public class PasswordController {

    private static final Logger log = LoggerFactory.getLogger(PasswordController.class);

private final ChangePasswordService service;

    public PasswordController( ChangePasswordService service) {
        this.service = service;
    }

    @PostMapping("/update")
    public String  UpdatingUserCredentials( @Valid @RequestBody ChangePasswordDTO request,Authentication auth){
        // C1 step 1: was System.out.println. The user id is fine to log; debug keeps it quiet.
        log.debug("Password change requested by user {}", auth.getName());
    String UserId=auth.getName();
    service.changePassword(UserId,request);
    return "Password changed successfully";

    }
}
