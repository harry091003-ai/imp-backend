package com.example.UserService.service;

import com.example.UserService.dto.*;
import com.example.UserService.entity.Role;
import com.example.UserService.entity.RoleName;
import com.example.UserService.entity.User;
import com.example.UserService.repository.RoleRepository;
import com.example.UserService.repository.UserRepository;
import com.project.web.exception.ConflictException;
import com.project.web.exception.ForbiddenException;
import com.project.web.exception.NotFoundException;
import com.project.web.exception.ValidationException;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@EnableMethodSecurity
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
private static final Logger log= LoggerFactory.getLogger(UserService.class);
    public UserService(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }


//    public ResponseEntity<UserRoleResponseDTO> UpdatingUserRoles(String username, @RequestBody @Valid UpdateUserRolesRequestDTO requestDTO) {
//        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
//        System.out.println("Authorities: " + auth.getAuthorities());
//        System.out.println("We entered in the UpdatingUserRoles Service Method with username: " + username + " and requested roles: " + requestDTO.getRole());
////        Fetching User From Db
//        User user = userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException(" User Not found"));
//
////       Fetching role by Matching in db , Is the roles that Admin  want to assign  are available in our db
//        Set<Role> roles = requestDTO.getRole()
//                .stream()
//                .map(RoleName::valueOf)//converts String Rolename from client to EnumRolename
//                .map(roleEnum -> roleRepository.findByRolename(roleEnum)
//                        .orElseThrow(() -> new RuntimeException("Role not found: " + roleEnum)))
//                .collect(Collectors.toSet());
//        System.out.println("roles  fetching from db for just checking" + roles);
////        assigning the new roles to User
//        user.setRoles(roles);
//
////            saving to database
//        User updateduser = userRepository.save(user);
//
//        return ResponseEntity.ok(UserRoleResponseDTO.builder().
//                userId(updateduser.getUserid()).
//                rolename(updateduser.getRoles().stream().map(role -> role.getRolename().name()).collect(Collectors.toSet())).
//                username(updateduser.getUsername())
//                .message("User Role Successfully Updated")
//                .build());
//
//    }


    public List<UserResponseDTO> GetRoleSpecifiedUsers(RoleName role) {

        List<User> users = userRepository.findUsersByRole(role);

        //converting list of users as per the format that we want to send to client (UserResponseDTO) and then returning
        return users.stream()
                .map(user -> UserResponseDTO.builder()
                        .userid(user.getUserid())
                        .username(user.getUsername())
                        .status(user.getStatus())
                        .email(user.getEmail())
                        .roles(user.getRoles().stream().map(r -> r.getRolename().name()).collect(Collectors.toSet()))
                        .createdAt(user.getCreatedAt())
                        .updatedAt(user.getUpdatedAt())
                        .lastLoginAt(user.getLastLoginAt())
                        .supervisorId(user.getSupervisor()!=null? user.getSupervisor().getUserid():null)
                        .build())
                .collect(Collectors.toList());
    }

    public UserResponseDTO GetRoleSpecifiedUserById(RoleName role, UUID id) {
        User Engineer = userRepository.findEngineerWithID(id, role)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND",
                        "No user with id " + id + " holding role " + role));
        return UserResponseDTO.builder()
                .userid(Engineer.getUserid())
                .username(Engineer.getUsername())
                .email(Engineer.getEmail())
                .status(Engineer.getStatus())
                .roles(Engineer.getRoles().stream().map(r -> r.getRolename().name()).collect(Collectors.toSet()))
                .createdAt(Engineer.getCreatedAt())
                .updatedAt(Engineer.getUpdatedAt())
                .lastLoginAt(Engineer.getLastLoginAt())
                .supervisorId(Engineer.getSupervisor()!=null? Engineer.getSupervisor().getUserid():null)
                .build();
    }

    public UserResponseDTO GetOwnDetails(Authentication authentication) {

        String UserId = authentication.getName();
      User user = userRepository.findById(UUID.fromString(UserId))
              .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + UserId));
        return UserResponseDTO.builder()
              .userid(user.getUserid())
              .username(user.getUsername())
              .email(user.getEmail())
              .status(user.getStatus())
              .roles(user.getRoles().stream().map(r -> r.getRolename().name()).collect(Collectors.toSet()))
              .createdAt(user.getCreatedAt())
              .updatedAt(user.getUpdatedAt())
              .lastLoginAt(user.getLastLoginAt())
              .build();


    }

    public StatusResponseDTO updateEngineerStatus(UUID id, StatusRequestDTO statusRequestDTO,Authentication auth) {
        // 1.Fetch user
        User engineer = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + id));
       //2.Check is user is Engineer
        boolean isEngineer=engineer.getRoles().stream().anyMatch(role -> role.getRolename()==RoleName.ROLE_ENGINEER);
       if (!isEngineer){
           // 400: the caller may update statuses, they just pointed at the wrong kind of user.
           throw new ValidationException("USER_NOT_ENGINEER",
                   "Status can only be updated for a user with the ENGINEER role.");
       }
        //3. Check → request status not null
        if (statusRequestDTO.getStatus() == null) {
            // Also covered by @NotNull on the DTO now, kept as a belt-and-braces guard
            // for any non-HTTP caller of this service method.
            throw new ValidationException("STATUS_REQUIRED", "Status is required.");
        }

        //4. if the status is changing by supervisor then we are checking is that supervisor has valid own of engineer (engineer must be assigned to this engineer)
        boolean isSupervisor=auth.getAuthorities().stream().anyMatch(role->role.getAuthority().equals(RoleName.ROLE_SUPERVISOR.name()));
                UUID authUserId = UUID.fromString(auth.getName());

            if(isSupervisor){
               if(engineer.getSupervisor()==null ||!engineer.getSupervisor().getUserid().equals(authUserId)){
                   // 403: a real permission boundary - this supervisor does not own this engineer.
                   throw new ForbiddenException("NOT_YOUR_ENGINEER",
                           "You can only update engineers assigned to you.");
               }
        }

// 5. Update status
        engineer.setStatus(statusRequestDTO.getStatus());

        userRepository.save(engineer);
//6. response
        return StatusResponseDTO.builder()
                .userid(engineer.getUserid())
                .username(engineer.getUsername())
                .email(engineer.getEmail())
                .status(engineer.getStatus())
                .message("Engineer status updated successfully")
                .build();
    }

    public StatusResponseDTO updateSupervisorStatus(UUID id, StatusRequestDTO statusRequestDTO) {
    // 1.Fetch user
    User supervisor = userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + id));
    //2.Check is user is Engineer
    boolean isSupervisor=supervisor.getRoles().stream().anyMatch(role -> role.getRolename()==RoleName.ROLE_SUPERVISOR);
    if (!isSupervisor){
        throw new ValidationException("USER_NOT_SUPERVISOR",
                "Status can only be updated for a user with the SUPERVISOR role.");
    }
    //3. Check → request status not null
    if (statusRequestDTO.getStatus() == null) {
        throw new ValidationException("STATUS_REQUIRED", "Status is required.");
    }


// 4. Update status
    supervisor.setStatus(statusRequestDTO.getStatus());

    userRepository.save(supervisor);
//5. response
    return StatusResponseDTO.builder()
            .userid(supervisor.getUserid())
            .username(supervisor.getUsername())
            .email(supervisor.getEmail())
            .status(supervisor.getStatus())
            .message("Supervisor status updated successfully")
            .build();
}

    @Transactional
    public MessageDTO assignOrReassignSupervisor(UUID engineerId, UUID supervisorId) {

        // 1. Fetch engineer
        User engineer = userRepository.findById(engineerId)
                .orElseThrow(() -> new NotFoundException("ENGINEER_NOT_FOUND",
                        "Engineer not found: " + engineerId));

        // 2. Fetch supervisor
        User supervisor = userRepository.findById(supervisorId)
                .orElseThrow(() -> new NotFoundException("SUPERVISOR_NOT_FOUND",
                        "Supervisor not found: " + supervisorId));

        // 3. Validation: same user
        if (engineerId.equals(supervisorId)) {
            throw new ValidationException("SELF_SUPERVISION",
                    "A user cannot be their own supervisor.");
        }

        // 4. Role validation
        boolean isEngineer = engineer.getRoles().stream()
                .anyMatch(role -> role.getRolename() == RoleName.ROLE_ENGINEER);

        boolean isSupervisor = supervisor.getRoles().stream()
                .anyMatch(role -> role.getRolename() == RoleName.ROLE_SUPERVISOR);

        if (!isEngineer) {
            throw new ValidationException("TARGET_USER_NOT_ENGINEER",
                    "The selected user does not have the ENGINEER role.");
        }

        if (!isSupervisor) {
            throw new ValidationException("TARGET_USER_NOT_SUPERVISOR",
                    "The selected user does not have the SUPERVISOR role.");
        }

        // 5. Idempotency check: such that if we are assigning same supervisor again to engineer then it should not do anything and just return
        if (engineer.getSupervisor() != null &&
                engineer.getSupervisor().getUserid().equals(supervisorId)) {
            log.info("Engineer {} is already assigned to Supervisor {}, no changes made.", engineerId, supervisorId);
            return MessageDTO.builder().SuccessMessage("Engineer: "+engineerId +"  is already assigned to Supervisor: "+supervisorId+" , no changes made.").build();
        }

        // 6. Prevent circular hierarchy (basic check) so that the supervisor supervisor_id must not be our engineer_id
        if (supervisor.getSupervisor() != null &&
                supervisor.getSupervisor().getUserid().equals(engineerId)) {
            log.info("Circular hierarchy not allowed...so Supervisor {} cannot be assigned to Engineer {} because it creates a circular relationship.", supervisorId, engineerId);
            // 409: both users are valid, it is the existing hierarchy that refuses this link.
            throw new ConflictException("CIRCULAR_HIERARCHY",
                    "That assignment would create a circular supervisor relationship.");
        }

        // 7. Remove from old supervisor
        User oldSupervisor = engineer.getSupervisor();
        if (oldSupervisor != null) {
            oldSupervisor.getEngineers().remove(engineer);
        }

        // 8. Assign new supervisor
        engineer.setSupervisor(supervisor);
        supervisor.getEngineers().add(engineer);

        // 9. Save
        userRepository.save(engineer);
        return MessageDTO.builder().SuccessMessage("Engineer assigned/reassigned successfully").build();
    }

    @Transactional
    public MessageDTO removeSupervisor(UUID engineerId){
        User engineer=userRepository.findById(engineerId)
                .orElseThrow(() -> new NotFoundException("ENGINEER_NOT_FOUND",
                        "Engineer not found: " + engineerId));
        User supervisor = engineer.getSupervisor();
        if(supervisor==null){
            log.info("No Supervisor is assigned to this engineer with id {} ",engineerId);
            return MessageDTO.builder().SuccessMessage("No Supervisor is assigned to this engineer with id:"+ engineerId).build();
        }
        //else supervisor exit for this engineer so we remove that supervisor

supervisor.getEngineers().remove(engineer);
engineer.setSupervisor(null);
userRepository.save(engineer);
return MessageDTO.builder().SuccessMessage("Supervisor removed successfully from engineer with id: "+ engineerId).build();
    }

    public SupervisorResponseDTO getSupervisorOfEngineer(UUID engineerId){
        User engineer = userRepository.findById(engineerId)
                .orElseThrow(() -> new NotFoundException("ENGINEER_NOT_FOUND",
                        "Engineer not found: " + engineerId));

        User supervisor = engineer.getSupervisor();

        if (supervisor == null) {
            throw new NotFoundException("NO_SUPERVISOR_ASSIGNED",
                    "This engineer has no supervisor assigned.");
        }
        return SupervisorResponseDTO.builder()
                .userid(supervisor.getUserid())
                .username(supervisor.getUsername())
                .email(supervisor.getEmail())
                .build();
    }

  /*   as i have List<Engineer> Supervisor in my Entity Relation but still why we dont use supervisor.getEngineers() to get List of engineers under this supervisor
     instead we are using userRepository.findBySupervisor_Userid(supervisorId);
    because it may cause 1. Lazy Loading Problem
                         2. Hidden Queries (N+1 Problem)
                         3.No Control:You CANNOT control:paginate ,filter,sort

     keep this rule in mind:
      A>Use Repository  for
    ✔ APIs / DB fetching
    ✔ APIs (Controller layer)
    ✔ ** Fetching lists
    ✔ use of Pagination/filtering needed
    ✔ Production code
       B>Use Entity Relations for
    ✔ internal logic / navigation
    ✔Inside @Transactional method i.e:  supervisor.getEngineers().size();
    ✔ Business logic (validations) i.e: if (supervisor.getEngineers().size() > 10): throw error
    ✔ In-memory operations i.e:supervisor.getEngineers().add(engineer);
    ✔ Setting relationships*/
    public List<UserResponseDTO> getEngineersUnderSupervisor(UUID supervisorId){
        List<User> engineers = userRepository.findBySupervisor_Userid(supervisorId);
        return engineers.stream().map(user -> UserResponseDTO.builder()
                .userid(user.getUserid())
                .username(user.getUsername())
                .email(user.getEmail())
                .status(user.getStatus())
                .roles(user.getRoles().stream().map(r -> r.getRolename().name()).collect(Collectors.toSet()))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build())
                .toList();
    }


    public Page<UserSearchResponseDTO>search_Users(String query, Pageable pageable ,Authentication auth){
        boolean isAdmin=auth.getAuthorities().stream().anyMatch(role->role.getAuthority().equals(RoleName.ROLE_ADMIN.name()));
        boolean isSupervisor=auth.getAuthorities().stream().anyMatch(role->role.getAuthority().equals(RoleName.ROLE_SUPERVISOR.name()));
        boolean isEngineer=auth.getAuthorities().stream().anyMatch(role->role.getAuthority().equals(RoleName.ROLE_ENGINEER.name()));
        UUID userId=UUID.fromString(auth.getName());
        Page<User> users= userRepository.searchUsers(query,isAdmin,isSupervisor,isEngineer,userId,pageable);
        //Page is not just a list
        // It is an object that contains List+ extra metadata .........( metadata is about total pages ,total elements etc)..
        // so we can not apply stream() on page because stream() will convert it to list and we will lose that metadata like
        // total pages ,total elements ,current page number etc..so for Page we have to apply map() function that will convert each
        // User to UserSearchResponseDTO but still keep the Page format with all metadata and then we can return that Page<UserSearchResponseDTO>
        // to client and client can get the data as well as metadata from that Page object
        //stream() is used when we want to convert a List to another List with some transformation that why we use stream().map()....collect(Collectors.toList()).


return users.map(user -> UserSearchResponseDTO.builder()
        .userid(user.getUserid())
        .username(user.getUsername())
        .email(user.getEmail())
        .supervisorId(user.getSupervisor()!=null ? user.getSupervisor().getUserid():null)
        .build());
    }

    public UserValidationDTO LimitedUserInfoForValidation(UUID userId){
        // This endpoint is consumed by IncidentService over HTTP. Throwing NotFoundException
        // makes it answer 404, which is what lets the caller distinguish "no such user" from
        // "UserService is broken".
        User user=userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + userId));
        return toValidationDTO(user);
    }

    // AI plan phase 1: the same answer for many users, in one query (the roles are loaded with the users).
    public List<UserValidationDTO> LimitedUsersInfoForValidation(List<UUID> userIds){
        return userRepository.findWithRolesByUseridIn(userIds).stream().map(UserService::toValidationDTO).toList();
    }

    private static UserValidationDTO toValidationDTO(User user){
        return UserValidationDTO.builder()
                .userid(user.getUserid())
                .status(user.getStatus()!=null?user.getStatus().name():"INACTIVE")
                .roles(user.getRoles().stream().map(r -> r.getRolename().name()).collect(Collectors.toSet()))
                .build();
    }
//-------------------------------------------------
//    specificaly made for internal service communication
    //for getting the UserCredential(mainly Email: used by Notification service for sending email)using Synchronous Interservice Communication
    public UserCredentialDTO GetUserCredentials(UUID userId){
        User user=userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + userId));
        return UserCredentialDTO.builder()
                .userid(user.getUserid())
                .email(user.getEmail())
                .username(user.getUsername())
                .build();
    }
    //for getting the set of UserCredential
    public List<UserCredentialDTO>GetBatchOfUsers_Credentials(List<UUID> userIds){
        List<User> users=userRepository.findAllById(userIds);// even we some some id's are duplicate but still it return unique users because in database:  1 -> Harman ,2 -> John ,3 -> Alex it uses somthing like SELECT * FROM users WHERE id IN (1,2,2,3,1)
        return users.stream().map(user->
                UserCredentialDTO
                        .builder()
                        .userid(user.getUserid())
                        .email(user.getEmail())
                        .username(user.getUsername())
                        // Step 3: the supervisor's id (null = none). Only the id is read, so the lazy
                        // supervisor is not loaded (SupervisorInBatchTest proves it with no transaction open).
                        .supervisorId(user.getSupervisor() == null ? null : user.getSupervisor().getUserid())
                        .build()).collect(Collectors.toList());
    }

}

