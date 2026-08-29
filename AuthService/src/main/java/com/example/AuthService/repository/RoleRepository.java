package com.example.AuthService.repository;

import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role,Integer> {
    Optional<Role> findByRolename (RoleName roleName);

}
