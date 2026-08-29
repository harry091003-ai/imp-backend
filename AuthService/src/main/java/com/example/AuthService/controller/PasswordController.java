package com.example.AuthService.controller;

import com.example.AuthService.dto.ChangePasswordDTO;
import com.example.AuthService.dto.SignupResponseDTO;
import com.example.AuthService.service.ChangePasswordService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/password")
public class PasswordController {

private final ChangePasswordService service;

    public PasswordController( ChangePasswordService service) {
        this.service = service;
    }

    @PostMapping("/update")
    public String  UpdatingUserCredentials( @Valid @RequestBody ChangePasswordDTO request,Authentication auth){
        System.out.println("User: " + auth.getName());
    String UserId=auth.getName();
    service.changePassword(UserId,request);
    return "Password changed successfully";

    }
}
