package com.example.AuthService.service;

import com.example.AuthService.config.SecurityConfig;
import com.example.AuthService.dto.LoginFromDTO;
import com.example.AuthService.dto.LoginResponseDTO;
import com.example.AuthService.dto.RefreshResponseDTO;
import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.RoleName;
import com.example.AuthService.entity.User;
import com.example.AuthService.kafka.producer.UserLoggedInEventProducer;
import com.example.AuthService.kafka.producer.UserLoggedOutEventProducer;
import com.example.AuthService.kafka.producer.UserRegisteredEventProducer;
import com.example.AuthService.repository.RoleRepository;
import com.project.outbox.OutboxWriter;
import com.project.web.exception.ApiException;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

/**
 * Auth v2 part B: refresh tokens, on the REAL AuthService code (H2 in MySQL mode, no Kafka).
 * The password check and the JWT signing are faked; everything about refresh tokens is real.
 *
 * Rules:
 *   1. login gives a refresh token; only its hash is stored
 *   2. a refresh gives a new pair and uses the old refresh token up
 *   3. a used token sent again cancels that whole login (and only that login)
 *   4. expired / unknown tokens are refused
 *   5. logout and an admin's forced logout (role change, credentials change) cancel all refresh tokens
 *   6. a locked account cannot refresh, but its token is not used up
 *   7. two refreshes with the same token at the same moment: only one wins
 */
@DataJpaTest(properties = {
        // LOCK_TIMEOUT: in the race test the second refresh waits for the row lock for about 2 s.
        "spring.datasource.url=jdbc:h2:mem:auth-refresh;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:outbox/outbox_event_mysql.sql"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({LoginUserService.class, LoginAttemptService.class, AccessTokenIssuer.class, RefreshTokenService.class,
        LogoutUserService.class, RegisteringUserService.class, UserLoggedInEventProducer.class,
        UserLoggedOutEventProducer.class, UserRegisteredEventProducer.class, RefreshTokenTest.Setup.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // no test-wide transaction: we test the real ones
class RefreshTokenTest {

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
    @Autowired RefreshTokenService refreshTokenService;
    @Autowired LogoutUserService logoutUserService;
    @Autowired RegisteringUserService registeringUserService;
    @Autowired RoleRepository roleRepository;
    @Autowired DataSource dataSource;

    @MockitoBean AuthenticationManager authenticationManager;   // every password is "right"
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean SecurityConfig securityConfig;                 // makes the access token

    private final AtomicInteger accessTokensMade = new AtomicInteger();
    private JdbcTemplate jdbc;
    private User alice;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM refresh_token");
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM roles");
        for (RoleName name : RoleName.values()) {
            roleRepository.save(Role.builder().rolename(name).build());
        }
        doReturn(null).when(authenticationManager).authenticate(any());
        // Each access token is different, so a test can tell an old one from a new one.
        doAnswer(call -> "access-" + accessTokensMade.incrementAndGet()).when(securityConfig).generateToken(any(), any());
        alice = User.builder().username("alice").email("alice@example.com").password("right-password").build();
        registeringUserService.RegisteringTheUser(alice);
    }

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ helpers

    private String login() {
        LoginFromDTO request = new LoginFromDTO();
        request.setUsername("alice");
        request.setEmail("alice@example.com");
        request.setPassword("right-password");
        LoginResponseDTO answer = loginUserService.LoginUser(request).getBody();
        assertNotNull(answer);
        assertNotNull(answer.getJWTtoken(), "login still gives the access token");
        return answer.getRefreshToken();
    }

    /** Refresh, and return the error code if it was refused (null = it worked). */
    private String refusedCode(String refreshToken) {
        try {
            refreshTokenService.refresh(refreshToken);
            return null;
        } catch (ApiException e) {
            return e.getErrorCode();
        }
    }

    private Map<String, Object> rowOf(String refreshToken) {
        return jdbc.queryForMap("SELECT * FROM refresh_token WHERE token_hash = ?", RefreshTokenService.hash(refreshToken));
    }

    /** Tokens of alice that still work: not used, not cancelled. */
    private int activeTokens() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND used_at IS NULL AND revoked_at IS NULL",
                Integer.class, alice.getUserid());
    }

    private void loggedInAsAlice() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(alice.getUserid().toString(), null, List.of()));
    }

    // ================================================================== 1. login

    @Test
    void loginGivesARefreshTokenAndStoresOnlyItsHash() {
        String refreshToken = login();

        assertNotNull(refreshToken);
        assertTrue(refreshToken.length() >= 43, "32 random bytes");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE token_hash = ?",
                Integer.class, refreshToken), "the token itself is never stored");
        Map<String, Object> row = rowOf(refreshToken);
        assertEquals(64, ((String) row.get("TOKEN_HASH")).length());
        LocalDateTime expires = ((java.sql.Timestamp) row.get("EXPIRES_AT")).toLocalDateTime();
        assertTrue(expires.isAfter(LocalDateTime.now().plusDays(6)), "lives about 7 days");
        assertTrue(expires.isBefore(LocalDateTime.now().plusDays(8)));
    }

    @Test
    void everyLoginGetsADifferentToken() {
        assertNotEquals(login(), login());
    }

    // ================================================================== 2. refresh

    @Test
    void refreshGivesANewPairAndUsesUpTheOldToken() {
        String first = login();

        RefreshResponseDTO answer = refreshTokenService.refresh(first);

        assertEquals(alice.getUserid(), answer.getUserid());
        assertEquals("access-2", answer.getJWTtoken(), "a NEW access token (login made access-1)");
        assertNotNull(answer.getRefreshToken());
        assertNotEquals(first, answer.getRefreshToken());
        assertNotNull(rowOf(first).get("USED_AT"), "the old one is used up");
        assertEquals(rowOf(first).get("FAMILY_ID"), rowOf(answer.getRefreshToken()).get("FAMILY_ID"),
                "the new one belongs to the same login");
        assertEquals(1, activeTokens());
    }

    @Test
    void theNewTokenCanBeRefreshedAgain() {
        String first = login();
        String second = refreshTokenService.refresh(first).getRefreshToken();
        String third = refreshTokenService.refresh(second).getRefreshToken();

        assertNotNull(third);
        assertEquals(1, activeTokens());
    }

    // ================================================================== 3. reuse

    @Test
    void reusingAUsedTokenCancelsTheWholeLogin() {
        String first = login();
        String second = refreshTokenService.refresh(first).getRefreshToken();

        // someone sends the old one again: a copy of it exists somewhere
        assertEquals("REFRESH_TOKEN_REUSED", refusedCode(first));

        // the cancel must have been SAVED (a 401 thrown inside the transaction would roll it back)
        assertNotNull(rowOf(second).get("REVOKED_AT"), "the newest token of that login is cancelled too");
        assertEquals("INVALID_REFRESH_TOKEN", refusedCode(second));
        assertEquals(0, activeTokens());
    }

    @Test
    void reuseCancelsOnlyThatLoginNotTheOtherDevice() {
        String phone = login();
        String laptop = login();
        refreshTokenService.refresh(phone);

        assertEquals("REFRESH_TOKEN_REUSED", refusedCode(phone));

        assertNull(refusedCode(laptop), "a different login (another device) still works");
    }

    // ================================================================== 4. expired, unknown

    @Test
    void anExpiredTokenIsRefused() {
        String token = login();
        jdbc.update("UPDATE refresh_token SET expires_at = ? WHERE token_hash = ?",
                LocalDateTime.now().minusMinutes(1), RefreshTokenService.hash(token));

        assertEquals("REFRESH_TOKEN_EXPIRED", refusedCode(token));
    }

    @Test
    void anUnknownTokenIsRefused() {
        login();
        UnauthorizedException error = assertThrows(UnauthorizedException.class,
                () -> refreshTokenService.refresh("made-up-token"));
        assertEquals("INVALID_REFRESH_TOKEN", error.getErrorCode());
        assertEquals(1, activeTokens(), "nothing changed");
    }

    // ================================================================== 5. logout cancels

    @Test
    void logoutCancelsEveryRefreshToken() {
        String phone = login();
        String laptop = login();
        loggedInAsAlice();

        logoutUserService.LogoutUser();

        assertEquals("INVALID_REFRESH_TOKEN", refusedCode(phone));
        assertEquals("INVALID_REFRESH_TOKEN", refusedCode(laptop));
        assertEquals(0, activeTokens());
    }

    @Test
    void anAdminsForcedLogoutCancelsEveryRefreshToken() {
        // LogoutUserByAdmin is what a role change and a credentials change call
        String token = login();

        logoutUserService.LogoutUserByAdmin(alice.getUserid());

        assertEquals("INVALID_REFRESH_TOKEN", refusedCode(token));
    }

    @Test
    void aLoginAfterLogoutWorksAgain() {
        login();
        loggedInAsAlice();
        logoutUserService.LogoutUser();

        assertNull(refusedCode(login()));
    }

    // ================================================================== 6. locked account

    @Test
    void aLockedAccountCannotRefreshButTheTokenIsKept() {
        String token = login();
        jdbc.update("UPDATE users SET account_locked = TRUE, locked_until = ? WHERE email = ?",
                LocalDateTime.now().plusMinutes(10), "alice@example.com");

        assertEquals("ACCOUNT_LOCKED", refusedCode(token));
        assertNull(rowOf(token).get("USED_AT"), "not used up");

        jdbc.update("UPDATE users SET locked_until = ? WHERE email = ?",
                LocalDateTime.now().minusMinutes(1), "alice@example.com");
        assertNull(refusedCode(token), "works again once the lock has ended");
    }

    // ================================================================== 7. two at once

    @Test
    void twoRefreshesWithTheSameTokenAtOnceOnlyOneWins() throws Exception {
        String token = login();

        // Hold each refresh inside its transaction (while making the access token) until the other one is there
        // too, or 2 s have passed. Without the row lock both get there, both swap the token: two live copies.
        CyclicBarrier bothInside = new CyclicBarrier(2);
        doAnswer(call -> {
            try {
                bothInside.await(2, TimeUnit.SECONDS);
            } catch (Exception waitedLongEnough) {
                // the other refresh is not coming (it is waiting for the lock): go on alone
            }
            return "access-" + accessTokensMade.incrementAndGet();
        }).when(securityConfig).generateToken(any(), any());

        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> one = pool.submit(() -> { go.await(); return String.valueOf(refusedCode(token)); });
            Future<String> two = pool.submit(() -> { go.await(); return String.valueOf(refusedCode(token)); });
            go.countDown();
            List<String> results = new ArrayList<>(List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS)));
            results.sort(String::compareTo);

            assertEquals(List.of("REFRESH_TOKEN_REUSED", "null"), results,
                    "one swaps it, the other finds it already used");
            assertEquals(0, activeTokens(), "the second call counts as reuse: that login is cancelled");
        } finally {
            pool.shutdownNow();
        }
    }
}
