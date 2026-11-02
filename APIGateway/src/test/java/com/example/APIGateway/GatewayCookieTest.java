package com.example.APIGateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/**
 * BCR-02 (2026-11-02): AuthService now sends the refresh token as an HttpOnly cookie and reads it back from the
 * Cookie header. No gateway code changed for this, so this test guards that the gateway keeps passing both
 * headers through untouched: a filter that dropped Cookie or Set-Cookie would log every browser user out after
 * 15 minutes, and no other test would notice.
 *
 * The whole gateway runs here, in front of a tiny stand-in AuthService that answers with the Cookie header it
 * received and sets a new cookie. Paths under /auth skip the token check, so no access token is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "internalsecret=dummy-test-only"
})
class GatewayCookieTest {

    private static final String NEW_COOKIE =
            "imp_refresh=new-token; Path=/api/auth/refresh; Max-Age=604800; Secure; HttpOnly; SameSite=Strict";

    private static final DisposableServer AUTH_SERVICE = HttpServer.create().port(0)
            .route(routes -> routes.post("/auth/refresh", (request, response) ->
                    response.header(HttpHeaders.SET_COOKIE, NEW_COOKIE)
                            .sendString(Mono.just(String.valueOf(request.requestHeaders().get(HttpHeaders.COOKIE))))
                            .then()))
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

    @Test
    void theCookieReachesAuthServiceAndTheNewCookieComesBack() {
        client.post().uri("/auth/refresh").cookie("imp_refresh", "old-token").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.SET_COOKIE, NEW_COOKIE)
                .expectBody(String.class).isEqualTo("imp_refresh=old-token");
    }
}
