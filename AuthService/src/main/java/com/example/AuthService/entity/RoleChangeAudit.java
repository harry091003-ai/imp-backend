package com.example.AuthService.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

// Issue 67: one row per role change (table made by Flyway V5).
// Saved in the same transaction as the change, so there is never a change without its record.
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "role_change_audit", indexes = {
        @Index(name = "idx_role_change_target_time", columnList = "target_user_id, changed_at")
})
public class RoleChangeAudit {

    public static final String SOURCE_ADMIN_REQUEST = "ADMIN_REQUEST";
    public static final String SOURCE_BOOTSTRAP = "BOOTSTRAP";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_user_id", nullable = false)
    private UUID targetUserId;      // whose roles changed

    @Column(name = "changed_by")
    private UUID changedBy;         // the admin's user id; null = the system (bootstrap)

    @Column(name = "old_roles", nullable = false, length = 255)
    private String oldRoles;        // e.g. "ROLE_ENGINEER"

    @Column(name = "new_roles", nullable = false, length = 255)
    private String newRoles;        // e.g. "ROLE_ADMIN,ROLE_ENGINEER"

    @Column(name = "source", nullable = false, length = 20)
    private String source;          // SOURCE_ADMIN_REQUEST or SOURCE_BOOTSTRAP

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
}
