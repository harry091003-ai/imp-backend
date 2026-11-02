package com.example.AuthService.controller;

import com.example.AuthService.dto.RefreshResponseDTO;
import com.example.AuthService.service.RefreshTokenService;
import com.project.web.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Auth v2 part B: swap a refresh token for a new access token + a new refresh token.
// Under /auth/**, so it needs no access token: it is called exactly when the access token has run out.
// BCR-02 (2026-11-02): the refresh token comes in the imp_refresh cookie and the new one goes out the same way.
// The JSON body is no longer read, so a token sent there is ignored (the old way is closed).
@RestController
@RequestMapping("/auth")
public class RefreshController {

    private final RefreshTokenService refreshTokenService;
    private final RefreshCookie refreshCookie;

    public RefreshController(RefreshTokenService refreshTokenService, RefreshCookie refreshCookie) {
        this.refreshTokenService = refreshTokenService;
        this.refreshCookie = refreshCookie;
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponseDTO> refresh(
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        try {
            // Missing, empty or too long: refused before it is hashed or looked up. A 401, not a 400, so the
            // frontend treats it like any other dead session and shows the login page.
            if (refreshToken == null || refreshToken.isBlank() || refreshToken.length() > RefreshCookie.MAX_LENGTH) {
                throw new UnauthorizedException("INVALID_REFRESH_TOKEN", "Refresh token is not valid. Please log in again.");
            }
            RefreshResponseDTO answer = refreshTokenService.refresh(refreshToken);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshCookie.issue(answer.getRefreshToken()))
                    .body(answer);
        } catch (UnauthorizedException refused) {
            // A refused token is dead, so the browser may as well delete it. Except a locked account: that
            // token is not used up and works again when the lock ends (RefreshTokenService).
            // The header stays on the response when common-web's handler writes the error body.
            if (!"ACCOUNT_LOCKED".equals(refused.getErrorCode())) {
                response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.clear());
            }
            throw refused;
        }
    }
}
