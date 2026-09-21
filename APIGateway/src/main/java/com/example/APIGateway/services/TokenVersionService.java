package com.example.APIGateway.services;

import com.example.APIGateway.utils.FailureDescription;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Checks if a user is still logged in (their token has not been revoked).
 *
 * A JWT cannot be deleted once issued. So each user has a "tokenVersion"
 * number in the database. Logging out increases that number - a token
 * minted before that still carries the old number, so it stops matching.
 * This class fetches the CURRENT number.
 *
 * It runs on every request, so it needs a cache and a way to survive
 * AuthService being slow or down. Three tiers, best to worst:
 *
 *   TIER 1  cached, under 30s old   -> use it, no network call
 *   LIVE    cache empty/old, asked AuthService, got a reply
 *   TIER 2  AuthService did not reply, cache is under 5 min old
 *           -> use it anyway, log a WARNING
 *   TIER 3  AuthService did not reply, nothing usable cached
 *           -> skip the check this time, log SEVERE
 *
 * Signature and expiry are checked BEFORE this class runs (in
 * JwtGatewayFilter) and need no network - so even in Tier 3 a fake or
 * expired token is still rejected. Only the EXTRA "was this revoked
 * recently" check is skipped.
 *
 * Safety rule for any fallback: give it a LIMIT (5 min here) and always
 * LOG it. Never silent, never forever.
 *
 * LATER: if we ever run more than one gateway, add Redis BEHIND this
 * cache (not instead of it), so every gateway shares the same answers.
 */
@Service
public class TokenVersionService {

    private static final Logger logger = Logger.getLogger(TokenVersionService.class.getName());

    private final WebClient webClient;

    /**
     * How long a cached value counts as "fresh" (Tier 1).
     * 30s and not 60s: going higher barely saves more calls, but doubles
     * how long a logged-out token can still work.
     */
    private final long freshMs;

    /**
     * Holds each user's last known token version.
     * Entries auto-expire after the "stale ceiling" (5 min), so an entry
     * being present at all already means it is within Tier 2's limit.
     * maximumSize stops this growing forever.
     */
    private final Cache<String, CachedVersion> cache;

    /**
     * Stops us calling AuthService at all while it is clearly broken.
     *
     * Without this, every request still waits the full 2-second timeout
     * before falling back. The breaker notices "these keep failing", then
     * fails instantly instead - so the fallback happens in microseconds.
     *
     * It has 3 states:
     *   CLOSED     normal. Calls go through.
     *   OPEN       too many recent failures. Calls are refused instantly,
     *              without touching the network.
     *   HALF_OPEN  after the wait time, let a few calls through to test
     *              whether AuthService is back. Good -> CLOSED, bad -> OPEN.
     */
    private final CircuitBreaker circuitBreaker;

    // Tally counters, one per tier. The number lives inside each Counter.
    // We add 1 with .increment() each time that tier answers a request.
    // View at: /actuator/metrics/gateway.tokenversion.lookups
    // Healthy system: stale_cache and unavailable stay at 0.
    //counters are created
    private final Counter freshCacheHits;
    private final Counter liveCalls;
    private final Counter staleFallbacks;
    private final Counter unavailableFallbacks;

    public TokenVersionService(
            WebClient.Builder builder,
            CircuitBreakerRegistry circuitBreakerRegistry,
            MeterRegistry meterRegistry,
            @Value("${gateway.tokenversion.fresh-ms}") long freshMs,
            @Value("${gateway.tokenversion.stale-ceiling-ms}") long staleCeilingMs,
            @Value("${gateway.tokenversion.max-cached-users}") int maxCachedUsers,
            @Value("${gateway.circuitbreaker.sliding-window-size}") int slidingWindowSize,
            @Value("${gateway.circuitbreaker.minimum-calls}") int minimumCalls,
            @Value("${gateway.circuitbreaker.failure-rate-percent}") float failureRatePercent,
            @Value("${gateway.circuitbreaker.open-duration-ms}") long openDurationMs,
            @Value("${gateway.circuitbreaker.half-open-test-calls}") int halfOpenTestCalls) {

        this.webClient = builder.baseUrl("http://AUTHSERVICE").build();
        this.freshMs = freshMs;

        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMillis(staleCeilingMs))
                .maximumSize(maxCachedUsers)
                .build();
//counter are registered
        // Create the 4 counters (all start at 0) and register them.
        this.freshCacheHits       = lookupCounter(meterRegistry, "fresh_cache");
        this.liveCalls            = lookupCounter(meterRegistry, "live");
        this.staleFallbacks       = lookupCounter(meterRegistry, "stale_cache");
        this.unavailableFallbacks = lookupCounter(meterRegistry, "unavailable");

        // Cache size. A gaugestores no number. It reads cache.estimatedSize() each time someone ask and reports that. It can go down too.
        Gauge.builder("gateway.tokenversion.cache.size", cache, Cache::estimatedSize)
                .description("Users currently held in the token-version cache")
                .register(meterRegistry);

        CircuitBreakerConfig breakerConfig = CircuitBreakerConfig.custom()
                // Judge on the last N calls, not on a time window.
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(slidingWindowSize)

                // Do not judge at all until we have seen this many calls.
                // Without it, the very first failure would be "100% failing"
                // and the breaker would open on a single blip.
                .minimumNumberOfCalls(minimumCalls)

                .failureRateThreshold(failureRatePercent)
                .waitDurationInOpenState(Duration.ofMillis(openDurationMs))
                .permittedNumberOfCallsInHalfOpenState(halfOpenTestCalls)

                // IMPORTANT: a 404 ("no such user") must NOT count as a failure.
                // AuthService is healthy and answering correctly - it is the
                // USER that is gone. Counting it would let a burst of old
                // bookmarked links trip the breaker for no real reason.
                .ignoreExceptions(AuthUserNotFoundException.class)

                .build();

        // Use the registry, not CircuitBreaker.of(). Same breaker, but the
        // registry tells the metrics code about it. With .of() the breaker
        // works, but never shows up in /actuator.
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(
                "authservice-token-version", breakerConfig);

        // Log every state change. This is the line that tells you the breaker
        // actually did something - otherwise it works silently and you cannot
        // tell "AuthService is fine" from "we stopped asking".
        this.circuitBreaker.getEventPublisher().onStateTransition(event ->
                logger.warning("CIRCUIT BREAKER (authservice-token-version): "
                        + event.getStateTransition()));

        logger.info("TokenVersionService ready - fresh for " + freshMs + "ms, usable as a"
                + " fallback for up to " + staleCeilingMs + "ms, max " + maxCachedUsers
                + " users cached. Circuit breaker opens at " + failureRatePercent
                + "% failures over the last " + slidingWindowSize + " calls (min "
                + minimumCalls + "), stays open " + openDurationMs + "ms.");
    }

    /**
     * One saved answer: the version number, and when we saved it.
     * (record = simple data holder; version() and savedAtNanos() are
     * auto-generated getters.)
     *
     * We use System.nanoTime(), not the wall clock, because the wall
     * clock can jump backwards (e.g. a clock sync). nanoTime only ever
     * moves forward, so "how long ago" always stays correct.
     */
    private record CachedVersion(int version, long savedAtNanos) {

        long ageMs() {
            return (System.nanoTime() - savedAtNanos) / 1_000_000L;
        }
    }

    /**
     * Gets the user's current token version - from cache, live, or a
     * fallback. Only throws when AuthService gives a clear "no such user"
     * answer. A slow or down AuthService never throws here - it falls
     * back instead, which is the whole point of this class.
     */
    public Mono<TokenVersionResult> getTokenVersion(String userid) {

        // TIER 1: do we already have a recent enough answer?
        // getIfPresent = look up by key, returns null if missing/expired.
        CachedVersion cached = cache.getIfPresent(userid);

        if (cached != null && cached.ageMs() < freshMs) {
            freshCacheHits.increment();
            // FINE, not INFO - this fires on almost every request.
            logger.fine("Token version from fresh cache for " + userid
                    + " (age " + cached.ageMs() + "ms)");
            return Mono.just(new TokenVersionResult(cached.version(),
                    TokenVersionResult.Source.FRESH_CACHE));
        }

        // Cache missed or was too old - ask AuthService for real.
        return callAuthService(userid)
                .map(version -> {
                    cache.put(userid, new CachedVersion(version, System.nanoTime()));
                    liveCalls.increment();
                    logger.info("Token version " + version + " fetched live for " + userid);
                    return new TokenVersionResult(version, TokenVersionResult.Source.LIVE);
                })
                // Catches the call FAILING. A "no such user" answer is NOT
                // caught here - it is re-thrown below instead, so a
                // deleted user can never fall into the fallback tiers.
                .onErrorResume(error -> handleFailure(userid, error));
    }

    /**
     * The actual call to AuthService.
     * Timeouts and the connection pool are set up in WebClientConfig.
     * The circuit breaker is applied at the bottom of this method.
     */
    private Mono<Integer> callAuthService(String userid) {
        return webClient.get()
                .uri("/auth/token-version/{userid}", userid)
                .retrieve()
                .onStatus(status -> status.isError(), response -> {

                    // 404 = AuthService is FINE, it is just telling us this
                    // user does not exist. Treat it as a real answer, not a
                    // failure - otherwise Tier 3 would think AuthService is
                    // down and let a deleted user's token straight through.
                    if (response.statusCode() == HttpStatus.NOT_FOUND) {
                        logger.warning("AuthService says user does not exist: " + userid);
                        return Mono.error(new AuthUserNotFoundException(userid));
                    }

                    logger.severe("AuthService returned error status: " + response.statusCode());
                    return Mono.error(new RuntimeException(
                            "AuthService returned " + response.statusCode()));
                })
                .bodyToMono(Integer.class)

                // The circuit breaker wraps ONLY this call - not the cache
                // lookup and not the fallback tiers. When it is OPEN, this
                // throws CallNotPermittedException straight away, which lands
                // in handleFailure() like any other failure and goes to the
                // Tier 2 / Tier 3 fallback - just without the 2-second wait.
                //
                // transformDeferred, not transform: it must apply per
                // subscription, so each request gets its own pass through
                // the breaker rather than sharing one from startup.
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }

    /**
     * AuthService did not answer. Work out which fallback applies.
     */
    private Mono<TokenVersionResult> handleFailure(String userid, Throwable error) {

        // "No such user" is a real answer, not something to fall back
        // from. Let it through to the filter, which turns it into a 401.
        // (instanceof checks the actual type of the object at runtime.)
        if (error instanceof AuthUserNotFoundException) {
            return Mono.error(error);
        }

        String why = FailureDescription.of(error);

        // TIER 2: if an entry is present at all, it is under 5 min old -
        // the cache already dropped anything older. So no separate age
        // check is needed here.
        CachedVersion cached = cache.getIfPresent(userid);

        if (cached != null) {
            staleFallbacks.increment();
            long count = (long) staleFallbacks.count();
            logger.warning("STALE CACHE FALLBACK #" + count + " - AuthService did not answer,"
                    + " using a " + cached.ageMs() + "ms old token version for " + userid
                    + ". Revocation IS still being checked, just against slightly old data."
                    + " Cause: " + why);
            return Mono.just(new TokenVersionResult(cached.version(),
                    TokenVersionResult.Source.STALE_CACHE));
        }

        // TIER 3: nothing usable. Skip the check this time, log SEVERE so
        // it is never invisible.
        unavailableFallbacks.increment();
        long count = (long) unavailableFallbacks.count();
        logger.log(Level.SEVERE, "REVOCATION CHECK SKIPPED #" + count + " - AuthService did not"
                + " answer and nothing usable is cached for " + userid + ". The token's"
                + " signature and expiry were still verified. Cause: " + why, error);

        return Mono.just(new TokenVersionResult(null, TokenVersionResult.Source.UNAVAILABLE));
    }

    /**
     * Makes one tally counter and registers it with the MeterRegistry.
     * All four share one name and differ only by the "tier" label.
     * Only use labels with a few fixed values. Never a user id - every new
     * value creates a new metric.
     */
    private static Counter lookupCounter(MeterRegistry registry, String tier) {
        return Counter.builder("gateway.tokenversion.lookups")
                .description("Token-version lookups, by which tier answered")
                .tag("tier", tier)
                .register(registry);
    }
}
