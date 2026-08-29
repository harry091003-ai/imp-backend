package com.example.AuthService.controller;

import com.example.AuthService.dto.LoginFromDTO;
import com.example.AuthService.dto.LoginResponseDTO;
import com.example.AuthService.dto.SignupResponseDTO;
import com.example.AuthService.service.LoginUserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class LoginController {
   private final LoginUserService service;

    public LoginController(LoginUserService login_UserService) {
        service = login_UserService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> loginTheUser(@Valid @RequestBody LoginFromDTO request){
         return service.LoginUser(request);


    }
}
