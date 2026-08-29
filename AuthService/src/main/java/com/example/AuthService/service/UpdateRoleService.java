package com.example.AuthService.service;

import com.example.AuthService.dto.UpdateUserRolesRequestDTO;
import com.example.AuthService.dto.UserRoleResponseDTO;
import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.RoleName;
import com.example.AuthService.entity.User;
import com.example.AuthService.kafka.producer.RoleUpdatedEventProducer;
import com.example.AuthService.repository.RoleRepository;
import com.example.AuthService.repository.UserRepository;
import com.project.events.UpdatingRoleEvent;
import com.project.web.exception.NotFoundException;
import com.project.web.exception.ValidationException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
@Service
public class UpdateRoleService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoleUpdatedEventProducer roleUpdatedEventProducer;
private final LogoutUserService logoutUserService;
    public UpdateRoleService(UserRepository userRepository, RoleRepository roleRepository, RoleUpdatedEventProducer roleUpdatedEventProducer, LogoutUserService logoutUserService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.roleUpdatedEventProducer = roleUpdatedEventProducer;
        this.logoutUserService=logoutUserService;

    }
    public ResponseEntity<UserRoleResponseDTO> UpdatingUserRoles(String username, @RequestBody @Valid UpdateUserRolesRequestDTO requestDTO) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("Authorities: " + auth.getAuthorities());
        System.out.println("We entered in the UpdatingUserRoles Service Method with username: " + username + " and requested roles: " + requestDTO.getRole());
//        Fetching User From Db
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + username));

//       Fetching role by Matching in db , Is the roles that Admin  want to assign  are available in our db
        Set<Role> roles = requestDTO.getRole()
                .stream()
                // CHANGED: RoleName.valueOf throws IllegalArgumentException for an unknown name,
                // which used to surface as a confusing 500. A bad role name is the caller mistake,
                // so it is now a 400 that names the valid options.
                .map(UpdateRoleService::toRoleName)//converts String Rolename from client to EnumRolename
                .map(roleEnum -> roleRepository.findByRolename(roleEnum)
                        // Left as a 500 on purpose: the name IS valid, it is just missing from our
                        // roles table. That is our seed-data problem, not the caller problem.
                        .orElseThrow(() -> new RuntimeException("Role not found: " + roleEnum)))
                .collect(Collectors.toSet());
        System.out.println("roles  fetching from db for just checking" + roles);
//        assigning the new roles to User
        user.setRoles(roles);

// then that user also logged out by admin
        logoutUserService.LogoutUserByAdmin(user.getUserid());

//            saving to database
        User updateduser = userRepository.save(user);

        Logger.getLogger("Now start preparing for kafka consumer for updating role");
// Updating Roles in UserService Microservice also, using kafka
        UpdatingRoleEvent updatingRoleEvent= UpdatingRoleEvent.builder().username(user.getUsername())
                .role(user.getRoles().stream().map((role -> role.getRolename().name())).collect(Collectors.toSet()))// convert role from RoleName type  to String type
                .build();
        //publish event
      roleUpdatedEventProducer.publishUpdatedRoleEvent(updatingRoleEvent);


        return ResponseEntity.ok(UserRoleResponseDTO.builder().
                userId(updateduser.getUserid()).
                rolename(updateduser.getRoles().stream().map(role -> role.getRolename().name()).collect(Collectors.toSet())).
                username(updateduser.getUsername())
                .message("User Role Successfully Updated")
                .build());

    }

    /**
     * Converts the role name the client sent into our RoleName enum.
     * Anything unrecognised becomes a 400 that tells the caller what IS accepted, instead of
     * an IllegalArgumentException bubbling out as a 500.
     */
    private static RoleName toRoleName(String rawRole) {
        try {
            return RoleName.valueOf(rawRole);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ValidationException("INVALID_ROLE",
                    "Unknown role: " + rawRole + ". Valid roles are: "
                            + java.util.Arrays.toString(RoleName.values()));
        }
    }
}
