package com.example.APIGateway.filter;

import com.example.APIGateway.services.KeyedRateLimiter;
import com.example.APIGateway.services.KeyedRateLimiter.Limit;
import com.example.APIGateway.utils.GatewayErrorWriter;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Logger;

// B5: limit how often one caller can hit the endpoints that cost the most.
//
// Rule "ai" (step 1) - /ai/**, counted per USER.
//   /ai/rag/suggestion calls the PAID Gemini API on every request. One loop could run
//   up a real bill overnight.
//   per minute -> stops a burst.  per day -> caps the bill.
//
// Rule "login" (step 2) - POST /auth/login, counted per IP address.
//   The account lockout stops guessing ONE account's password. It does nothing against
//   "credential stuffing": one guess each against thousands of different accounts,
//   which never trips any single account's lock. This limit catches that.
//
// Rule "signup" (step 2) - POST /auth/signup, counted per IP address.
//   Stops one machine creating accounts in bulk. A real person signs up once.
//
// Why IP and not user id for login/signup: there is no token yet, so no user id.
//
// Rule "default" (step 3) - EVERY other routed path, counted per USER.
//   A safety floor, not a targeted fix. Generous enough that a person never notices it.
//   It only stops a runaway script from loading a service's database and CPU.
//   It is the LAST rule on purpose: a path gets the first rule that covers it, so /ai/**
//   and login/signup keep their own tighter limits and do NOT also spend a default coin.
//
// Over a limit -> 429 Too Many Requests, our normal JSON error, and a Retry-After header.
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger logger = Logger.getLogger(RateLimitFilter.class.getName());

    // Written by JwtGatewayFilter from the VERIFIED token. Anything the caller sent under
    // this name was wiped first, so it can't be faked to dodge the limit.
    private static final String USER_ID_HEADER = "X-User-Id";

    // One rule = which paths it covers + who it counts by + its own buckets and counter.
    // Each rule has SEPARATE buckets, so using up signups never blocks your login.
    private record Rule(String name,
                        Predicate<String> coversPath,
                        boolean countByUser,
                        KeyedRateLimiter limiter,
                        Counter rejected,
                        String whatForMessage) {
    }

    // Checked in order. The first rule that covers the path is used.
    private final List<Rule> rules;

    // Used by Spring.
    @Autowired
    public RateLimitFilter(@Value("${gateway.ratelimit.ai.per-minute}") long aiPerMinute,
                           @Value("${gateway.ratelimit.ai.per-day}") long aiPerDay,
                           @Value("${gateway.ratelimit.login.per-minute}") long loginPerMinute,
                           @Value("${gateway.ratelimit.login.per-hour}") long loginPerHour,
                           @Value("${gateway.ratelimit.signup.per-hour}") long signupPerHour,
                           @Value("${gateway.ratelimit.signup.per-day}") long signupPerDay,
                           @Value("${gateway.ratelimit.default.per-second}") long defaultPerSecond,
                           @Value("${gateway.ratelimit.default.per-minute}") long defaultPerMinute,
                           @Value("${gateway.ratelimit.max-tracked-keys}") long maxTrackedKeys,
                           MeterRegistry meterRegistry) {
        this(new KeyedRateLimiter(List.of(new Limit(aiPerMinute, Duration.ofMinutes(1)),
                                          new Limit(aiPerDay, Duration.ofDays(1))),
                        maxTrackedKeys, TimeMeter.SYSTEM_NANOTIME),
                new KeyedRateLimiter(List.of(new Limit(loginPerMinute, Duration.ofMinutes(1)),
                                             new Limit(loginPerHour, Duration.ofHours(1))),
                        maxTrackedKeys, TimeMeter.SYSTEM_NANOTIME),
                new KeyedRateLimiter(List.of(new Limit(signupPerHour, Duration.ofHours(1)),
                                             new Limit(signupPerDay, Duration.ofDays(1))),
                        maxTrackedKeys, TimeMeter.SYSTEM_NANOTIME),
                new KeyedRateLimiter(List.of(new Limit(defaultPerSecond, Duration.ofSeconds(1)),
                                             new Limit(defaultPerMinute, Duration.ofMinutes(1))),
                        maxTrackedKeys, TimeMeter.SYSTEM_NANOTIME),
                meterRegistry);
    }

    // Used by tests, so they can pass limiters with a fake clock.
    RateLimitFilter(KeyedRateLimiter aiLimiter,
                    KeyedRateLimiter loginLimiter,
                    KeyedRateLimiter signupLimiter,
                    KeyedRateLimiter defaultLimiter,
                    MeterRegistry meterRegistry) {
        this.rules = List.of(
                new Rule("ai", RateLimitFilter::isAiPath, true,
                        aiLimiter, rejectedCounter(meterRegistry, "ai"), "AI requests"),
                new Rule("login", path -> isExactPath(path, "/auth/login"), false,
                        loginLimiter, rejectedCounter(meterRegistry, "login"), "login attempts"),
                new Rule("signup", path -> isExactPath(path, "/auth/signup"), false,
                        signupLimiter, rejectedCounter(meterRegistry, "signup"), "sign-up attempts"),
                // Must stay LAST: it covers every path, so any rule after it would never be reached.
                // Counted by user. The only paths here with no user id are the other public
                // /auth/** ones (token-version) - those fall back to IP, like the "ai" rule does.
                new Rule("default", path -> true, true,
                        defaultLimiter, rejectedCounter(meterRegistry, "default"), "requests"));
    }

    // Shows at /actuator/metrics/gateway.ratelimit.rejected, one tag value per rule.
    // Tag is the RULE name (a fixed value), never a user id or IP - see CLAUDE.md.
    private static Counter rejectedCounter(MeterRegistry registry, String ruleName) {
        return Counter.builder("gateway.ratelimit.rejected")
                .description("Requests refused because the caller went over a rate limit")
                .tag("rule", ruleName)
                .register(registry);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        Rule rule = ruleFor(path);
        if (rule == null) {
            // Can't happen while "default" is in the list. Kept so that removing that rule
            // later means "not limited" instead of a crash.
            return chain.filter(exchange);
        }

        String key = callerKey(exchange.getRequest(), rule.countByUser());
        ConsumptionProbe probe = rule.limiter().tryConsume(key);
        if (probe.isConsumed()) {
            return chain.filter(exchange);
        }

        // Round UP, and never 0: "retry in 0 seconds" would invite an instant retry.
        long retryAfterSeconds = Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);

        rule.rejected().increment();
        String traceId = TraceIdGatewayFilter.currentTraceId(exchange);
        logger.warning("[" + traceId + "] Rate limit hit: rule=" + rule.name() + ", caller=" + key
                + ", retry in " + retryAfterSeconds + "s");

        // Retry-After is the standard header that tells a client how long to wait.
        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        return GatewayErrorWriter.write(exchange, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                "Too many " + rule.whatForMessage() + ". Try again in " + retryAfterSeconds + " seconds.");
    }

    private Rule ruleFor(String path) {
        for (Rule rule : rules) {
            if (rule.coversPath().test(path)) {
                return rule;
            }
        }
        return null;
    }

    // /ai and everything under /ai/. The slash matters: "/airport" is not an AI path.
    private static boolean isAiPath(String path) {
        return path.equals("/ai") || path.startsWith("/ai/");
    }

    // Exactly this path, with or without one slash at the end.
    // Without the second check, "/auth/login/" would be a free way around the limit.
    private static boolean isExactPath(String path, String expected) {
        return path.equals(expected) || path.equals(expected + "/");
    }

    // Who to count this request against.
    //
    // countByUser = true  (ai, default): the user id from the verified token. If there is
    //   none, fall back to IP, so the request is still limited, never let through free.
    //   (For "default" that happens on the public /auth/** paths like token-version.)
    //
    // countByUser = false (login, signup): ALWAYS the IP. There is no token yet.
    //
    // The IP is the address that actually opened the connection to us.
    // We deliberately IGNORE the X-Forwarded-For header: a caller can type anything in it,
    // so trusting it would let them claim a new IP on every request and never be limited.
    private String callerKey(ServerHttpRequest request, boolean countByUser) {
        if (countByUser) {
            String userId = request.getHeaders().getFirst(USER_ID_HEADER);
            if (userId != null && !userId.isBlank()) {
                return "user:" + userId;
            }
        }
        InetSocketAddress remote = request.getRemoteAddress();
        return "ip:" + (remote != null && remote.getAddress() != null
                ? remote.getAddress().getHostAddress() : "unknown");
    }

    // 1 = right AFTER JwtGatewayFilter (-1).
    // It must be after, because the user id the "ai" rule counts by is written by that filter.
    // /auth/** requests also pass through JwtGatewayFilter first (it skips the token check
    // there but still passes them on), so the login and signup rules run here too.
    // RequestSizeFilter (-2) runs before both.
    @Override
    public int getOrder() {
        return 1;
    }
}
