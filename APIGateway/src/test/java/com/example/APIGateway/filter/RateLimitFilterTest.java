package com.example.APIGateway.filter;

import com.example.APIGateway.services.KeyedRateLimiter;
import com.example.APIGateway.services.KeyedRateLimiter.Limit;
import io.github.bucket4j.TimeMeter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

// B5 steps 1-3: checks RateLimitFilter's 4 rules (ai, login, signup, default).
// "passed" = the request went on to the next filter. Otherwise it was refused here.
class RateLimitFilterTest {

    // A clock we move by hand, so a test can "wait 12 seconds" instantly.
    private final AtomicLong nowNanos = new AtomicLong(0);
    private final TimeMeter fakeClock = new TimeMeter() {
        @Override
        public long currentTimeNanos() {
            return nowNanos.get();
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }
    };

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private KeyedRateLimiter limiter(long first, Duration firstPeriod, long second, Duration secondPeriod) {
        return new KeyedRateLimiter(
                List.of(new Limit(first, firstPeriod), new Limit(second, secondPeriod)), 1000, fakeClock);
    }

    // Same numbers as application.properties, except the ai ones, which each test picks.
    private RateLimitFilter filter(long aiPerMinute, long aiPerDay) {
        return new RateLimitFilter(
                limiter(aiPerMinute, Duration.ofMinutes(1), aiPerDay, Duration.ofDays(1)),
                limiter(10, Duration.ofMinutes(1), 100, Duration.ofHours(1)),   // login
                limiter(5, Duration.ofHours(1), 20, Duration.ofDays(1)),        // signup
                limiter(20, Duration.ofSeconds(1), 300, Duration.ofMinutes(1)), // default
                registry);
    }

    private MockServerWebExchange get(String path, String userId) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).header("X-User-Id", userId).build());
    }

    private RateLimitFilter defaultFilter() {
        return filter(5, 100);
    }

    private MockServerWebExchange aiRequest(String userId) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/ai/rag/suggestion").header("X-User-Id", userId).build());
    }

    private MockServerWebExchange post(String path, String ip) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.post(path).remoteAddress(new InetSocketAddress(ip, 50000)).build());
    }

    // Runs the filter. Returns true if the request was let through.
    private boolean run(RateLimitFilter filter, MockServerWebExchange exchange) {
        AtomicBoolean passed = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            passed.set(true);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return passed.get();
    }

    private void advanceSeconds(long seconds) {
        nowNanos.addAndGet(Duration.ofSeconds(seconds).toNanos());
    }

    // ---------------- Step 1: /ai/** per user ----------------

    @Test
    void fiveRequestsPassThenSixthIs429() {
        RateLimitFilter filter = defaultFilter();
        for (int i = 1; i <= 5; i++) {
            assertTrue(run(filter, aiRequest("user-a")), "request " + i + " should pass");
        }
        MockServerWebExchange sixth = aiRequest("user-a");
        assertFalse(run(filter, sixth));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, sixth.getResponse().getStatusCode());
    }

    @Test
    void rejectionUsesOurJsonShapeAndRetryAfter() {
        RateLimitFilter filter = defaultFilter();
        for (int i = 0; i < 5; i++) run(filter, aiRequest("user-a"));

        MockServerWebExchange rejected = aiRequest("user-a");
        run(filter, rejected);

        String body = rejected.getResponse().getBodyAsString().block();
        assertTrue(body.contains("\"status\":429"), body);
        assertTrue(body.contains("\"error\":\"RATE_LIMITED\""), body);
        assertTrue(body.contains("Too many AI requests"), body);
        assertTrue(body.contains("\"traceId\":"), body);
        // 5 per minute = 1 token every 12 seconds.
        assertEquals("12", rejected.getResponse().getHeaders().getFirst("Retry-After"));
    }

    // One user hitting the limit must not block anyone else.
    @Test
    void otherUsersAreNotAffected() {
        RateLimitFilter filter = defaultFilter();
        for (int i = 0; i < 6; i++) run(filter, aiRequest("user-a"));
        assertTrue(run(filter, aiRequest("user-b")));
    }

    // Tokens come back over time: after 12 seconds, exactly one more request fits.
    @Test
    void oneTokenComesBackAfterTwelveSeconds() {
        RateLimitFilter filter = defaultFilter();
        for (int i = 0; i < 5; i++) run(filter, aiRequest("user-a"));
        assertFalse(run(filter, aiRequest("user-a")));

        advanceSeconds(12);
        assertTrue(run(filter, aiRequest("user-a")));
        assertFalse(run(filter, aiRequest("user-a")));
    }

    // The daily cap still holds when the per-minute limit has plenty left.
    @Test
    void dailyCapHoldsEvenAfterTheMinutePasses() {
        RateLimitFilter filter = filter(100, 3);
        for (int i = 0; i < 3; i++) assertTrue(run(filter, aiRequest("user-a")));
        assertFalse(run(filter, aiRequest("user-a")));

        advanceSeconds(60);   // the per-minute limit is long refilled...
        assertFalse(run(filter, aiRequest("user-a")));   // ...but the day limit is not
    }

    // A path that only STARTS with the letters "ai" is not an AI path.
    // (It gets the generous default rule instead, so 3 requests easily pass.)
    @Test
    void pathLikeAirportIsNotAnAiPath() {
        RateLimitFilter filter = filter(1, 100);
        for (int i = 0; i < 3; i++) {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/airport").header("X-User-Id", "user-a").build());
            assertTrue(run(filter, exchange));
        }
    }

    // Safety net: with no user id, the request is still limited (by IP), not let through free.
    @Test
    void missingUserIdIsStillLimited() {
        RateLimitFilter filter = filter(2, 100);
        for (int i = 0; i < 2; i++) {
            assertTrue(run(filter, MockServerWebExchange.from(MockServerHttpRequest.get("/ai/rag/suggestion").build())));
        }
        assertFalse(run(filter, MockServerWebExchange.from(MockServerHttpRequest.get("/ai/rag/suggestion").build())));
    }

    // ---------------- Step 2: login and signup per IP ----------------

    @Test
    void tenLoginsPassThenEleventhIs429() {
        RateLimitFilter filter = defaultFilter();
        for (int i = 1; i <= 10; i++) {
            assertTrue(run(filter, post("/auth/login", "10.0.0.1")), "login " + i + " should pass");
        }
        MockServerWebExchange eleventh = post("/auth/login", "10.0.0.1");
        assertFalse(run(filter, eleventh));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, eleventh.getResponse().getStatusCode());
        assertTrue(eleventh.getResponse().getBodyAsString().block().contains("Too many login attempts"));
    }

    // One IP being blocked must not block a different IP.
    @Test
    void loginLimitIsPerIp() {
        RateLimitFilter filter = defaultFilter();
        for (int i = 0; i < 11; i++) run(filter, post("/auth/login", "10.0.0.1"));
        assertTrue(run(filter, post("/auth/login", "10.0.0.2")));
    }

    // Credential stuffing: 1 try per account. Every try comes from the same IP, so the IP
    // limit stops it, even though no single account ever gets near its lockout.
    @Test
    void manyDifferentAccountsFromOneIpAreStillLimited() {
        RateLimitFilter filter = defaultFilter();
        for (int i = 0; i < 10; i++) {
            // In real life each of these would carry a different email in the body.
            assertTrue(run(filter, post("/auth/login", "10.0.0.1")));
        }
        assertFalse(run(filter, post("/auth/login", "10.0.0.1")));
    }

    // The hourly cap: a script that waits out the per-minute limit is still held back.
    // 20 minutes x 10 tries. The minute limit alone would let all 200 through.
    // The hourly limit allows 100, plus 1 more every 36 seconds (100 per hour):
    // about 31 more in these 19 minutes -> about 131.
    @Test
    void loginHourlyCapHoldsEvenWhenTheMinuteRefills() {
        RateLimitFilter filter = defaultFilter();
        int passed = 0;
        for (int minute = 0; minute < 20; minute++) {
            for (int i = 0; i < 10; i++) {
                if (run(filter, post("/auth/login", "10.0.0.1"))) passed++;
            }
            advanceSeconds(60);
        }
        assertTrue(passed >= 128 && passed <= 133, "passed = " + passed);
    }

    // Faking X-Forwarded-For must not give the caller a fresh bucket.
    @Test
    void xForwardedForHeaderIsIgnored() {
        RateLimitFilter filter = defaultFilter();
        for (int i = 0; i < 10; i++) run(filter, post("/auth/login", "10.0.0.1"));

        var faked = MockServerWebExchange.from(MockServerHttpRequest.post("/auth/login")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 50000))
                .header("X-Forwarded-For", "203.0.113.99")
                .build());
        assertFalse(run(filter, faked));
    }

    // Login is counted by IP only. A made-up X-User-Id must not give a fresh bucket.
    // (The gateway also wipes this header on /auth/**, but the rule must not depend on that.)
    @Test
    void loginIgnoresUserIdHeader() {
        RateLimitFilter filter = defaultFilter();
        for (int i = 0; i < 10; i++) run(filter, post("/auth/login", "10.0.0.1"));

        var faked = MockServerWebExchange.from(MockServerHttpRequest.post("/auth/login")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 50000))
                .header("X-User-Id", "someone-new")
                .build());
        assertFalse(run(filter, faked));
    }

    // A trailing slash is not a way around the limit.
    @Test
    void trailingSlashCountsAsTheSamePath() {
        RateLimitFilter filter = defaultFilter();
        for (int i = 0; i < 10; i++) run(filter, post("/auth/login", "10.0.0.1"));
        assertFalse(run(filter, post("/auth/login/", "10.0.0.1")));
    }

    @Test
    void fiveSignupsPassThenSixthIs429() {
        RateLimitFilter filter = defaultFilter();
        for (int i = 1; i <= 5; i++) {
            assertTrue(run(filter, post("/auth/signup", "10.0.0.1")), "signup " + i + " should pass");
        }
        MockServerWebExchange sixth = post("/auth/signup", "10.0.0.1");
        assertFalse(run(filter, sixth));
        assertTrue(sixth.getResponse().getBodyAsString().block().contains("Too many sign-up attempts"));
        // 5 per hour = 1 token every 12 minutes = 720 seconds.
        assertEquals("720", sixth.getResponse().getHeaders().getFirst("Retry-After"));
    }

    // Separate buckets: using up signups must not block logging in, and the other way round.
    @Test
    void loginAndSignupHaveSeparateBuckets() {
        RateLimitFilter filter = defaultFilter();
        for (int i = 0; i < 6; i++) run(filter, post("/auth/signup", "10.0.0.1"));
        assertTrue(run(filter, post("/auth/login", "10.0.0.1")));
    }

    // ---------------- Step 3: the default rule, everything else ----------------

    // 20 at once pass (more than any page load), the 21st in the same second is refused.
    @Test
    void defaultAllowsTwentyAtOnceThenRefuses() {
        RateLimitFilter filter = defaultFilter();
        for (int i = 1; i <= 20; i++) {
            assertTrue(run(filter, get("/incidents", "user-a")), "request " + i + " should pass");
        }
        MockServerWebExchange refused = get("/incidents", "user-a");
        assertFalse(run(filter, refused));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, refused.getResponse().getStatusCode());
        assertTrue(refused.getResponse().getBodyAsString().block().contains("Too many requests"));
        assertEquals("1", refused.getResponse().getHeaders().getFirst("Retry-After"));
    }

    // The per-second limit refills fast: one second later, a full 20 fit again.
    @Test
    void defaultRefillsEverySecond() {
        RateLimitFilter filter = defaultFilter();
        for (int i = 0; i < 21; i++) run(filter, get("/incidents", "user-a"));

        advanceSeconds(1);
        for (int i = 0; i < 20; i++) assertTrue(run(filter, get("/incidents", "user-a")));
    }

    // A script sending 20 every second for a whole minute (1200 tries).
    // The per-second limit alone would let all 1200 through.
    // The per-minute limit allows 300, plus 5 more every second: about 595.
    @Test
    void defaultPerMinuteCapHoldsASteadyScript() {
        RateLimitFilter filter = defaultFilter();
        int passed = 0;
        for (int second = 0; second < 60; second++) {
            for (int i = 0; i < 20; i++) {
                if (run(filter, get("/incidents", "user-a"))) passed++;
            }
            advanceSeconds(1);
        }
        assertTrue(passed >= 590 && passed <= 600, "passed = " + passed);
    }

    // Counted per user: one user's script never slows anyone else down.
    @Test
    void defaultIsPerUser() {
        RateLimitFilter filter = defaultFilter();
        for (int i = 0; i < 21; i++) run(filter, get("/incidents", "user-a"));
        assertTrue(run(filter, get("/incidents", "user-b")));
    }

    // All the other paths share ONE default bucket per user - hopping between
    // services is not a way to get more requests.
    @Test
    void defaultIsSharedAcrossServices() {
        RateLimitFilter filter = defaultFilter();
        for (int i = 0; i < 10; i++) run(filter, get("/incidents", "user-a"));
        for (int i = 0; i < 10; i++) run(filter, get("/users/me", "user-a"));
        assertFalse(run(filter, get("/reports/metrics", "user-a")));
    }

    // First rule that covers a path wins. /ai/** has its own bucket, so using up the
    // default bucket does not block AI, and AI requests never spend default coins.
    @Test
    void aiAndDefaultBucketsAreSeparate() {
        RateLimitFilter filter = defaultFilter();
        for (int i = 0; i < 21; i++) run(filter, get("/incidents", "user-a"));
        assertTrue(run(filter, aiRequest("user-a")));

        RateLimitFilter fresh = defaultFilter();
        for (int i = 0; i < 6; i++) run(fresh, aiRequest("user-b"));
        assertTrue(run(fresh, get("/incidents", "user-b")));
    }

    // The other public /auth paths (token-version) have no user id, so the default rule
    // counts them by IP.
    @Test
    void otherAuthPathsUseTheDefaultByIp() {
        RateLimitFilter filter = defaultFilter();
        for (int i = 0; i < 20; i++) {
            assertTrue(run(filter, MockServerWebExchange.from(MockServerHttpRequest.get("/auth/token-version/abc")
                    .remoteAddress(new InetSocketAddress("10.0.0.1", 50000)).build())));
        }
        assertFalse(run(filter, MockServerWebExchange.from(MockServerHttpRequest.get("/auth/token-version/abc")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 50000)).build())));
        assertTrue(run(filter, MockServerWebExchange.from(MockServerHttpRequest.get("/auth/token-version/abc")
                .remoteAddress(new InetSocketAddress("10.0.0.2", 50000)).build())));
    }

    // ---------------- Metrics ----------------

    // Every refusal is counted, per rule.
    @Test
    void rejectionsAreCountedPerRule() {
        RateLimitFilter filter = filter(1, 100);
        run(filter, aiRequest("user-a"));
        run(filter, aiRequest("user-a"));
        run(filter, aiRequest("user-a"));
        for (int i = 0; i < 11; i++) run(filter, post("/auth/login", "10.0.0.1"));
        for (int i = 0; i < 23; i++) run(filter, get("/incidents", "user-a"));

        assertEquals(2.0, registry.get("gateway.ratelimit.rejected").tag("rule", "ai").counter().count());
        assertEquals(1.0, registry.get("gateway.ratelimit.rejected").tag("rule", "login").counter().count());
        assertEquals(0.0, registry.get("gateway.ratelimit.rejected").tag("rule", "signup").counter().count());
        assertEquals(3.0, registry.get("gateway.ratelimit.rejected").tag("rule", "default").counter().count());
    }
}
