package com.example.AuthService.controller;

import com.example.AuthService.dto.LoginFromDTO;
import com.example.AuthService.dto.LoginResponseDTO;
import com.example.AuthService.dto.SignupResponseDTO;
import com.example.AuthService.service.LoginUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
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
    // BCR-02: the refresh token goes to the browser as an HttpOnly cookie, not in the JSON (@JsonIgnore there).
    private final RefreshCookie refreshCookie;

    public LoginController(LoginUserService login_UserService, RefreshCookie refreshCookie) {
        service = login_UserService;
        this.refreshCookie = refreshCookie;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> loginTheUser(@Valid @RequestBody LoginFromDTO request){
        // A failed login throws inside LoginUser, so it never reaches this line and sets no cookie.
        ResponseEntity<LoginResponseDTO> answer = service.LoginUser(request);
        return ResponseEntity.status(answer.getStatusCode())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.issue(answer.getBody().getRefreshToken()))
                .body(answer.getBody());
    }
}
