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

    public ChangePasswordService(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,LogoutUserService logoutUserService,
                                 TransactionTemplate transactionTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.logoutUserService=logoutUserService;
        this.transactionTemplate = transactionTemplate;
    }

    // Bug 5 (issue 1): the new password used to be set but never saved by this code. It was saved only by
    // luck, by the logout's save (Spring's open-session-in-view). Now it is ONE transaction:
    // check, set, save, log out. All saved, or none.
    public void changePassword(String UserId , ChangePasswordDTO request){
        transactionTemplate.executeWithoutResult(status -> {
            User user = userRepository.findById(UUID.fromString(UserId))
                    .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + UserId));

            if(!passwordEncoder.matches(request.getOldpassword(), user.getPassword())){
                // 401 not 400: this is a credentials check that failed, same category as a bad login.
                throw new UnauthorizedException("INVALID_OLD_PASSWORD", "The current password is incorrect.");
            }
            // Checked only AFTER the old password is right, so it tells a stranger nothing.
            if (passwordEncoder.matches(request.getNewpassword(), user.getPassword())) {
                throw new ValidationException("NEW_PASSWORD_SAME_AS_OLD",
                        "The new password must be different from the current one.");
            }

            user.setPassword(passwordEncoder.encode(request.getNewpassword()));
            user.setPasswordUpdatedAt(LocalDateTime.now());
            userRepository.save(user);   // saved on purpose, not by luck

            // Revoke every old login. It joins THIS transaction, so the password and the logout go together.
            logoutUserService.LogoutUser();
        });
    }
}
