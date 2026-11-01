package com.example.AuthService.service;

import com.example.AuthService.dto.ChangePasswordDTO;
import com.example.AuthService.entity.User;
import com.example.AuthService.repository.UserRepository;
import com.project.web.exception.NotFoundException;
import com.project.web.exception.UnauthorizedException;
import com.project.web.exception.ValidationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ChangePasswordService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LogoutUserService logoutUserService;
    private final TransactionTemplate transactionTemplate;
    // Issue 68: a wrong OLD password counts in the same lockout as a wrong login.
    private final LoginAttemptService loginAttemptService;

    public ChangePasswordService(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,LogoutUserService logoutUserService,
                                 TransactionTemplate transactionTemplate, LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.logoutUserService=logoutUserService;
        this.transactionTemplate = transactionTemplate;
        this.loginAttemptService = loginAttemptService;
    }

    // Bug 5 (issue 1): the new password used to be set but never saved by this code. It was saved only by
    // luck, by the logout's save (Spring's open-session-in-view). Now it is ONE transaction:
    // check, set, save, log out. All saved, or none.
    //
    // Issue 68: someone holding a stolen token could guess the old password here without limit. Now a wrong
    // old password counts in the login lockout (5 -> locked 15 min), and a locked account cannot change it.
    public void changePassword(String UserId , ChangePasswordDTO request){
        // Returns the email when the old password was wrong. It is counted AFTER this transaction, because
        // throwing the 401 inside would roll the count back (the issue 55 trap).
        String wrongOldPasswordFor = transactionTemplate.execute(status -> {
            User user = userRepository.findById(UUID.fromString(UserId))
                    .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + UserId));

            // Same rule and message as login. Checked first, so a locked account gives no more guesses.
            if (loginAttemptService.isLockedNow(user)) {
                throw new UnauthorizedException("ACCOUNT_LOCKED",
                        "Account is locked due to multiple failed login attempts. Try again later.");
            }
            if(!passwordEncoder.matches(request.getOldpassword(), user.getPassword())){
                return user.getEmail();
            }
            // Checked only AFTER the old password is right, so it tells a stranger nothing.
            if (passwordEncoder.matches(request.getNewpassword(), user.getPassword())) {
                throw new ValidationException("NEW_PASSWORD_SAME_AS_OLD",
                        "The new password must be different from the current one.");
            }

            user.setPassword(passwordEncoder.encode(request.getNewpassword()));
            user.setPasswordUpdatedAt(LocalDateTime.now());
            // The right password was typed: start counting from 0 again, as a successful login does.
            user.setFailedLoginAttempts(0);
            user.setAccountLocked(false);
            user.setLockedUntil(null);
            userRepository.save(user);   // saved on purpose, not by luck

            // Revoke every old login. It joins THIS transaction, so the password and the logout go together.
            logoutUserService.LogoutUser();
            return null;
        });

        if (wrongOldPasswordFor != null) {
            loginAttemptService.recordFailedLogin(wrongOldPasswordFor);   // its own transaction, see LoginAttemptService
            // 401 not 400: this is a credentials check that failed, same category as a bad login.
            throw new UnauthorizedException("INVALID_OLD_PASSWORD", "The current password is incorrect.");
        }
    }
}
