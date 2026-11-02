package com.example.AuthService.controller;

import com.example.AuthService.TestKeys;
import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.RoleName;
import com.example.AuthService.entity.User;
import com.example.AuthService.repository.RoleRepository;
import com.example.AuthService.service.RefreshTokenService;
import com.example.AuthService.service.RegisteringUserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BCR-02 (2026-11-02): the refresh token travels in an HttpOnly cookie, never in JSON.
 *
 * Why: a browser page had to keep the 7-day refresh token somewhere JavaScript can read (memory is lost on reload,
 * localStorage and sessionStorage can be read by an injected script, and a duplicated tab copied it, so the next
 * refresh looked like reuse and logged the user out). An HttpOnly cookie is kept by the browser, sent only to
 * /auth/refresh, and can not be read by any script. Full reasoning: the frontend's docs/bcr-02-plan.md.
 *
 * The whole AuthService in the test world (H2, no Kafka, no Eureka, dummy secret), over real HTTP requests.
 * RefreshTokenService itself is unchanged; RefreshTokenTest still covers its rules.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-refresh-cookie;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:outbox/outbox_event_mysql.sql",
        "eureka.client.enabled=false",
        "internalsecret=dummy-test-only",
        "spring.kafka.bootstrap-servers=localhost:1"
})
@AutoConfigureMockMvc
class RefreshCookieTest {

    private static final String SECRET = "dummy-test-only";
    private static final String COOKIE = "imp_refresh";
    private static final String PASSWORD = "right-password";
    // Every flag the cookie must carry (the defaults in application.properties).
    private static final List<String> FLAGS = List.of(
            "Path=/api/auth/refresh", "Max-Age=604800", "HttpOnly", "Secure", "SameSite=Strict");

    @DynamicPropertySource
    static void testSigningKey(DynamicPropertyRegistry registry) {
        registry.add("jwtprivatekey", TestKeys::privateKeyText);
    }

    @Autowired MockMvc mvc;
    @Autowired RegisteringUserService registeringUserService;
    @Autowired RoleRepository roleRepository;
    @Autowired DataSource dataSource;
    // Real code, watched: proves a refused cookie never reaches the hashing and the database.
    @MockitoSpyBean RefreshTokenService refreshTokenService;

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
        alice = User.builder().username("alice").email("alice@example.com").password(PASSWORD).build();
        registeringUserService.RegisteringTheUser(alice);
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions login(String password) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"email\":\"alice@example.com\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions refreshWith(String cookieValue) throws Exception {
        return mvc.perform(post("/auth/refresh").cookie(new Cookie(COOKIE, cookieValue)));
    }

    private static String setCookieOf(MvcResult result) {
        return result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    }

    /** The token inside "imp_refresh=<token>; Path=...". */
    private static String tokenIn(String setCookie) {
        assertNotNull(setCookie, "a Set-Cookie header");
        assertTrue(setCookie.startsWith(COOKIE + "="), setCookie);
        return setCookie.substring(COOKIE.length() + 1, setCookie.indexOf(';'));
    }

    private String loginAndGetToken() throws Exception {
        return tokenIn(setCookieOf(login(PASSWORD).andExpect(status().isOk()).andReturn()));
    }

    private static void assertHasEveryFlag(String setCookie) {
        for (String flag : FLAGS) {
            assertTrue(setCookie.contains(flag), "missing " + flag + " in: " + setCookie);
        }
    }

    private static void assertCleared(String setCookie) {
        assertNotNull(setCookie, "the cookie must be cleared");
        assertTrue(setCookie.startsWith(COOKIE + "=;"), "empty value: " + setCookie);
        assertTrue(setCookie.contains("Max-Age=0"), setCookie);
        // A browser only deletes a cookie whose path matches the one it was set with.
        assertTrue(setCookie.contains("Path=/api/auth/refresh"), setCookie);
    }

    // ================================================================== login

    @Test
    void loginSetsTheRefreshCookieWithEveryFlag() throws Exception {
        String setCookie = setCookieOf(login(PASSWORD).andExpect(status().isOk()).andReturn());

        assertTrue(tokenIn(setCookie).length() >= 43, "32 random bytes");
        assertHasEveryFlag(setCookie);
    }

    @Test
    void loginJsonNoLongerCarriesTheRefreshToken() throws Exception {
        login(PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void theAccessTokensJsonKeyIsJwttoken() throws Exception {
        // Not new behaviour: this pins the key a client must read. The Java field is JWTtoken, but Jackson
        // names it from the getter getJWTtoken(), which gives "jwttoken". Renaming the field breaks clients.
        login(PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.jwttoken").isString())
                .andExpect(jsonPath("$.JWTtoken").doesNotExist());
    }

    @Test
    void aFailedLoginSetsNoCookie() throws Exception {
        login("wrong-password").andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token", Integer.class));
    }

    // ================================================================== refresh

    @Test
    void refreshWithTheCookieGivesANewAccessTokenAndANewCookie() throws Exception {
        String first = loginAndGetToken();

        MvcResult result = refreshWith(first).andExpect(status().isOk())
                .andExpect(jsonPath("$.jwttoken").isString())
                .andExpect(jsonPath("$.userid").value(alice.getUserid().toString()))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String setCookie = setCookieOf(result);
        assertNotEquals(first, tokenIn(setCookie), "rotation: a new token");
        assertHasEveryFlag(setCookie);
    }

    @Test
    void refreshWithNoCookieIs401NotAValidationError() throws Exception {
        mvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"));
        verify(refreshTokenService, never()).refresh(any());
    }

    @Test
    void aTokenInTheBodyIsIgnoredSoTheOldWayIsClosed() throws Exception {
        String token = loginAndGetToken();

        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + token + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"));

        refreshWith(token).andExpect(status().isOk());   // it was not used up by the refused call
    }

    @Test
    void anOversizedCookieIsRefusedBeforeItIsHashed() throws Exception {
        // Same limit the old body had (@Size(max = 200)): a huge value never reaches SHA-256 or the database.
        MvcResult result = refreshWith("a".repeat(201))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"))
                .andReturn();

        verify(refreshTokenService, never()).refresh(any());
        assertCleared(setCookieOf(result));
    }

    @Test
    void anUnknownCookieIsRefusedAndCleared() throws Exception {
        MvcResult result = refreshWith("made-up-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"))
                .andReturn();

        assertCleared(setCookieOf(result));
    }

    @Test
    void reuseIsRefusedTheLoginStaysCancelledAndTheCookieIsCleared() throws Exception {
        String first = loginAndGetToken();
        String second = tokenIn(setCookieOf(refreshWith(first).andExpect(status().isOk()).andReturn()));

        MvcResult result = refreshWith(first)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("REFRESH_TOKEN_REUSED"))
                .andReturn();

        assertCleared(setCookieOf(result));
        refreshWith(second).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void aLockedAccountIsRefusedButKeepsItsCookie() throws Exception {
        String token = loginAndGetToken();
        jdbc.update("UPDATE users SET account_locked = TRUE, locked_until = ? WHERE email = ?",
                LocalDateTime.now().plusMinutes(10), "alice@example.com");

        // The token is not used up by a lock, so it works again when the lock ends: clearing it would
        // force a new login for nothing.
        refreshWith(token).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("ACCOUNT_LOCKED"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        jdbc.update("UPDATE users SET locked_until = ? WHERE email = ?",
                LocalDateTime.now().minusMinutes(1), "alice@example.com");
        refreshWith(token).andExpect(status().isOk());
    }

    // ================================================================== logout

    @Test
    void logoutClearsTheCookieAndTheTokenIsDead() throws Exception {
        String token = loginAndGetToken();

        MvcResult result = mvc.perform(post("/logout/user")
                        .header("X-Internal-Secret", SECRET)
                        .header("X-User-Id", alice.getUserid().toString())
                        .header("X-Roles", "ROLE_ENGINEER"))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = setCookieOf(result);
        assertCleared(setCookie);
        assertTrue(setCookie.contains("HttpOnly") && setCookie.contains("Secure"), setCookie);
        refreshWith(token).andExpect(status().isUnauthorized());
    }
}
