package com.example.AuthService.service;

import com.example.AuthService.dto.UpdateUserRolesRequestDTO;
import com.example.AuthService.dto.UserRoleResponseDTO;
import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.RoleChangeAudit;
import com.example.AuthService.entity.RoleName;
import com.example.AuthService.entity.User;
import com.example.AuthService.kafka.producer.RoleUpdatedEventProducer;
import com.example.AuthService.repository.RoleChangeAuditRepository;
import com.example.AuthService.repository.RoleRepository;
import com.example.AuthService.repository.UserRepository;
import com.project.events.UpdatingRoleEvent;
import com.project.web.exception.ConflictException;
import com.project.web.exception.ForbiddenException;
import com.project.web.exception.NotFoundException;
import com.project.web.exception.ValidationException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
@Service
public class UpdateRoleService {
    private static final Logger log = LoggerFactory.getLogger(UpdateRoleService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoleUpdatedEventProducer roleUpdatedEventProducer;
private final LogoutUserService logoutUserService;
// Outbox step 3: runs "roles + forced logout + both events" as ONE short transaction.
private final TransactionTemplate transactionTemplate;
private final RoleChangeAuditRepository roleChangeAuditRepository;   // issue 67
    public UpdateRoleService(UserRepository userRepository, RoleRepository roleRepository, RoleUpdatedEventProducer roleUpdatedEventProducer, LogoutUserService logoutUserService,
                             TransactionTemplate transactionTemplate, RoleChangeAuditRepository roleChangeAuditRepository) {
        this.roleChangeAuditRepository = roleChangeAuditRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.roleUpdatedEventProducer = roleUpdatedEventProducer;
        this.logoutUserService=logoutUserService;
        this.transactionTemplate = transactionTemplate;

    }
    public ResponseEntity<UserRoleResponseDTO> UpdatingUserRoles(String username, @RequestBody @Valid UpdateUserRolesRequestDTO requestDTO,
                                                                 UUID changedBy) {
        User updateduser = changeRoles(username, requestDTO.getRole(), changedBy, RoleChangeAudit.SOURCE_ADMIN_REQUEST);

        return ResponseEntity.ok(UserRoleResponseDTO.builder().
                userId(updateduser.getUserid()).
                rolename(updateduser.getRoles().stream().map(role -> role.getRolename().name()).collect(Collectors.toSet())).
                username(updateduser.getUsername())
                .message("User Role Successfully Updated")
                .build());
    }

    // Issue 67: the first admin, made at start-up by AdminBootstrap. Same path as an admin request,
    // so the roles.update event is sent and UserService learns it (a hand edit in MySQL never did).
    public void MakeFirstAdmin(String username) {
        changeRoles(username, Set.of(RoleName.ROLE_ADMIN.name()), null, RoleChangeAudit.SOURCE_BOOTSTRAP);
    }

    // changedBy = the admin's user id, or null when the system does it (bootstrap).
    private User changeRoles(String username, Set<String> roleNames, UUID changedBy, String source) {
        // C1 step 1: removed 2 System.out.println lines (caller authorities, target username +
        // requested roles). RoleController already logs who asked and for which roles.
//        Fetching User From Db
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + username));

//       Fetching role by Matching in db , Is the roles that Admin  want to assign  are available in our db
        Set<Role> roles = roleNames
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
        // C1 step 1: removed System.out.println of the Role objects ("for just checking").
        // The outcome is logged once below, after the save.
        // Outbox step 3: this change used to be THREE separate writes (the roles, the forced logout,
        // and the Kafka event) with no transaction around them. A crash in between could change the
        // roles but never tell UserService. Now it is ONE transaction: the new roles, the token
        // revocation (and its logged-out event) and the roles.update event are all saved, or none.
        // LogoutUserByAdmin has its own transactionTemplate: inside this one it JOINS it.
        User updateduser = transactionTemplate.execute(status -> {
        // Re-load the user INSIDE the transaction. The `user` above was read before it. LogoutUserByAdmin
        // below raises the token version on the user it loads; saving the OLD `user` object afterwards
        // would write the old token version back and silently undo the logout. It only worked before
        // because Spring's open-session-in-view happens to hand back the same object (the same hidden
        // dependency as known issue 1). One user object inside one transaction works either way.
        // Issue 67: lock the ADMIN role row first. Every role change waits here for the one before it,
        // so the admin count below is always up to date (two admins removing each other cannot both pass).
        roleRepository.findByRolenameForUpdate(RoleName.ROLE_ADMIN)
                .orElseThrow(() -> new RuntimeException("Role not found: " + RoleName.ROLE_ADMIN));   // seed data: 500
        User target = userRepository.findById(user.getUserid())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + username));
        String oldRoles = namesOf(target.getRoles());

        // Issue 67 guards (only when this change takes ADMIN away):
        boolean removesAdmin = hasAdmin(target.getRoles()) && !hasAdmin(roles);
        if (removesAdmin && target.getUserid().equals(changedBy)) {
            throw new ForbiddenException("CANNOT_REMOVE_OWN_ADMIN_ROLE",
                    "You cannot remove your own ADMIN role. Ask another admin.");
        }
        if (removesAdmin && userRepository.countOtherUsersWithRole(RoleName.ROLE_ADMIN, target.getUserid()) == 0) {
            throw new ConflictException("LAST_ADMIN", "There must always be at least one admin.");
        }
//        assigning the new roles to User
        target.setRoles(roles);

// then that user also logged out by admin
        logoutUserService.LogoutUserByAdmin(target.getUserid());

//            saving to database
        User saved = userRepository.save(target);

        // Issue 67: the record of this change, in the same transaction (both or neither).
        roleChangeAuditRepository.save(RoleChangeAudit.builder()
                .targetUserId(saved.getUserid())
                .changedBy(changedBy)
                .oldRoles(oldRoles)
                .newRoles(namesOf(roles))
                .source(source)
                .changedAt(LocalDateTime.now())
                .build());

        // C1 step 1: this line used to be Logger.getLogger("Now start preparing..."), which logs
        // NOTHING - getLogger() only creates a logger whose name is that sentence. Now a real line.
        log.debug("Roles saved for user {}: {}. Saving roles.update event to the outbox", saved.getUserid(),
                roleNames);
// Updating Roles in UserService Microservice also: the event goes into the outbox (same transaction)
        UpdatingRoleEvent updatingRoleEvent= UpdatingRoleEvent.builder().username(target.getUsername())
                .role(target.getRoles().stream().map((role -> role.getRolename().name())).collect(Collectors.toSet()))// convert role from RoleName type  to String type
                .build();
        //save the event (key = the user id, so it stays in order with that user's other events)
      roleUpdatedEventProducer.publishUpdatedRoleEvent(saved.getUserid(), updatingRoleEvent);
      return saved;
        });

        return updateduser;
    }

    private static boolean hasAdmin(Set<Role> roles) {
        return roles.stream().anyMatch(role -> role.getRolename() == RoleName.ROLE_ADMIN);
    }

    // For the audit row: sorted and joined, e.g. "ROLE_ADMIN,ROLE_ENGINEER".
    private static String namesOf(Set<Role> roles) {
        return roles.stream().map(role -> role.getRolename().name()).sorted().collect(Collectors.joining(","));
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
