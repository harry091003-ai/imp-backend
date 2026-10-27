package com.example.AuthService.service;

import com.example.AuthService.config.SecurityConfig;
import com.example.AuthService.entity.User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Makes the short access token (the JWT) for one user. Auth v2 part B.
 * Moved here from LoginUserService, because the refresh endpoint makes the same token:
 * one place for the claims, so login and refresh can never send the gateway different ones.
 */
@Service
public class AccessTokenIssuer {

    private final SecurityConfig securityConfig;

    public AccessTokenIssuer(SecurityConfig securityConfig) {
        this.securityConfig = securityConfig;
    }

    public String issueFor(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", user.getRoles()
                .stream()
                .map(role -> role.getRolename().name())
                .toList());
        // The gateway compares this with AuthService's current value: logout / password change / role change
        // bump it, which kills every access token made before.
        claims.put("tokenVersion", user.getTokenVersion());
        claims.put("email", user.getEmail());
        return securityConfig.generateToken(user.getUserid().toString(), claims);
    }
}
