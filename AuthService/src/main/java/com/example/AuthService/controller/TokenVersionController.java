package com.example.AuthService.controller;

import com.example.AuthService.entity.User;
import com.example.AuthService.repository.UserRepository;
import com.project.web.exception.NotFoundException;
import jakarta.validation.constraints.Null;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class TokenVersionController {

    private final UserRepository userRepository;

    public TokenVersionController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/token-version/{userid}")
    public Integer getTokenVersion(@PathVariable String userid) {
        System.out.println("UUID is: ");
 System.out.println(UUID.fromString(userid));
        User user = userRepository.findById(UUID.fromString(userid))
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + userid));
        return user.getTokenVersion();
    }
}
