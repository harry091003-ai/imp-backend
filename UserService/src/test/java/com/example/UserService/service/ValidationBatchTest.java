package com.example.UserService.service;

import com.example.UserService.dto.UserValidationDTO;
import com.example.UserService.entity.Role;
import com.example.UserService.entity.RoleName;
import com.example.UserService.entity.Status;
import com.example.UserService.entity.User;
import com.example.UserService.repository.RoleRepository;
import com.example.UserService.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AI plan phase 1 (2026-11-01): Ai-Service asks about all its candidate engineers in one call.
 * The REAL method with a real database (H2) and no transaction open, so lazy roles would fail here if not loaded.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user-validation-batch;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // like a real call: no test-wide transaction around it
class ValidationBatchTest {

    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;

    private UserService userService;
    private Role engineerRole;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, roleRepository);
        userRepository.deleteAll();
        roleRepository.deleteAll();
        engineerRole = roleRepository.save(Role.builder().rolename(RoleName.ROLE_ENGINEER).build());
    }

    private User user(String name, Status status) {
        LocalDateTime now = LocalDateTime.now();
        return userRepository.save(User.builder().userid(UUID.randomUUID()).username(name).email(name + "@example.com")
                .status(status).roles(Set.of(engineerRole)).createdAt(now).updatedAt(now).build());
    }

    @Test
    void eachUserComesBackWithStatusAndRoles() {
        User active = user("eva", Status.ACTIVE);
        User suspended = user("noah", Status.SUSPENDED);

        Map<UUID, UserValidationDTO> answer = userService.LimitedUsersInfoForValidation(List.of(active.getUserid(), suspended.getUserid()))
                .stream().collect(Collectors.toMap(UserValidationDTO::getUserid, u -> u));

        assertEquals(2, answer.size());
        assertEquals("ACTIVE", answer.get(active.getUserid()).getStatus());
        assertEquals("SUSPENDED", answer.get(suspended.getUserid()).getStatus());
        assertEquals(Set.of("ROLE_ENGINEER"), answer.get(active.getUserid()).getRoles());
    }

    @Test
    void anUnknownIdIsSimplyMissingFromTheAnswer() {
        User active = user("eva", Status.ACTIVE);

        List<UserValidationDTO> answer = userService.LimitedUsersInfoForValidation(List.of(active.getUserid(), UUID.randomUUID()));

        assertEquals(1, answer.size());
    }
}
