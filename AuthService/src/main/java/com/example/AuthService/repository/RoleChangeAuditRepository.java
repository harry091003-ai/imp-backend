package com.example.AuthService.repository;

import com.example.AuthService.entity.RoleChangeAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

// Issue 67: the record of role changes.
@Repository
public interface RoleChangeAuditRepository extends JpaRepository<RoleChangeAudit, Long> {

    List<RoleChangeAudit> findByTargetUserIdOrderByChangedAtAsc(UUID targetUserId);
}
