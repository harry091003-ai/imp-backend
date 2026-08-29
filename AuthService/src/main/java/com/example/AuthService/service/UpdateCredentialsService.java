package com.example.AuthService.service;

import com.example.AuthService.dto.UpdateCredentialResponseDTO;
import com.example.AuthService.dto.UpdateCredentialsRequestDTO;
import com.example.AuthService.entity.User;
import com.example.AuthService.kafka.producer.UserUpdatedEventProducer;
import com.example.AuthService.repository.UserRepository;
import com.project.events.UpdatingUserEvent;
import com.project.web.exception.ConflictException;
import com.project.web.exception.NotFoundException;
import com.project.web.exception.UnauthorizedException;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class UpdateCredentialsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
private final UserUpdatedEventProducer userUpdatedEventProducer;
private final LogoutUserService logoutUserService;
    public UpdateCredentialsService(UserRepository userRepository,PasswordEncoder passwordEncoder,UserUpdatedEventProducer userUpdatedEventProducer, LogoutUserService logoutUserService) {
        this.userRepository = userRepository;
        this.passwordEncoder=passwordEncoder;
        this.userUpdatedEventProducer=userUpdatedEventProducer;
        this.logoutUserService=logoutUserService;
    }
    // CHANGED: return type tightened from ResponseEntity<?> to the actual DTO. It was only
    // wildcarded because the error paths used to return raw Strings; those now throw instead.
    public ResponseEntity<UpdateCredentialResponseDTO> UpdateCredentials(UpdateCredentialsRequestDTO request, String userId){
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + userId));

        // ✅ 1. Verify password
        // CHANGED: was returning a bare string body "Invalid password" with a 401.
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("INVALID_PASSWORD",
                    "The password you entered is incorrect.");
        }

        // ✅ 2. Check email uniqueness
        // CHANGED: these were 400s with a bare string body. A name already being taken is a
        // clash with existing state, which is a 409, not a malformed request.
        if (request.getNewEmail() != null &&
                userRepository.existsByEmail(request.getNewEmail())) {
            throw new ConflictException("EMAIL_TAKEN", "That email is already registered.");
        }

        if (request.getNewUsername() != null &&
                userRepository.existsByUsername(request.getNewUsername())) {
            throw new ConflictException("USERNAME_TAKEN", "That username is already taken.");
        }
        // ✅ 3. Update Email field
        if (request.getNewEmail() != null &&!request.getNewEmail().trim().isEmpty()) {
            user.setEmail(request.getNewEmail());
        }

        // Update Username field
        if (request.getNewUsername() != null && !request.getNewUsername().trim().isEmpty()) {
            user.setUsername(request.getNewUsername());
        }
// Update the time profile updated time field
        user.setUpdatedAt(LocalDateTime.now());



        //  Logout user before saving
//        user.setTokenVersion(user.getTokenVersion() + 1);
        logoutUserService.LogoutUser();

        //saving
       User updatedUser= userRepository.save(user);


// producing a Kafka Event
        UpdatingUserEvent event=UpdatingUserEvent.builder()
                .userId(updatedUser.getUserid())
                .UpdatedEmail(updatedUser.getEmail())
                .UpdatedUserName(updatedUser.getUsername())
                .build();

        userUpdatedEventProducer.sendUserUpdatedEvent(event);


        return ResponseEntity.ok( UpdateCredentialResponseDTO.builder()
                .userId(user.getUserid())
                .updatedEmail(user.getEmail())
                .updatedUsername(user.getUsername())
                .updatedAt(user.getUpdatedAt())
                .message("Profile updated successfully")
                .build());

    }
}
