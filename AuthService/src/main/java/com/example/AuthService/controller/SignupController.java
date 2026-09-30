package com.example.AuthService.controller;

import com.example.AuthService.dto.SignupResponseDTO;
import com.example.AuthService.dto.SignupFormDTO;
import com.example.AuthService.entity.User;
import com.example.AuthService.service.RegisteringUserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/auth")
public class SignupController {
    private final  RegisteringUserService service;
    public SignupController(RegisteringUserService service){

        this.service = service;
    }
    @PostMapping("/signup")
    public ResponseEntity<SignupResponseDTO> Signup(@Valid @RequestBody SignupFormDTO Request){
//        Making User Object and then transfering Data from DTO to our User Object

        User user= User.builder()
                .username(Request.getUsername())
                .email(Request.getEmail())
                .password(Request.getPassword())
                .build();
        // C1 step 1: removed System.out.println(user.getRoles()). It always printed an empty
        // set (roles are added later, in the service). RegisteringUserService logs the result.
        service.RegisteringTheUser(user);
return ResponseEntity.ok(new SignupResponseDTO(user.getUsername(),"Registered Successfully"));
    }
}
