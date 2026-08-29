package com.example.AuthService.controller;

import com.example.AuthService.dto.UpdateUserRolesRequestDTO;
import com.example.AuthService.dto.UserRoleResponseDTO;
import com.example.AuthService.service.UpdateRoleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/roles")
public class RoleController {

private final UpdateRoleService service;

    public RoleController(UpdateRoleService service) {
        this.service = service;
    }

    @PreAuthorize("hasRole('ADMIN')")// only admin can update the user role
    @PutMapping("/{username}/update")
    public ResponseEntity<UserRoleResponseDTO> UpdateUserRoles(@PathVariable String username
            , @Valid @RequestBody UpdateUserRolesRequestDTO requestDTO)
    {
        System.out.println("We entered the controller and the username is: " + username + " and the requested roles are: " + requestDTO.getRole());


        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("User: " + auth.getName());
        System.out.println("Authorities: " + auth.getAuthorities());
        return   service.UpdatingUserRoles( username,requestDTO);

    }



}
