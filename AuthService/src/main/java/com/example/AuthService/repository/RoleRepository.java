package com.example.AuthService.repository;

import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.RoleName;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role,Integer> {
    Optional<Role> findByRolename (RoleName roleName);

    // Issue 67: lock one role row until the transaction ends (SELECT ... FOR UPDATE).
    // Every role change locks the ADMIN row first, so two changes run one after the other:
    // two admins removing each other at the same moment cannot leave zero admins.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Role r WHERE r.rolename = :roleName")
    Optional<Role> findByRolenameForUpdate(@Param("roleName") RoleName roleName);

}
