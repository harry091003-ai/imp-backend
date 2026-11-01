package com.example.APIGateway;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Step 7: the gateway sends /notifications/** to NotificationService.
 * Reads the REAL route list from application.properties (no Spring start).
 */
class GatewayRoutesTest {

    private static Properties props;

    @BeforeAll
    static void readTheRealSettings() throws Exception {
        props = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
    }

    /** All routes as "uri | predicate", in order. */
    private static List<String[]> routes() {
        List<String[]> routes = new ArrayList<>();
        for (int i = 0; props.getProperty("spring.cloud.gateway.routes[" + i + "].id") != null; i++) {
            routes.add(new String[]{
                    props.getProperty("spring.cloud.gateway.routes[" + i + "].id"),
                    props.getProperty("spring.cloud.gateway.routes[" + i + "].uri"),
                    props.getProperty("spring.cloud.gateway.routes[" + i + "].predicates[0]")});
        }
        return routes;
    }

    @Test
    void notificationsGoToNotificationService() {
        String[] route = routes().stream().filter(r -> r[2] != null && r[2].contains("/notifications/**"))
                .findFirst().orElseThrow(() -> new AssertionError("no gateway route for /notifications/**"));

        // The name after lb:// must be the service's spring.application.name (NotificationService), like the others.
        assertEquals("lb://NotificationService", route[1]);
        assertEquals("Path=/notifications/**", route[2]);
    }

    @Test
    void onlyOneRouteClaimsNotifications() {
        long claims = routes().stream().filter(r -> r[2] != null && r[2].contains("/notifications")).count();
        assertEquals(1, claims, "two routes for the same path would make the result depend on their order");
    }

    @Test
    void theExistingRoutesAreStillThere() {
        List<String> uris = routes().stream().map(r -> r[1]).toList();
        assertTrue(uris.containsAll(List.of("lb://AUTHSERVICE", "lb://USERSERVICE", "lb://IncidentService",
                "lb://ReportingService", "lb://AiService")), uris.toString());
    }

    // ---------------- Issue 49 (2026-10-28): every routed call has a time limit ----------------
    // GatewayRouteTimeoutTest proves these setting names really work; here we check the REAL values.

    private static long routeTimeoutMs(String routeId) {
        for (int i = 0; props.getProperty("spring.cloud.gateway.routes[" + i + "].id") != null; i++) {
            if (routeId.equals(props.getProperty("spring.cloud.gateway.routes[" + i + "].id"))) {
                String own = props.getProperty("spring.cloud.gateway.routes[" + i + "].metadata.response-timeout");
                return own == null ? -1 : Long.parseLong(own.trim());
            }
        }
        throw new AssertionError("no route " + routeId);
    }

    @Test
    void everyRoutedCallHasATimeLimit() {
        String limit = props.getProperty("spring.cloud.gateway.server.webflux.httpclient.response-timeout");
        assertNotNull(limit, "without it a slow service makes the caller wait for ever");
        assertEquals("10s", limit.trim());
        assertEquals("2000", props.getProperty("spring.cloud.gateway.server.webflux.httpclient.connect-timeout").trim());
    }

    @Test
    void theAiRouteGetsALongerLimit() {
        // A Gemini answer can take 10-30 seconds: 10 s would cut off normal AI calls.
        assertEquals(60_000, routeTimeoutMs("ai-service"));
    }

    @Test
    void theOtherRoutesUseTheDefault() {
        for (String id : List.of("auth-service", "user-service", "incident-service", "report-service", "notification-service")) {
            assertEquals(-1, routeTimeoutMs(id), id + " should use the 10 s default");
        }
    }
}
