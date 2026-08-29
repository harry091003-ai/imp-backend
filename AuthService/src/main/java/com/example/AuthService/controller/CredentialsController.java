package com.example.AuthService.controller;

import com.example.AuthService.dto.UpdateCredentialResponseDTO;
import com.example.AuthService.dto.UpdateCredentialsRequestDTO;
import com.example.AuthService.service.UpdateCredentialsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/credentials")
public class CredentialsController {
private final UpdateCredentialsService service;

    public CredentialsController(UpdateCredentialsService service) {
        this.service = service;
    }
//updating username and email
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody @Valid UpdateCredentialsRequestDTO request, Authentication auth
    ) {
        String userId = auth.getName();

         return  service.UpdateCredentials(request,userId);
    }
}
