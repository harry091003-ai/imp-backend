package com.example.UserService.service;

import com.example.UserService.entity.User;
import com.example.UserService.repository.RoleRepository;
import com.example.UserService.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Step 3: the batch answer also says who each user's supervisor is (supervisorId).
 * Real code, in-memory database. Checks the JSON, because that is what the other service receives.
 * Written first: the "has a supervisor" tests failed.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user-supervisor;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // like a real call: no test-wide transaction around it
class SupervisorInBatchTest {

    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;

    private UserService userService;
    private final ObjectMapper json = new ObjectMapper();

    private User supervisor;
    private User engineerWithSupervisor;
    private User engineerWithoutSupervisor;

    @BeforeEach
    void setUp() {
        // Built by hand: the class only needs these two repositories.
        userService = new UserService(userRepository, roleRepository);
        userRepository.deleteAll();
        supervisor = userRepository.save(user("sam"));
        User engineer = user("eva");
        engineer.setSupervisor(supervisor);
        engineerWithSupervisor = userRepository.save(engineer);
        engineerWithoutSupervisor = userRepository.save(user("noah"));
    }

    private static User user(String name) {
        LocalDateTime now = LocalDateTime.now();
        return User.builder().userid(UUID.randomUUID()).username(name).email(name + "@example.com")
                .createdAt(now).updatedAt(now).build();
    }

    /** Call the real method, turn the answer into JSON like the HTTP reply, and index it by user id. */
    private Map<String, JsonNode> batch(UUID... ids) throws Exception {
        String reply = json.writeValueAsString(userService.GetBatchOfUsers_Credentials(List.of(ids)));
        Map<String, JsonNode> byId = new HashMap<>();
        for (JsonNode node : json.readTree(reply)) {
            byId.put(node.path("userid").asText(), node);
        }
        return byId;
    }

    @Test
    void anEngineerWithASupervisorGetsTheSupervisorsId() throws Exception {
        JsonNode eva = batch(engineerWithSupervisor.getUserid()).get(engineerWithSupervisor.getUserid().toString());

        assertEquals(supervisor.getUserid().toString(), eva.path("supervisorId").asText(),
                "the answer says who the supervisor is");
    }

    @Test
    void aUserWithoutASupervisorGetsAnEmptySupervisorId() throws Exception {
        JsonNode noah = batch(engineerWithoutSupervisor.getUserid()).get(engineerWithoutSupervisor.getUserid().toString());

        assertFalse(noah.hasNonNull("supervisorId"), "no supervisor -> empty, not an error");
    }

    @Test
    void oneBatchCanMixAllCasesAndUnknownIdsAreSkipped() throws Exception {
        Map<String, JsonNode> answer = batch(engineerWithSupervisor.getUserid(), engineerWithoutSupervisor.getUserid(),
                supervisor.getUserid(), UUID.randomUUID());

        assertEquals(3, answer.size(), "the unknown id is simply not in the answer (as before)");
        assertEquals(supervisor.getUserid().toString(),
                answer.get(engineerWithSupervisor.getUserid().toString()).path("supervisorId").asText());
        assertFalse(answer.get(engineerWithoutSupervisor.getUserid().toString()).hasNonNull("supervisorId"));
        assertFalse(answer.get(supervisor.getUserid().toString()).hasNonNull("supervisorId"),
                "the supervisor has no supervisor of their own here");
    }

    @Test
    void theOldFieldsAreStillThere() throws Exception {
        // NotificationService already reads these three. Adding a field must not break them.
        JsonNode eva = batch(engineerWithSupervisor.getUserid()).get(engineerWithSupervisor.getUserid().toString());

        assertEquals("eva", eva.path("username").asText());
        assertEquals("eva@example.com", eva.path("email").asText());
        assertEquals(engineerWithSupervisor.getUserid().toString(), eva.path("userid").asText());
    }
}
