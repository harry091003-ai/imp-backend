package com.example.UserService.service;

import com.example.UserService.dto.UserNameDTO;
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
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BCR-05 (2026-11-02): the REAL lookup with a real database (H2), no transaction open, like a real call.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user-lookup;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserLookupTest {

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

    private User user(String name) {
        LocalDateTime now = LocalDateTime.now();
        return userRepository.save(User.builder().userid(UUID.randomUUID()).username(name).email(name + "@example.com")
                .status(Status.ACTIVE).roles(Set.of(engineerRole)).createdAt(now).updatedAt(now).build());
    }

    @Test
    void eachIdComesBackWithItsName() {
        User priya = user("priya");
        User omar = user("omar");

        Map<UUID, String> names = userService.lookupUserNames(List.of(priya.getUserid(), omar.getUserid())).stream()
                .collect(Collectors.toMap(UserNameDTO::getUserid, UserNameDTO::getUsername));

        assertEquals(Map.of(priya.getUserid(), "priya", omar.getUserid(), "omar"), names);
    }

    // Any logged-in user may call this, so it must never give away more than the name.
    @Test
    void theAnswerHoldsOnlyTheIdAndTheName() throws Exception {
        User priya = user("priya");

        String json = new ObjectMapper().writeValueAsString(userService.lookupUserNames(List.of(priya.getUserid())));

        assertTrue(json.contains("\"username\":\"priya\""), json);
        assertFalse(json.contains("example.com"), "no email in the answer: " + json);
        assertFalse(json.contains("ACTIVE"), "no status in the answer: " + json);
        assertFalse(json.contains("ROLE_"), "no roles in the answer: " + json);
    }

    // A deleted user, or an id that never existed, is simply left out. The screen then shows a short id.
    @Test
    void anUnknownIdIsLeftOut() {
        User priya = user("priya");

        List<UserNameDTO> answer = userService.lookupUserNames(List.of(priya.getUserid(), UUID.randomUUID()));

        assertEquals(1, answer.size());
    }

    @Test
    void theSameIdTwiceComesBackOnce() {
        User priya = user("priya");

        assertEquals(1, userService.lookupUserNames(List.of(priya.getUserid(), priya.getUserid())).size());
    }

    @Test
    void anEmptyListGivesAnEmptyAnswer() {
        assertEquals(List.of(), userService.lookupUserNames(List.of()));
    }
}
