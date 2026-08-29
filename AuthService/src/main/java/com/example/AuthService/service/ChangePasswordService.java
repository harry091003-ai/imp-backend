package com.example.AuthService.service;

import com.example.AuthService.dto.ChangePasswordDTO;
import com.example.AuthService.entity.User;
import com.example.AuthService.repository.UserRepository;
import com.project.web.exception.NotFoundException;
import com.project.web.exception.UnauthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ChangePasswordService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LogoutUserService logoutUserService;

    public ChangePasswordService(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,LogoutUserService logoutUserService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.logoutUserService=logoutUserService;
    }
    public void changePassword(String UserId , ChangePasswordDTO request){
        User user = userRepository.findById(java.util.UUID.fromString(UserId))
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + UserId));

        // NOTE: this null check is dead code - orElseThrow above already guarantees non-null.
        // Left in place deliberately; removing it is unrelated to error handling.

        if(!passwordEncoder.matches(request.getOldpassword(), user.getPassword())){
            // 401 not 400: this is a credentials check that failed, same category as a bad login.
            throw new UnauthorizedException("INVALID_OLD_PASSWORD", "The current password is incorrect.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewpassword()));

        logoutUserService.LogoutUser();
    }
}
