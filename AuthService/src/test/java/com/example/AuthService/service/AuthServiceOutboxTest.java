package com.example.AuthService.service;

import com.example.AuthService.config.SecurityConfig;
import com.example.AuthService.dto.LoginFromDTO;
import com.example.AuthService.dto.UpdateCredentialsRequestDTO;
import com.example.AuthService.dto.UpdateUserRolesRequestDTO;
import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.RoleName;
import com.example.AuthService.entity.User;
import com.example.AuthService.kafka.producer.RoleUpdatedEventProducer;
import com.example.AuthService.kafka.producer.UserLoggedInEventProducer;
import com.example.AuthService.kafka.producer.UserLoggedOutEventProducer;
import com.example.AuthService.kafka.producer.UserRegisteredEventProducer;
import com.example.AuthService.kafka.producer.UserUpdatedEventProducer;
import com.example.AuthService.repository.RoleRepository;
import com.example.AuthService.repository.UserRepository;
import com.project.outbox.OutboxWriter;
import com.project.web.exception.ConflictException;
import com.project.web.exception.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.JacksonUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

/**
 * Outbox step 3: the REAL AuthService code, a real database (H2 in MySQL mode), and NO Kafka at all.
 *
 * What it proves, for each of the 5 events:
 *   - the user change and ONE outbox row are saved together
 *   - the row has the right topic and the Kafka key = the user id
 *   - if the event row cannot be saved, the user change is NOT saved either (both or neither)
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-outbox;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:outbox/outbox_event_mysql.sql"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({RegisteringUserService.class, LoginUserService.class, LoginAttemptService.class, LogoutUserService.class,
        UpdateCredentialsService.class, UpdateRoleService.class,
        UserRegisteredEventProducer.class, UserLoggedInEventProducer.class, UserLoggedOutEventProducer.class,
        UserUpdatedEventProducer.class, RoleUpdatedEventProducer.class,
        AuthServiceOutboxTest.OutboxSetup.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // no test-wide transaction: we test the real ones
class AuthServiceOutboxTest {

    @TestConfiguration
    static class OutboxSetup {
        @Bean
        OutboxWriter outboxWriter(DataSource dataSource) {
            return new OutboxWriter(new JdbcTemplate(dataSource), JacksonUtils.enhancedObjectMapper());
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @Autowired
    RegisteringUserService registeringUserService;
    @Autowired
    LoginUserService loginUserService;
    @Autowired
    LogoutUserService logoutUserService;
    @Autowired
    UpdateCredentialsService updateCredentialsService;
    @Autowired
    UpdateRoleService updateRoleService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    DataSource dataSource;

    // LoginUserService needs these three; the login test only cares about what is saved.
    @MockitoBean
    AuthenticationManager authenticationManager;
    @MockitoBean
    UserDetailsService userDetailsService;
    @MockitoBean
    SecurityConfig securityConfig;

    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM roles");
        for (RoleName name : RoleName.values()) {
            roleRepository.save(Role.builder().rolename(name).build());
        }
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ helpers

    private static User newUser(String name, String password) {
        return User.builder().username(name).email(name + "@example.com").password(password).build();
    }

    /** Create a user through the REAL signup code, then forget the signup's own event row. */
    private User existingUser(String name) {
        User user = newUser(name, "plain-secret-1");
        registeringUserService.RegisteringTheUser(user);
        jdbc.update("DELETE FROM outbox_event");
        return userRepository.findById(user.getUserid()).orElseThrow();
    }

    private void actingAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getUserid().toString(), null, List.of()));
    }

    private List<Map<String, Object>> outboxRows() {
        return jdbc.queryForList("SELECT * FROM outbox_event ORDER BY seq");
    }

    private List<String> topics() {
        return outboxRows().stream().map(r -> r.get("TOPIC").toString()).toList();
    }

    /** The user's role names, read straight from the database (no lazy-loading outside a session). */
    private List<String> roleNamesOf(User user) {
        return jdbc.queryForList("SELECT r.rolename FROM user_roles ur JOIN roles r ON r.id = ur.role_id "
                + "WHERE ur.user_id = ? ORDER BY r.rolename", String.class, user.getUserid().toString());
    }

    private int tokenVersionOf(User user) {
        return userRepository.findById(user.getUserid()).orElseThrow().getTokenVersion();
    }

    /** Make every outbox insert fail, run the action, put the table back. */
    private void whileTheOutboxTableIsMissing(Runnable action) {
        jdbc.execute("ALTER TABLE outbox_event RENAME TO outbox_event_hidden");
        try {
            action.run();
        } finally {
            jdbc.execute("ALTER TABLE outbox_event_hidden RENAME TO outbox_event");
        }
    }

    // ------------------------------------------------------------------ tests

    @Test
    void signupSavesTheUserAndOneRegisteredRowKeyedByTheUserId() {
        User user = newUser("alice", "plain-secret-1");

        registeringUserService.RegisteringTheUser(user);

        assertTrue(userRepository.existsByEmail("alice@example.com"));
        List<Map<String, Object>> rows = outboxRows();
        assertEquals(1, rows.size());
        assertEquals("user.registered", rows.get(0).get("TOPIC"));
        assertEquals(user.getUserid().toString(), rows.get(0).get("MSG_KEY"),
                "the key is the user id now (it used to be no key at all)");
        assertEquals("NEW", rows.get(0).get("STATUS"));
        String payload = rows.get(0).get("PAYLOAD").toString();
        assertTrue(payload.contains("alice@example.com"));
        assertFalse(payload.toLowerCase().contains("password") && payload.contains("plain-secret-1"),
                "the password never goes into an event");
    }

    @Test
    void ifTheRegisteredEventCannotBeSavedTheUserIsNotSavedEither() {
        User user = newUser("bob", "plain-secret-1");

        whileTheOutboxTableIsMissing(() ->
                assertThrows(RuntimeException.class, () -> registeringUserService.RegisteringTheUser(user)));

        assertFalse(userRepository.existsByEmail("bob@example.com"), "both or neither: no user without its event");
    }

    @Test
    void aTakenUsernameSavesNothing() {
        existingUser("carol");

        assertThrows(ConflictException.class,
                () -> registeringUserService.RegisteringTheUser(newUser("carol", "plain-secret-2")));

        assertEquals(0, outboxRows().size());
    }

    @Test
    void aSuccessfulLoginSavesTheLoginTimeAndALoggedInRow() {
        User user = existingUser("dave");
        LoginFromDTO request = new LoginFromDTO();
        request.setUsername("dave");
        request.setEmail("dave@example.com");
        request.setPassword("plain-secret-1");
        doReturn("test-token").when(securityConfig).generateToken(any(), any());

        loginUserService.LoginUser(request);

        assertNotNull(userRepository.findById(user.getUserid()).orElseThrow().getLastLoginAt());
        List<Map<String, Object>> rows = outboxRows();
        assertEquals(1, rows.size());
        assertEquals("user.logged-in", rows.get(0).get("TOPIC"));
        assertEquals(user.getUserid().toString(), rows.get(0).get("MSG_KEY"));
    }

    @Test
    void aWrongPasswordSavesNoEvent() {
        existingUser("erin");
        LoginFromDTO request = new LoginFromDTO();
        request.setUsername("erin");
        request.setEmail("erin@example.com");
        request.setPassword("wrong-password");
        doThrow(new BadCredentialsException("bad")).when(authenticationManager).authenticate(any());

        assertThrows(UnauthorizedException.class, () -> loginUserService.LoginUser(request));

        assertEquals(0, outboxRows().size(), "no login happened, so no logged-in event");
    }

    @Test
    void logoutRaisesTheTokenVersionAndSavesALoggedOutRowTogether() {
        User user = existingUser("frank");
        actingAs(user);
        int before = tokenVersionOf(user);

        logoutUserService.LogoutUser();

        assertEquals(before + 1, tokenVersionOf(user));
        assertEquals(List.of("user.logged-out"), topics());
        assertEquals(user.getUserid().toString(), outboxRows().get(0).get("MSG_KEY"));
    }

    @Test
    void ifTheLoggedOutEventCannotBeSavedTheTokenIsNotRevokedEither() {
        User user = existingUser("grace");
        actingAs(user);
        int before = tokenVersionOf(user);

        whileTheOutboxTableIsMissing(() -> assertThrows(RuntimeException.class, () -> logoutUserService.LogoutUser()));

        assertEquals(before, tokenVersionOf(user), "both or neither: the token version did not move");
    }

    @Test
    void changingYourEmailSavesTheChangeTheLogoutAndTwoEventRows() {
        User user = existingUser("heidi");
        actingAs(user);
        int before = tokenVersionOf(user);
        UpdateCredentialsRequestDTO request = UpdateCredentialsRequestDTO.builder()
                .newEmail("heidi.new@example.com").password("plain-secret-1").build();

        updateCredentialsService.UpdateCredentials(request, user.getUserid().toString());

        User after = userRepository.findById(user.getUserid()).orElseThrow();
        assertEquals("heidi.new@example.com", after.getEmail());
        assertEquals(before + 1, after.getTokenVersion());
        assertEquals(List.of("user.logged-out", "user.Updated"), topics());
        assertTrue(outboxRows().stream().allMatch(r -> user.getUserid().toString().equals(r.get("MSG_KEY"))));
    }

    @Test
    void changingRolesSavesRolesLogoutAndBothEventsAllTogether() {
        User user = existingUser("ivan");
        int before = tokenVersionOf(user);

        updateRoleService.UpdatingUserRoles("ivan",
                UpdateUserRolesRequestDTO.builder().role(Set.of("ROLE_SUPERVISOR")).build());

        User after = userRepository.findById(user.getUserid()).orElseThrow();
        assertEquals(before + 1, after.getTokenVersion(), "tokens are revoked, and the bump was not overwritten");
        assertEquals(List.of("ROLE_SUPERVISOR"), roleNamesOf(user));
        assertEquals(List.of("user.logged-out", "roles.update"), topics());
        assertTrue(outboxRows().stream().allMatch(r -> user.getUserid().toString().equals(r.get("MSG_KEY"))),
                "roles.update is keyed by the user id too (it used to have no key)");
        assertTrue(outboxRows().get(1).get("PAYLOAD").toString().contains("ROLE_SUPERVISOR"));
    }

    @Test
    void ifTheRolesEventCannotBeSavedNothingAtAllChanges() {
        User user = existingUser("judy");
        int before = tokenVersionOf(user);
        List<String> rolesBefore = roleNamesOf(user);

        whileTheOutboxTableIsMissing(() -> assertThrows(RuntimeException.class, () ->
                updateRoleService.UpdatingUserRoles("judy",
                        UpdateUserRolesRequestDTO.builder().role(Set.of("ROLE_ADMIN")).build())));

        assertEquals(before, tokenVersionOf(user), "the forced logout was rolled back too");
        assertEquals(rolesBefore, roleNamesOf(user), "the new role was not saved");
        assertFalse(roleNamesOf(user).contains("ROLE_ADMIN"));
    }
}
