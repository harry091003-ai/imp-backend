package com.example.APIGateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue 49 (2026-10-28): the gateway had NO time limit on routed calls. A slow (not down) service made the
 * caller wait for ever, and the 504 in GatewayExceptionHandler could never fire.
 *
 * The whole gateway runs here, in front of a tiny local test server that answers after N milliseconds
 * (/auth/slow/{ms}). Paths under /auth skip the token check, so no token is needed.
 * The time limit is 1 second here to keep the test fast; the real value is in application.properties and
 * GatewayRoutesTest checks it. The setting NAME is the same, so this proves the real setting works.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "internalsecret=dummy-test-only",
        "spring.cloud.gateway.server.webflux.httpclient.response-timeout=1s"
})
class GatewayRouteTimeoutTest {

    // Answers "ok" after the number of milliseconds in the URL.
    private static final DisposableServer SLOW_SERVICE = HttpServer.create().port(0)
            .route(routes -> routes.get("/auth/{kind}/{ms}", (request, response) ->
                    Mono.delay(Duration.ofMillis(Long.parseLong(request.param("ms"))))
                            .then(response.sendString(Mono.just("ok")).then())))
            .bindNow();

    @DynamicPropertySource
    static void routesToTheSlowService(DynamicPropertyRegistry registry) {
        registry.add("jwtpublickey", TestKeys::publicKeyText);
        String uri = "http://localhost:" + SLOW_SERVICE.port();
        // A route list set here REPLACES the one in application.properties (lists are not merged).
        registry.add("spring.cloud.gateway.routes[0].id", () -> "normal");
        registry.add("spring.cloud.gateway.routes[0].uri", () -> uri);
        registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/auth/normal/**");
        // Like the AI route: its own, longer limit (milliseconds).
        registry.add("spring.cloud.gateway.routes[1].id", () -> "patient");
        registry.add("spring.cloud.gateway.routes[1].uri", () -> uri);
        registry.add("spring.cloud.gateway.routes[1].predicates[0]", () -> "Path=/auth/patient/**");
        registry.add("spring.cloud.gateway.routes[1].metadata.response-timeout", () -> "3000");
    }

    @AfterAll
    static void stopTheSlowService() {
        SLOW_SERVICE.disposeNow();
    }

    @Autowired
    WebTestClient client;

    @Test
    void aQuickAnswerPasses() {
        client.get().uri("/auth/normal/100").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("ok");
    }

    @Test
    void aTooSlowServiceGets504QuicklyInsteadOfAnEndlessWait() {
        long start = System.nanoTime();

        client.mutate().responseTimeout(Duration.ofSeconds(10)).build()
                .get().uri("/auth/normal/4000").exchange()
                .expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
                .expectBody(String.class).value(body -> assertTrue(body.contains("took too long"), body));

        long ms = (System.nanoTime() - start) / 1_000_000;
        assertTrue(ms < 3000, "the gateway gave up after about 1 s, not after the service's 4 s: " + ms + " ms");
    }

    @Test
    void aRouteWithItsOwnLongerLimitWaitsLonger() {
        // 1.5 s is over the 1 s default, but under this route's own 3 s.
        client.mutate().responseTimeout(Duration.ofSeconds(10)).build()
                .get().uri("/auth/patient/1500").exchange()
                .expectStatus().isOk();
    }
}
