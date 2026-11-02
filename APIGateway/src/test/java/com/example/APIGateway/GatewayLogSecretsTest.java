package com.example.APIGateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * BCR-04 first piece (2026-11-02): the gateway wrote EVERY request header into its log.
 *
 * Found while testing BCR-02: the real application.properties sets the gateway's log level to DEBUG, and at DEBUG
 * Spring Cloud Gateway's ObservedRequestHttpHeadersFilter prints all request headers - the refresh cookie, the
 * Authorization header (the access token) and X-Internal-Secret. Anyone who can read the log could reuse them.
 * Our own filters never did this (they log "header present: true"), so the leak is the framework's DEBUG line.
 *
 * The whole gateway runs here with the REAL logging settings from application.properties (nothing is set for logging in
 * this test), in front of a tiny stand-in AuthService. The test sends a request carrying three distinctive secrets and
 * checks that none of them reaches the console. Paths under /auth skip the token check, so no real token is needed.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false"
})
class GatewayLogSecretsTest {

    private static final String COOKIE_VALUE = "leakcheck-cookie-value-1";
    private static final String BEARER_VALUE = "leakcheck-bearer-value-2";
    private static final String INTERNAL_SECRET = "leakcheck-internal-secret-7";

    private static final DisposableServer AUTH_SERVICE = HttpServer.create().port(0)
            .route(routes -> routes.post("/auth/refresh", (request, response) ->
                    response.sendString(Mono.just("ok")).then()))
            .bindNow();

    @DynamicPropertySource
    static void routesToTheStandIn(DynamicPropertyRegistry registry) {
        registry.add("jwtpublickey", TestKeys::publicKeyText);
        // Set here, not in the annotation: Spring prints the annotation's properties in the test's own start-up log.
        registry.add("internalsecret", () -> INTERNAL_SECRET);
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

    @Test
    void noRequestHeaderSecretReachesTheLog(CapturedOutput output) {
        client.post().uri("/auth/refresh")
                .cookie("imp_refresh", COOKIE_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + BEARER_VALUE)
                .exchange()
                .expectStatus().isOk();

        String log = output.getAll();
        assertFalse(log.contains(COOKIE_VALUE), "the refresh cookie is in the log");
        assertFalse(log.contains(BEARER_VALUE), "the access token is in the log");
        // The gateway adds this header itself on every call to a service: it must not be printed either.
        assertFalse(log.contains(INTERNAL_SECRET), "the internal secret is in the log");
    }
}
