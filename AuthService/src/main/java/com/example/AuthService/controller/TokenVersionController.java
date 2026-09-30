package com.example.AuthService.controller;

import com.example.AuthService.entity.User;
import com.example.AuthService.repository.UserRepository;
import com.project.web.exception.NotFoundException;
import jakarta.validation.constraints.Null;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class TokenVersionController {

    private static final Logger log = LoggerFactory.getLogger(TokenVersionController.class);

    private final UserRepository userRepository;

    public TokenVersionController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // B4 step 5: userid is now a UUID, not a String.
    // Before: "abc" reached UUID.fromString(...) and crashed = 500.
    // Now: Spring checks it first and "abc" gets a clean 400.
    @GetMapping("/token-version/{userid}")
    public Integer getTokenVersion(@PathVariable UUID userid) {
        // C1 step 1: was 2 System.out.println lines. The gateway calls this on every cache miss,
        // so debug - off by default, not one console line per request.
        log.debug("Token version requested for user {}", userid);
        User user = userRepository.findById(userid)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + userid));
        return user.getTokenVersion();
    }
}
