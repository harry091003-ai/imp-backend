package com.example.AuthService.controller;

import com.example.AuthService.dto.RefreshRequestDTO;
import com.example.AuthService.dto.RefreshResponseDTO;
import com.example.AuthService.service.RefreshTokenService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Auth v2 part B: swap a refresh token for a new access token + a new refresh token.
// Under /auth/**, so it needs no access token: it is called exactly when the access token has run out.
@RestController
@RequestMapping("/auth")
public class RefreshController {

    private final RefreshTokenService refreshTokenService;

    public RefreshController(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/refresh")
    public RefreshResponseDTO refresh(@Valid @RequestBody RefreshRequestDTO request) {
        return refreshTokenService.refresh(request.getRefreshToken());
    }
}
