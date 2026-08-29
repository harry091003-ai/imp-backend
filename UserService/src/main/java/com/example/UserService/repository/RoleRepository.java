package com.example.UserService.repository;

import com.example.UserService.entity.Role;
import com.example.UserService.entity.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface RoleRepository extends JpaRepository<Role,Integer> {
    Optional<Role> findByRolename(RoleName roleName);

}
