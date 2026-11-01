package com.example.APIGateway.services;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue 11 (2026-10-28): AuthService now answers /auth/token-version only to an internal service.
 * So the gateway's call must carry the internal secret AND X-Internal-Service, or every token check
 * would fail and fall back to the tiers. No network: the request is caught before it leaves.
 */
class TokenVersionServiceHeadersTest {

    @Test
    void theTokenVersionCallSaysItIsTheGateway() {
        AtomicReference<ClientRequest> sent = new AtomicReference<>();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            sent.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("3").build());
        });
        TokenVersionService service = new TokenVersionService(builder, CircuitBreakerRegistry.ofDefaults(),
                new SimpleMeterRegistry(), 30_000, 300_000, 1_000,
                10, 5, 50f, 10_000, 2, "the-internal-secret");

        String userid = UUID.randomUUID().toString();
        TokenVersionResult result = service.getTokenVersion(userid).block();

        assertEquals(3, result.version());
        ClientRequest request = sent.get();
        assertEquals("/auth/token-version/" + userid, request.url().getPath());
        assertEquals("the-internal-secret", request.headers().getFirst("X-Internal-Secret"));
        assertEquals("APIGATEWAY", request.headers().getFirst("X-Internal-Service"));
    }
}
