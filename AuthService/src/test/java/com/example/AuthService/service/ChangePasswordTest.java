package com.example.AuthService.service;

import com.example.AuthService.dto.ChangePasswordDTO;
import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.RoleName;
import com.example.AuthService.entity.User;
import com.example.AuthService.kafka.producer.UserLoggedOutEventProducer;
import com.example.AuthService.kafka.producer.UserRegisteredEventProducer;
import com.example.AuthService.repository.RoleRepository;
import com.example.AuthService.repository.UserRepository;
import com.project.outbox.OutboxWriter;
import com.project.web.exception.ApiException;
import com.project.web.exception.UnauthorizedException;
import com.project.web.exception.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.JacksonUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug 5 (issue 1): changing your password, on the REAL AuthService code (H2 in MySQL mode, no Kafka).
 *
 * This test has no "open session in view" (no web request), so a password that is only saved
 * by luck is NOT saved here. That is exactly what we want to catch.
 *
 * Rules:
 *   1. the new password is really saved, and passwordUpdatedAt is set
 *   2. all old logins are revoked (token version + 1, a logged-out event row)
 *   3. a wrong old password changes nothing (401)
 *   4. a new password equal to the old one is refused (400) and changes nothing
 *   5. both or neither: if the logout cannot be saved, the password stays the old one
 *
 * Issue 68 (2026-10-28): a wrong OLD password counts in the SAME lockout counter as a wrong login.
 *   6. each wrong old password adds 1 to failed_login_attempts, and the 401 does not undo it
 *   7. login and change-password share one counter: 4 wrong logins + 1 wrong old password = locked
 *   8. a locked account cannot change its password, not even with the right old one
 *   9. the right old password sets the counter back to 0
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-password;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:outbox/outbox_event_mysql.sql"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ChangePasswordService.class, LogoutUserService.class, RegisteringUserService.class, LoginAttemptService.class,
        UserLoggedOutEventProducer.class, UserRegisteredEventProducer.class, ChangePasswordTest.Setup.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // no test-wide transaction: we test the real ones
class ChangePasswordTest {

    @TestConfiguration
    static class Setup {
        @Bean
        OutboxWriter outboxWriter(DataSource dataSource) {
            return new OutboxWriter(new JdbcTemplate(dataSource), JacksonUtils.enhancedObjectMapper());
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    private static final String OLD = "old-secret-1";
    private static final String NEW = "new-secret-2";

    @Autowired
    ChangePasswordService changePasswordService;
    @Autowired
    RegisteringUserService registeringUserService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    DataSource dataSource;

    private JdbcTemplate jdbc;
    private User user;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM roles");
        for (RoleName name : RoleName.values()) {
            roleRepository.save(Role.builder().rolename(name).build());
        }
        // sign up through the real code, then log in as that user (LogoutUser reads who is logged in)
        user = User.builder().username("pat").email("pat@example.com").password(OLD).build();
        registeringUserService.RegisteringTheUser(user);
        jdbc.update("DELETE FROM outbox_event");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getUserid().toString(), null, List.of()));
    }

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ helpers

    private void change(String oldPassword, String newPassword) {
        changePasswordService.changePassword(user.getUserid().toString(),
                ChangePasswordDTO.builder().oldpassword(oldPassword).newpassword(newPassword).build());
    }

    private User fromDatabase() {
        return userRepository.findById(user.getUserid()).orElseThrow();
    }

    private boolean storedPasswordIs(String plain) {
        return passwordEncoder.matches(plain, fromDatabase().getPassword());
    }

    private int failedAttempts() {
        return fromDatabase().getFailedLoginAttempts();
    }

    private List<String> outboxTopics() {
        return jdbc.queryForList("SELECT topic FROM outbox_event ORDER BY seq", String.class);
    }

    /** Refused with this code, and NOTHING changed: password, token version, date, events. */
    private <T extends ApiException> void refusedAndNothingChanged(Class<T> type, String code, Executable action) {
        User before = fromDatabase();
        T error = assertThrows(type, action);
        assertEquals(code, error.getErrorCode());
        User after = fromDatabase();
        assertTrue(storedPasswordIs(OLD), "the old password still works");
        assertEquals(before.getTokenVersion(), after.getTokenVersion(), "nobody was logged out");
        assertEquals(before.getPasswordUpdatedAt(), after.getPasswordUpdatedAt());
        assertEquals(List.of(), outboxTopics(), "no event");
    }

    // ------------------------------------------------------------------ tests

    @Test
    void theNewPasswordIsReallySaved() {
        change(OLD, NEW);

        assertTrue(storedPasswordIs(NEW), "the new password works");
        assertFalse(storedPasswordIs(OLD), "the old password no longer works");
    }

    @Test
    void theDateOfTheChangeIsSaved() {
        // Signup set the date a moment ago. Put it far in the past, so only the CHANGE can set a new one.
        jdbc.update("UPDATE users SET password_updated_at = ? WHERE userid = ?",
                LocalDateTime.of(2020, 1, 1, 0, 0), user.getUserid());
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        change(OLD, NEW);

        LocalDateTime saved = fromDatabase().getPasswordUpdatedAt();
        assertNotNull(saved);
        assertTrue(saved.isAfter(before), "passwordUpdatedAt is the time of the change, not of signup");
    }

    @Test
    void allOldLoginsAreRevoked() {
        int tokenVersionBefore = fromDatabase().getTokenVersion();

        change(OLD, NEW);

        assertEquals(tokenVersionBefore + 1, fromDatabase().getTokenVersion(), "old tokens stop working");
        assertEquals(List.of("user.logged-out"), outboxTopics());
    }

    @Test
    void aWrongOldPasswordChangesNothing() {
        refusedAndNothingChanged(UnauthorizedException.class, "INVALID_OLD_PASSWORD",
                () -> change("not-my-password", NEW));
    }

    @Test
    void aNewPasswordEqualToTheOldOneIsRefused() {
        refusedAndNothingChanged(ValidationException.class, "NEW_PASSWORD_SAME_AS_OLD",
                () -> change(OLD, OLD));
    }

    @Test
    void ifTheLogoutCannotBeSavedThePasswordStaysTheOldOne() {
        int tokenVersionBefore = fromDatabase().getTokenVersion();
        jdbc.execute("ALTER TABLE outbox_event RENAME TO outbox_event_hidden");   // the logout's event row will fail
        try {
            assertThrows(RuntimeException.class, () -> change(OLD, NEW));
        } finally {
            jdbc.execute("ALTER TABLE outbox_event_hidden RENAME TO outbox_event");
        }

        assertTrue(storedPasswordIs(OLD), "both or neither: no new password without the logout");
        assertEquals(tokenVersionBefore, fromDatabase().getTokenVersion());
    }

    // ------------------------------------------------------------------ issue 68: the lockout

    @Test
    void aWrongOldPasswordIsCountedAndThe401DoesNotUndoIt() {
        assertThrows(UnauthorizedException.class, () -> change("guess-1", NEW));
        assertThrows(UnauthorizedException.class, () -> change("guess-2", NEW));

        assertEquals(2, failedAttempts(), "both wrong old passwords are counted");
        assertFalse(Boolean.TRUE.equals(fromDatabase().getAccountLocked()), "2 is not enough to lock");
    }

    @Test
    void fiveWrongOldPasswordsLockTheAccount() {
        for (int i = 1; i <= 5; i++) {
            int n = i;
            assertThrows(UnauthorizedException.class, () -> change("guess-" + n, NEW));
        }

        User locked = fromDatabase();
        assertTrue(locked.getAccountLocked(), "locked after 5 wrong old passwords");
        assertNotNull(locked.getLockedUntil());
        assertTrue(locked.getLockedUntil().isAfter(LocalDateTime.now().plusMinutes(14)), "locked for about 15 minutes");
    }

    @Test
    void loginAndChangePasswordShareOneCounter() {
        jdbc.update("UPDATE users SET failed_login_attempts = 4 WHERE userid = ?", user.getUserid());   // 4 wrong logins

        assertThrows(UnauthorizedException.class, () -> change("guess", NEW));

        assertTrue(fromDatabase().getAccountLocked(), "the 5th wrong password locks, wherever it was typed");
    }

    @Test
    void aLockedAccountCannotChangeItsPasswordEvenWithTheRightOldOne() {
        jdbc.update("UPDATE users SET account_locked = TRUE, failed_login_attempts = 5, locked_until = ? WHERE userid = ?",
                LocalDateTime.now().plusMinutes(10), user.getUserid());

        refusedAndNothingChanged(UnauthorizedException.class, "ACCOUNT_LOCKED", () -> change(OLD, NEW));
        assertEquals(5, failedAttempts(), "a refused try while locked is not counted again");
    }

    @Test
    void theRightOldPasswordSetsTheCounterBackToZero() {
        jdbc.update("UPDATE users SET failed_login_attempts = 3 WHERE userid = ?", user.getUserid());

        change(OLD, NEW);

        assertEquals(0, failedAttempts());
        assertTrue(storedPasswordIs(NEW));
    }
}
