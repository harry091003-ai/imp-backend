package com.example.AuthService.repository;

import com.example.AuthService.entity.RoleName;
import com.example.AuthService.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
@Repository
public interface UserRepository extends JpaRepository<User,UUID>{

//    can also write an extra methods for DB interaction
//    Here we doesnot need to write the implementation of this function spring will automatically write it for us
//    we just need to write the method declaration like this
//    eg: function name start with: findBy  then field name in camelcase like: Email
//    so findByEmail

    public User findByEmail(String Email);
    public Boolean existsByUsername(String roleName);
    public Boolean existsByEmail(String email);
    Optional<User> findByUsername(String username);
}
