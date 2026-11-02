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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BCR-01 (2026-11-02): the real client IP behind nginx.
 *
 * With nginx in front, every connection to the gateway comes FROM nginx, so the login, signup and refresh limits
 * (counted per IP) were shared by every visitor: one bot using up 10 logins a minute locked everyone out.
 *
 * Here the whole gateway runs in front of a stand-in AuthService. This test connects from this machine, so it plays
 * the part of nginx: the setting names 127.0.0.1 and ::1 as the trusted proxy, and each request carries the
 * X-Real-IP that nginx would write. The login limit is lowered to 2 a minute to keep the test short.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "internalsecret=dummy-test-only",
        "gateway.trusted-proxies=127.0.0.1, ::1",
        "gateway.ratelimit.login.per-minute=2"
})
class GatewayClientIpTest {

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

    private HttpStatus loginFrom(String visitorIp) {
        return HttpStatus.valueOf(client.post().uri("/auth/login")
                .header("X-Real-IP", visitorIp)
                .exchange()
                .returnResult(String.class)
                .getStatus()
                .value());
    }

    // Before the fix: Alice's 2 logins used up the ONE shared bucket, so Bob got 429 on his first try.
    @Test
    void twoVisitorsBehindTheProxyEachGetTheirOwnLoginLimit() {
        assertEquals(HttpStatus.OK, loginFrom("198.51.100.1"), "Alice, try 1");
        assertEquals(HttpStatus.OK, loginFrom("198.51.100.1"), "Alice, try 2");
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, loginFrom("198.51.100.1"), "Alice, try 3: over her own limit");

        assertEquals(HttpStatus.OK, loginFrom("198.51.100.2"), "Bob, try 1: Alice's limit must not block him");
    }
}
