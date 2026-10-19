package com.example.NotificationService.Entity;

public enum NotificationStatus {
    PENDING,
    SUCCESS,
    FAILED,
    // ADDED 2026-10-17 (SLA notifications step 4): saved and visible in the app, NO email sent (email is not
    // switched on yet - your decision). Honest, unlike SUCCESS with "Email Successfully sent" (issue 16).
    RECORDED
}
