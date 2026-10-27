package com.example.AuthService.service;

import com.example.AuthService.entity.User;
import com.example.AuthService.kafka.producer.UserLoggedOutEventProducer;
import com.example.AuthService.repository.RefreshTokenRepository;
import com.example.AuthService.repository.UserRepository;
import com.project.events.UserLoggedInEvent;
import com.project.events.UserLoggedOutAtEvent;
import com.project.web.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class LogoutUserService {
    private static final Logger log = LoggerFactory.getLogger(LogoutUserService.class);

    private final UserRepository userRepository;
private final UserLoggedOutEventProducer userLoggedOutEventProducer;
// Outbox step 3: "bump the token version + save the logged-out event row" must be ONE transaction.
// REQUIRED (the default) means: if the caller already has a transaction (UpdateCredentialsService,
// UpdateRoleService), JOIN it; if not (LogoutController, ChangePasswordService), start a short one.
private final TransactionTemplate transactionTemplate;
// Auth v2 part B: a logout also cancels every refresh token of the user, in the SAME transaction.
// Password change, role change and credentials change all log out through here, so they cancel them too.
private final RefreshTokenRepository refreshTokenRepository;
    public LogoutUserService(UserRepository userRepository,UserLoggedOutEventProducer userLoggedOutEventProducer,
                             TransactionTemplate transactionTemplate, RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.userLoggedOutEventProducer=userLoggedOutEventProducer;
        this.transactionTemplate = transactionTemplate;
        this.refreshTokenRepository = refreshTokenRepository;
    }
// this method we use when we want to logout the current user
    public void LogoutUser(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        String UserId=auth.getName();
        User updatedUser = transactionTemplate.execute(status -> {
        User user = userRepository.findById(UUID.fromString(UserId))
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + UserId));

        user.setTokenVersion(user.getTokenVersion()+1);
        // update the LastLogout time
        user.setLastLogoutAt(LocalDateTime.now());
       User saved= userRepository.save(user);
        refreshTokenRepository.revokeAllOfUser(saved.getUserid(), saved.getLastLogoutAt());
        //Outbox step 3: save the Logged-out event in the outbox, in the SAME transaction, so UserService
        //(through Kafka, a moment later) updates its copy of the user. Both saved, or neither.
         UserLoggedOutAtEvent event=UserLoggedOutAtEvent.builder()
                 .userid(saved.getUserid())
                 .userLastLoggedOutAt(saved.getLastLogoutAt())
                 .build();
         userLoggedOutEventProducer.sendUserLoggedOutEvent(saved.getUserid(),event);
         return saved;
        });
        // C1 step 1: 3 System.out.println lines in this method (user id, token version, "success")
        // became this ONE line. Token version is a plain counter, fine to log.
        log.debug("User {} logged out, token version is now {}", UserId, updatedUser.getTokenVersion());
    }
    public void LogoutUserByAdmin(UUID userId){

        User updatedUser = transactionTemplate.execute(status -> {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + userId));
        user.setTokenVersion(user.getTokenVersion()+1);
        // update the LastLogout time
        user.setLastLogoutAt(LocalDateTime.now());
      User saved=  userRepository.save(user);
        refreshTokenRepository.revokeAllOfUser(saved.getUserid(), saved.getLastLogoutAt());
        //Outbox step 3: the Logged-out event goes into the outbox, in the SAME transaction.
        UserLoggedOutAtEvent event=UserLoggedOutAtEvent.builder()
                .userid(saved.getUserid())
                .userLastLoggedOutAt(saved.getLastLogoutAt())
                .build();
        userLoggedOutEventProducer.sendUserLoggedOutEvent(saved.getUserid(),event);
        return saved;
        });
        // C1 step 1: was System.out.println with the username. Trimmed to the user id.
        log.debug("User {} logged out by an admin, token version is now {}", userId,
                updatedUser.getTokenVersion());
    }
}
