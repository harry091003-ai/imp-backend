package com.example.AuthService.service;

import com.example.AuthService.dto.LoginFromDTO;
import com.example.AuthService.dto.LoginResponseDTO;
import com.example.AuthService.entity.User;
import com.example.AuthService.kafka.producer.UserLoggedInEventProducer;
import com.example.AuthService.repository.UserRepository;


import com.project.events.UserLoggedInEvent;
import com.project.web.exception.UnauthorizedException;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Transactional
@Service
public class LoginUserService {
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final UserLoggedInEventProducer userLoggedInEventProducer;
    // Issue 55 fix: counts a wrong password in its OWN transaction (see LoginAttemptService for why).
    private final LoginAttemptService loginAttemptService;
    // Auth v2 part B: the access token (was made here with SecurityConfig) and the new refresh token.
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenService refreshTokenService;
    public LoginUserService(AuthenticationManager authentication_manager,
                            UserDetailsService TheuserDetailsService,UserRepository userRepository,UserLoggedInEventProducer userLoggedInEventProducer,
                            LoginAttemptService loginAttemptService, AccessTokenIssuer accessTokenIssuer,
                            RefreshTokenService refreshTokenService){
        authenticationManager=authentication_manager;
        userDetailsService=TheuserDetailsService;
        this.userRepository=userRepository;
        this.userLoggedInEventProducer= userLoggedInEventProducer;
        this.loginAttemptService = loginAttemptService;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenService = refreshTokenService;
    }

    public ResponseEntity<LoginResponseDTO> LoginUser(LoginFromDTO request) {

        //initial condition to  check is the account is locked or not
        // CHANGED: this used to RETURN a 401 whose body was a LoginResponseDTO (its own private
        // error shape). It now THROWS, so a failed login comes back in the same ErrorResponse
        // shape as every other error in the platform.
        HandleMultipleFailedLoginAttempts(request.getEmail());

        //matching username and password with the database using authenticationManager.authenticate method before generating token
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    //here we are using the email as the username
                    request.getEmail(),
                    request.getPassword()
            ));
           }
             catch (BadCredentialsException e) {
//          setting the number of failed login attempts and updating it to the database and
//          also locked the account if the failed login attempts is greater than or equal to 5 account will set to locked
            // Issue 55: count it in its OWN transaction, so the 401 below cannot undo it.
            loginAttemptService.recordFailedLogin(request.getEmail());
            // Same message whether the email exists or not. Saying "email not registered"
            // would let anyone probe this endpoint to harvest a list of valid accounts.
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password.");
            };

        // for generating token

        User user = userRepository.findByEmail(request.getEmail());

        // Auth v2 part B: the claims are built in AccessTokenIssuer now, shared with POST /auth/refresh.
        String token = accessTokenIssuer.issueFor(user);
        // ...and a refresh token (7 days), saved in THIS transaction: a failed login stores none.
        String refreshToken = refreshTokenService.issueForLogin(user.getUserid());

     User OurUser=   userRepository.findByEmail(request.getEmail());
        // updating the lastLoginAt field
        OurUser.setLastLoginAt(LocalDateTime.now());
        // resetting the failed login attempts to 0 and also make the unlock the account after successful login
        OurUser.setFailedLoginAttempts(0);
        OurUser.setAccountLocked(false);
        OurUser.setLockedUntil(null);

    User updatedUser=    userRepository.save(OurUser);

//        Using KafkaEvent Producer For Updating the Logged-in Filed Of our User in UserService Db
        UserLoggedInEvent event=UserLoggedInEvent
                .builder()
                .userid(updatedUser.getUserid())
                .userLastLoggedInAt(updatedUser.getLastLoginAt())
                .build();
        userLoggedInEventProducer.sendUserLoggedInEvent(updatedUser.getUserid(),event);

        return ResponseEntity.ok(LoginResponseDTO
                .builder()
                .username(updatedUser.getUsername())
                .email(updatedUser.getEmail())
                .userid(updatedUser.getUserid())
                .status(200)
                .message("Login Successfully")
                .JWTtoken(token)
                .refreshToken(refreshToken)
                .build());

        }


        // CHANGED: was returning ResponseEntity-or-null as a pseudo error channel.
        // Now returns void and throws, which is what the rest of the codebase does.
        public void HandleMultipleFailedLoginAttempts(String email){
            User user=userRepository.findByEmail(email);
            if(user==null){
                // Same generic message as a wrong password - do not reveal which emails exist.
                throw new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password.");
            }
            // Issue 6: a lock now ends (locked only until lockedUntil).
            if(loginAttemptService.isLockedNow(user)){
                throw new UnauthorizedException("ACCOUNT_LOCKED",
                        "Account is locked due to multiple failed login attempts. Try again later.");
            }
        }
    }

// authenticationManager.authenticate,AuthenticationManager,DaoAuthenticationProvider, usedetailService,UserDetails ,loadUserByUsername,generateToken
