package com.example.APIGateway.filter;

import com.example.APIGateway.services.AuthUserNotFoundException;
import com.example.APIGateway.services.TokenVersionResult;
import com.example.APIGateway.services.TokenVersionResult.Source;
import com.example.APIGateway.services.TokenVersionService;
import com.example.APIGateway.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Checks how JwtGatewayFilter handles errors.
//
// The bug (found during B5 step 3): the error catch covered the WHOLE chain, not just the
// token-version lookup. So a routing failure (the service is down) came back as
// "503 AUTH_SERVICE_UNAVAILABLE - Unable to verify the session", and the log blamed AuthService.
//
// These tests: a failure AFTER the token check must travel up unchanged, while the
// lookup's own failures must still get exactly the answers they got before.
class JwtGatewayFilterTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final TokenVersionService tokenVersionService = mock(TokenVersionService.class);
    private final JwtGatewayFilter filter = new JwtGatewayFilter(jwtUtil, tokenVersionService, "test-secret");

    @BeforeEach
    void aValidTokenForUser1WithVersion1() {
        when(jwtUtil.validateToken(anyString())).thenReturn(true);
        when(jwtUtil.getSubjectFromToken(anyString())).thenReturn("user-1");
        when(jwtUtil.getEmailFromToken(anyString())).thenReturn("user1@test.com");
        when(jwtUtil.getRolesFromToken(anyString())).thenReturn(List.of("ROLE_ENGINEER"));
        when(jwtUtil.getTokenVersion(anyString())).thenReturn(1);
    }

    private MockServerWebExchange request() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/incidents").header("Authorization", "Bearer any-token").build());
    }

    private void lookupAnswers(Integer version, Source source) {
        when(tokenVersionService.getTokenVersion("user-1"))
                .thenReturn(Mono.just(new TokenVersionResult(version, source)));
    }

    private void lookupFails(Throwable error) {
        when(tokenVersionService.getTokenVersion("user-1")).thenReturn(Mono.error(error));
    }

    // THE BUG. The token is fine; then routing fails because the service is down.
    // That error must come back up unchanged - NOT be relabelled as an AuthService problem.
    @Test
    void failureAfterTheTokenCheckIsNotBlamedOnAuthService() {
        lookupAnswers(1, Source.LIVE);
        ResponseStatusException routingFailure = new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Unable to find instance for INCIDENTSERVICE");
        GatewayFilterChain chain = ex -> Mono.error(routingFailure);

        MockServerWebExchange exchange = request();
        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> filter.filter(exchange, chain).block());

        assertSame(routingFailure, thrown, "the routing error must travel up unchanged");
        assertNull(exchange.getResponse().getStatusCode(),
                "the JWT filter must not write its own error for a failure that is not its business");
    }

    // The token-version LOOKUP itself fails unexpectedly -> still 503 AUTH_SERVICE_UNAVAILABLE.
    @Test
    void lookupFailureStillGives503() {
        lookupFails(new IllegalStateException("something unexpected in the lookup"));
        MockServerWebExchange exchange = request();

        filter.filter(exchange, ex -> Mono.empty()).block();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        assertTrue(exchange.getResponse().getBodyAsString().block().contains("AUTH_SERVICE_UNAVAILABLE"));
    }

    // AuthService says 404 (the user was deleted) -> still 401 TOKEN_REVOKED.
    @Test
    void deletedUserStillGets401() {
        lookupFails(new AuthUserNotFoundException("user-1"));
        MockServerWebExchange exchange = request();

        filter.filter(exchange, ex -> Mono.empty()).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertTrue(exchange.getResponse().getBodyAsString().block().contains("TOKEN_REVOKED"));
    }

    // Token version 1, AuthService says 2 -> revoked -> 401, and the request never goes on.
    @Test
    void revokedTokenStillGets401() {
        lookupAnswers(2, Source.LIVE);
        MockServerWebExchange exchange = request();
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, ex -> { forwarded.set(ex); return Mono.empty(); }).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertNull(forwarded.get(), "a revoked token must not be forwarded");
    }

    // Normal case: versions match -> forwarded, with the gateway's own identity headers.
    @Test
    void validTokenIsForwardedWithIdentityHeaders() {
        lookupAnswers(1, Source.LIVE);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(request(), ex -> { forwarded.set(ex); return Mono.empty(); }).block();

        assertNotNull(forwarded.get());
        assertEquals("user-1", forwarded.get().getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("test-secret", forwarded.get().getRequest().getHeaders().getFirst("X-Internal-Secret"));
    }

    // Tier 3 (AuthService down, nothing cached) -> still forwarded, as designed in B3.
    @Test
    void unavailableTierStillForwards() {
        lookupAnswers(null, Source.UNAVAILABLE);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(request(), ex -> { forwarded.set(ex); return Mono.empty(); }).block();

        assertNotNull(forwarded.get());
    }
}
