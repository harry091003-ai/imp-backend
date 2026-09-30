package com.example.AuthService.controller;

import com.example.AuthService.dto.UpdateUserRolesRequestDTO;
import com.example.AuthService.dto.UserRoleResponseDTO;
import com.example.AuthService.service.UpdateRoleService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/roles")
public class RoleController {

    private static final Logger log = LoggerFactory.getLogger(RoleController.class);

private final UpdateRoleService service;

    public RoleController(UpdateRoleService service) {
        this.service = service;
    }

    @PreAuthorize("hasRole('ADMIN')")// only admin can update the user role
    @PutMapping("/{username}/update")
    public ResponseEntity<UserRoleResponseDTO> UpdateUserRoles(@PathVariable String username
            , @Valid @RequestBody UpdateUserRolesRequestDTO requestDTO)
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // C1 step 1: was 3 System.out.println lines (target username, caller id, caller authorities).
        // Trimmed: no username (personal data), no authorities (the /roles/** rule already
        // guarantees ROLE_ADMIN). Kept: who asked (their id) and what they asked for.
        log.debug("Role update requested by user {}: roles {}", auth.getName(), requestDTO.getRole());
        return   service.UpdatingUserRoles( username,requestDTO);

    }



}
