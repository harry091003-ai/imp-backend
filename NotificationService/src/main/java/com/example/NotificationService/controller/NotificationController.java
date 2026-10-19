package com.example.NotificationService.controller;

import com.example.NotificationService.dto.MyNotificationsResponseDTO;
import com.example.NotificationService.service.MyNotificationsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * GET /notifications/me : your own notifications, newest first, with the total (step 7).
 * - Who you are comes from the login (X-User-Id from the gateway), never from the URL.
 * - Users only (see SecurityConfig). page 0 or more, size 1 to 100; anything else is a clear 400.
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final MyNotificationsService myNotificationsService;

    @GetMapping("/me")
    public ResponseEntity<MyNotificationsResponseDTO> getMyNotifications(
            Authentication auth,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be 0 or more") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must be at most 100") int size) {
        UUID me = UUID.fromString(auth.getName());   // always a user id here (users only, SecurityConfig)
        return ResponseEntity.ok(myNotificationsService.getMyNotifications(me, page, size));
    }
}
