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

    // B4 step 5: userid is now a UUID, not a String.
    // Before: "abc" reached UUID.fromString(...) and crashed = 500.
    // Now: Spring checks it first and "abc" gets a clean 400.
    @GetMapping("/token-version/{userid}")
    public Integer getTokenVersion(@PathVariable UUID userid) {
        System.out.println("UUID is: ");
 System.out.println(userid);
        User user = userRepository.findById(userid)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + userid));
        return user.getTokenVersion();
    }
}
