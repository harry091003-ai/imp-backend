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
}
