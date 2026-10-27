package com.example.AuthService.service;

import com.example.AuthService.config.SecurityConfig;
import com.example.AuthService.dto.LoginFromDTO;
import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.RoleName;
import com.example.AuthService.entity.User;
import com.example.AuthService.kafka.producer.UserLoggedInEventProducer;
import com.example.AuthService.kafka.producer.UserRegisteredEventProducer;
import com.example.AuthService.repository.RoleRepository;
import com.example.AuthService.repository.UserRepository;
import com.project.outbox.OutboxWriter;
import com.project.web.exception.UnauthorizedException;
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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Issue 55 and 6: the lockout. Real LoginUserService, in-memory database, faked password check.
 * Written first: 5 wrong passwords left the counter at 0 ("expected 5 but was 0").
 * The other tests check the unlock rules.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-lockout;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:outbox/outbox_event_mysql.sql"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({LoginUserService.class, LoginAttemptService.class, AccessTokenIssuer.class, RefreshTokenService.class,
        RegisteringUserService.class,
        UserLoggedInEventProducer.class, UserRegisteredEventProducer.class, AccountLockoutTest.Setup.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // no test-wide transaction: we test the real ones
class AccountLockoutTest {

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

    @Autowired LoginUserService loginUserService;
    @Autowired RegisteringUserService registeringUserService;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired DataSource dataSource;

    @MockitoBean AuthenticationManager authenticationManager;   // decides: right or wrong password
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean SecurityConfig securityConfig;                 // makes the token

    private JdbcTemplate jdbc;

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
        doReturn("test-token").when(securityConfig).generateToken(any(), any());
        registeringUserService.RegisteringTheUser(
                User.builder().username("alice").email("alice@example.com").password("right-password").build());
    }

    // ------------------------------------------------------------------ helpers

    private static LoginFromDTO login(String password) {
        LoginFromDTO request = new LoginFromDTO();
        request.setUsername("alice");
        request.setEmail("alice@example.com");
        request.setPassword(password);
        return request;
    }

    /** One login with a wrong password. Returns the error code it got back. */
    private String wrongPassword() {
        doThrow(new BadCredentialsException("bad")).when(authenticationManager).authenticate(any());
        return assertThrows(UnauthorizedException.class,
                () -> loginUserService.LoginUser(login("wrong-password"))).getErrorCode();
    }

    /** One login with the right password. */
    private void rightPassword() {
        doReturn(null).when(authenticationManager).authenticate(any());
        loginUserService.LoginUser(login("right-password"));
    }

    /** The row as it is IN THE DATABASE (not a Java object that may still hold unsaved changes). */
    private Map<String, Object> row() {
        return jdbc.queryForMap("SELECT failed_login_attempts, account_locked, locked_until FROM users WHERE email = ?",
                "alice@example.com");
    }

    private LocalDateTime lockedUntil() {
        Object value = row().get("LOCKED_UNTIL");
        return value == null ? null : ((Timestamp) value).toLocalDateTime();
    }

    /** Pretend time has passed: move the end of the lock into the past. */
    private void theLockTimeHasPassed() {
        jdbc.update("UPDATE users SET locked_until = ? WHERE email = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), "alice@example.com");
    }

    private int failedAttempts() {
        return ((Number) row().get("FAILED_LOGIN_ATTEMPTS")).intValue();
    }

    private boolean locked() {
        return Boolean.TRUE.equals(row().get("ACCOUNT_LOCKED")) || "1".equals(String.valueOf(row().get("ACCOUNT_LOCKED")));
    }

    // ------------------------------------------------------------------ tests

    @Test
    void eachWrongPasswordIsCountedInTheDatabase() {
        wrongPassword();
        wrongPassword();
        wrongPassword();

        assertEquals(3, failedAttempts(), "every failed login is counted, even though the login itself fails");
        assertFalse(locked(), "3 is not enough to lock");
    }

    @Test
    void fiveWrongPasswordsLockTheAccountInTheDatabase() {
        for (int i = 0; i < 5; i++) {
            assertEquals("INVALID_CREDENTIALS", wrongPassword());
        }

        assertEquals(5, failedAttempts());
        assertTrue(locked(), "5 wrong passwords lock the account");
        LocalDateTime end = lockedUntil();
        assertNotNull(end, "the lock has an end time");
        assertTrue(end.isAfter(LocalDateTime.now().plusMinutes(14)) && end.isBefore(LocalDateTime.now().plusMinutes(16)),
                "locked for about 15 minutes, was until " + end);
    }

    @Test
    void aLockedAccountIsRejectedEvenWithTheRightPassword() {
        for (int i = 0; i < 5; i++) {
            wrongPassword();
        }
        doReturn(null).when(authenticationManager).authenticate(any());

        UnauthorizedException error = assertThrows(UnauthorizedException.class,
                () -> loginUserService.LoginUser(login("right-password")));

        assertEquals("ACCOUNT_LOCKED", error.getErrorCode());
        verify(authenticationManager, times(5)).authenticate(any());   // the 6th try never reached the password check
    }

    @Test
    void afterTheLockTimeTheRightPasswordWorksAndEverythingIsReset() {
        for (int i = 0; i < 5; i++) {
            wrongPassword();
        }
        theLockTimeHasPassed();

        rightPassword();   // no exception = logged in

        assertEquals(0, failedAttempts());
        assertFalse(locked());
        assertNull(lockedUntil());
    }

    @Test
    void afterTheLockTimeOneWrongPasswordDoesNotLockAgainAtOnce() {
        for (int i = 0; i < 5; i++) {
            wrongPassword();
        }
        theLockTimeHasPassed();

        assertEquals("INVALID_CREDENTIALS", wrongPassword(), "a normal wrong-password answer, not 'locked'");

        assertEquals(1, failedAttempts(), "the counter started again from 0");
        assertFalse(locked());
    }

    @Test
    void anOldLockWithNoEndTimeIsReleased() {
        // An account locked BEFORE this fix: locked, but no end time. It used to stay locked forever (issue 6).
        jdbc.update("UPDATE users SET account_locked = TRUE, failed_login_attempts = 5, locked_until = NULL WHERE email = ?",
                "alice@example.com");

        rightPassword();

        assertEquals(0, failedAttempts());
        assertFalse(locked());
    }

    @Test
    void aRightPasswordSetsTheCounterBackToZero() {
        wrongPassword();
        wrongPassword();
        wrongPassword();

        rightPassword();

        assertEquals(0, failedAttempts());
    }

    @Test
    void anUnknownEmailGetsTheSameAnswerAndChangesNothing() {
        doThrow(new BadCredentialsException("bad")).when(authenticationManager).authenticate(any());
        LoginFromDTO request = login("wrong-password");
        request.setEmail("nobody@example.com");

        UnauthorizedException error = assertThrows(UnauthorizedException.class, () -> loginUserService.LoginUser(request));

        assertEquals("INVALID_CREDENTIALS", error.getErrorCode(), "same answer as a wrong password: do not reveal which emails exist");
        assertEquals(0, failedAttempts(), "alice is not touched");
    }
}
