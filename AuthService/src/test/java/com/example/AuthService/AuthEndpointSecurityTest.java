package com.example.AuthService;

import com.example.AuthService.controller.RoleController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security batch, 2026-10-28. The whole AuthService in the test world (H2, no Kafka, no Eureka, dummy secret).
 *
 * Issue 11: GET /auth/token-version/{id} was open to anybody. Only the gateway needs it, so now it needs
 *   the internal secret AND X-Internal-Service (the gateway's own service call). The gateway wipes
 *   X-Internal-Service off every outside request, so nobody outside can make that claim.
 * Issue 10: AuthService had no @EnableMethodSecurity, so @PreAuthorize on RoleController did nothing.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-endpoint-security;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class AuthEndpointSecurityTest {

    private static final String SECRET = "dummy-test-only";

    @DynamicPropertySource
    static void testSigningKey(DynamicPropertyRegistry registry) {
        registry.add("jwtprivatekey", TestKeys::privateKeyText);
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    RoleController roleController;

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    private static String tokenVersionUrl() {
        return "/auth/token-version/" + UUID.randomUUID();
    }

    // ---------------------------------------------------------------- issue 11

    @Test
    void tokenVersionWithNoHeadersIsRefused() throws Exception {
        mvc.perform(get(tokenVersionUrl())).andExpect(status().isForbidden());
    }

    @Test
    void tokenVersionFromOutsideThroughTheGatewayIsRefused() throws Exception {
        // What the gateway forwards for an outside caller on /auth/**: the secret, but no service name.
        mvc.perform(get(tokenVersionUrl()).header("X-Internal-Secret", SECRET))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenVersionForALoggedInPersonIsRefused() throws Exception {
        mvc.perform(get(tokenVersionUrl()).header("X-Internal-Secret", SECRET)
                        .header("X-User-Id", UUID.randomUUID().toString()).header("X-Roles", "ROLE_ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenVersionWithAWrongSecretIsRefused() throws Exception {
        mvc.perform(get(tokenVersionUrl()).header("X-Internal-Secret", "wrong")
                        .header("X-Internal-Service", "APIGATEWAY"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theGatewaysOwnCallGetsThrough() throws Exception {
        // An unknown user id answers 404: the request passed the security check and reached the controller.
        mvc.perform(get(tokenVersionUrl()).header("X-Internal-Secret", SECRET)
                        .header("X-Internal-Service", "APIGATEWAY"))
                .andExpect(status().isNotFound());
    }

    @Test
    void loginSignupAndRefreshAreStillOpen() throws Exception {
        // An empty body answers 400 (validation): not blocked by security, which would be 403.
        for (String path : List.of("/auth/login", "/auth/signup")) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }
        // BCR-02: refresh reads a cookie, not a body. With no cookie the controller itself answers 401
        // INVALID_REFRESH_TOKEN - it was reached, so security did not block it.
        mvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"));
    }

    // ---------------------------------------------------------------- issue 10

    @Test
    void preAuthorizeOnRoleControllerReallyRuns() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ENGINEER"))));

        // Called directly, past the URL rule: only @PreAuthorize can refuse it now.
        assertThrows(AccessDeniedException.class, () -> roleController.UpdateUserRoles("pat", null));
    }
}
