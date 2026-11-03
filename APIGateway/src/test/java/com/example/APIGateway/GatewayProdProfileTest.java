package com.example.APIGateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * BCR-04 (2026-11-02): the gateway's production settings, used only when it starts with the "prod" profile.
 *
 * 1. Logs at INFO, not DEBUG: DEBUG writes a lot of detail about every request into the log.
 * 2. /actuator moves to its own port, open only to this machine. After deployment nginx passes /api/... to the
 *    gateway's main port, so without this anyone could open /api/actuator/... through nginx.
 *
 * The real application-prod.properties is used. Only the management port is set here (managementport=0 means "any
 * free port"), so the test never clashes with a gateway already running on 8092.
 */
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("prod")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "internalsecret=dummy-test-only",
        "managementport=0"
})
class GatewayProdProfileTest {

    private static final DisposableServer AUTH_SERVICE = HttpServer.create().port(0)
            .route(routes -> routes.post("/auth/login", (request, response) ->
                    response.sendString(Mono.just("ok")).then()))
            .bindNow();

    @DynamicPropertySource
    static void routesToTheStandIn(DynamicPropertyRegistry registry) {
        registry.add("jwtpublickey", TestKeys::publicKeyText);
        // A route list set here REPLACES the one in application.properties (lists are not merged).
        registry.add("spring.cloud.gateway.routes[0].id", () -> "auth-service");
        registry.add("spring.cloud.gateway.routes[0].uri", () -> "http://localhost:" + AUTH_SERVICE.port());
        registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/auth/**");
    }

    @AfterAll
    static void stopTheStandIn() {
        AUTH_SERVICE.disposeNow();
    }

    @Autowired
    WebTestClient client;

    @Autowired
    Environment environment;

    @Autowired
    LoggingSystem loggingSystem;

    // The main port is the one nginx sends /api/... to. /actuator must not answer there.
    @Test
    void actuatorIsNotOnTheMainPort() {
        client.get().uri("/actuator/health").exchange().expectStatus().isNotFound();
        client.get().uri("/actuator/metrics").exchange().expectStatus().isNotFound();
    }

    // Still usable by you, on the server itself, through its own port.
    @Test
    void actuatorWorksOnItsOwnPortFromThisMachine() {
        String mainPort = environment.getProperty("local.server.port");
        String managementPort = environment.getProperty("local.management.port");
        assertNotEquals(mainPort, managementPort, "/actuator must have its own port");
        // A test cannot connect from a second computer, so check the setting that keeps other computers out.
        assertEquals("127.0.0.1", environment.getProperty("management.server.address"),
                "the /actuator port must be open to this machine only");

        WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + managementPort).build()
                .get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void gatewayLogsAreInfoNotDebug(CapturedOutput output) {
        assertEquals(LogLevel.INFO, loggingSystem.getLoggerConfiguration("org.springframework.cloud.gateway")
                .getEffectiveLevel());

        // A request through a route: at DEBUG this wrote about 11 gateway lines.
        client.post().uri("/auth/login").exchange().expectStatus().isOk();
        assertFalse(output.getAll().contains(" DEBUG "), "a DEBUG line was written in prod");
    }
}
