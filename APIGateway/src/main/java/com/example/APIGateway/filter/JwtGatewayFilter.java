package com.example.APIGateway.filter;
import com.example.APIGateway.services.AuthUserNotFoundException;
import com.example.APIGateway.services.TokenVersionResult;
import com.example.APIGateway.services.TokenVersionService;
import com.example.APIGateway.utils.FailureDescription;
import com.example.APIGateway.utils.GatewayErrorWriter;
import com.example.APIGateway.utils.JwtUtil;
import jakarta.validation.constraints.Email;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


//1. Request comes to Gateway
//2. Check if public → skip
//3. Check Authorization header
//4. Extract token
//5. Validate token (expiry + signature)
//6. Extract email, roles, tokenVersion
//7. Call AuthService → get latest version
//8. Compare versions
//9. If valid → add headers
//10. Forward request
//11. Else → 401

@Component
//GlobalFilter → runs for every request in API Gateway
//Ordered → defines execution priority
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final TokenVersionService tokenVersionService;

    // ADDED 2026-09-02 (Track B1, step 2): the secret handshake.
    //
    // We send this word with every request we pass on. Each service checks it before it believes
    // the X-User-Id and X-Roles headers we add below.
    //
    // Why we need it: those headers are just text. Anybody who can reach a service directly could
    // type "X-Roles: ROLE_ADMIN" and be believed. Step 1 stopped outsiders reaching the services at
    // all. This is the second lock: even if someone does reach one, they cannot fake being a user
    // without knowing this word.
    //
    // No default value on purpose. If the setting is missing the gateway refuses to start, which is
    // much safer than starting up and quietly sending no secret at all.
    private final String internalSecret;
    private static final Logger logger = Logger.getLogger(JwtGatewayFilter.class.getName());

    // Must match TraceIdFilter.TRACE_ID_HEADER in common-web. The gateway cannot share that
    // class: common-web is Spring MVC (servlet) and this app is WebFlux (reactive), so the
    // constant is duplicated on purpose rather than dragging the servlet stack in here.
    private static final String TRACE_ID_HEADER = GatewayErrorWriter.TRACE_ID_HEADER;

    public JwtGatewayFilter(JwtUtil jwtUtil,
                            TokenVersionService tokenVersionService,
                            @Value("${internal.secret}") String internalSecret) {
        this.jwtUtil = jwtUtil;
        this.tokenVersionService = tokenVersionService;
        this.internalSecret = internalSecret;
    }

    /** The header name we put the secret word in. Must match HeaderAuthFilter in common-security. */
    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    /**
     * Headers we wipe off every incoming request before we add our own.
     *
     * The first three are the identity headers we set ourselves from the token.
     *
     * X-Internal-Service was added 2026-09-02 (Track B1, step 3) for a different reason: it is
     * how one of OUR services tells another "I am a service, not a person", which grants the
     * ROLE_INTERNAL_SERVICE role and opens the /internal/** endpoints.
     *
     * A caller from outside must never be able to claim that. They would also need the secret
     * word, which they do not have - but wiping it costs nothing and means the claim cannot even
     * be made. Two things would have to go wrong instead of one.
     */
    private static final String[] IDENTITY_HEADERS =
            { "X-User-Id", "X-Roles", "X-User-Email", "X-Internal-Service" };


//    ServerWebExchange → request + response (like HttpServletRequest in servlet filters)
//GatewayFilterChain → next filter / service call
//Mono<Void> is  async (non-blocking)
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {


        String path = exchange.getRequest().getURI().getPath();

        // Establish the traceId as early as possible so that even a rejected request has one,
        // and so the SAME id is forwarded downstream - one id covers the whole call chain.
        String traceId = resolveTraceId(exchange);
        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);

        logger.info("[" + traceId + "] 🔍 JwtGatewayFilter processing path: " + path);

        // ✅ 1. Skip public endpoints
        if (path.startsWith("/auth")) {
            logger.info("✅ Public endpoint detected (/auth/**) - Skipping authentication");
            // CHANGED 2026-09-02 (Track B1, step 2).
            // We still skip the token check here - login and signup have no token yet, by
            // definition. But we no longer pass the request on untouched:
            //
            //   1. We wipe any X-User-Id / X-Roles the caller sent. Nothing under /auth reads
            //      those today, so this is not fixing a live hole. It stops a future one: if
            //      somebody later adds a page under /auth that trusts them, it is already safe.
            //   2. We add the secret word, so these requests look the same as every other one we
            //      send. One consistent rule is easier to reason about than two.
            return chain.filter(
                    exchange.mutate()
                            .request(builder -> stripIdentityHeaders(builder)
                                    .header(INTERNAL_SECRET_HEADER, internalSecret)
                                    .header(TRACE_ID_HEADER, traceId))
                            .build());
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        logger.info("📋 Authorization header present: " + (authHeader != null));

        // ✅ 2. Check header
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.severe("❌ Missing or invalid Authorization header format");
            return onError(exchange, "MISSING_TOKEN",
                    "Authorization header is missing or is not a Bearer token.", HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);
        logger.info("✅ Token extracted from Authorization header");

        //try-catch handles synchronous (immediate) errors like :
        // ✔ Token parsing errors
        //✔ Invalid signature
        //✔ Expired token exceptions
        //try-catch did not handle
        //tokenVersionService.getTokenVersion(Email) as it returns: Mono<Integer> (where Mono<> or FLUX is  async (non-blocking))
        try {

            // ✅ 3. VALIDATE TOKEN FIRST (CRITICAL)
            if (!jwtUtil.validateToken(token)) {
                logger.severe("❌ Token validation failed - expired or invalid");
                return onError(exchange, "INVALID_TOKEN",
                        "The token is invalid or has expired.", HttpStatus.UNAUTHORIZED);
            }
            logger.info("✅ Token validated successfully");

            // ✅ 4. Extract claims AFTER validation
            String Userid = jwtUtil.getSubjectFromToken(token);
            String Useremail=jwtUtil.getEmailFromToken(token);
            List<String> roles = jwtUtil.getRolesFromToken(token);
            Integer tokenVersion = jwtUtil.getTokenVersion(token);

            logger.info("✅ Claims extracted - Userid: " + Userid + ", Roles: " + roles+ " TOKEN VERSION: "+tokenVersion);

            System.out.println("JwtGatewayFilter executed");
            // ✅ 5. Get latest version from AuthService
            //
            // FIXED 2026-09-25: catch errors from the LOOKUP only.
            // Before, .onErrorResume(...) was attached AFTER chain.filter(...). So it also caught
            // every error from further down (routing, the service being down) and called all of
            // them "AUTH_SERVICE_UNAVAILABLE". A down IncidentService was blamed on AuthService.
            //
            // materialize() turns the lookup's answer - a value OR an error - into a normal
            // value (a "Signal") that we can look at. So:
            //   the lookup's own errors  -> handled here, in lookupFailed(...)
            //   errors from chain.filter -> NOT caught here; they go up to
            //                               GatewayExceptionHandler, which reports them honestly
            return tokenVersionService.getTokenVersion(Userid)
                    .materialize()
                    .flatMap(signal -> {

                        if (signal.isOnError()) {
                            return lookupFailed(exchange, traceId, signal.getThrowable());
                        }
                        TokenVersionResult result = signal.get();
                        if (result == null) {
                            // The lookup finished with no answer at all. Should never happen.
                            return lookupFailed(exchange, traceId,
                                    new IllegalStateException("Token-version lookup returned no answer"));
                        }

                        // means jab tokenversion jo authservice se lekar ana tha: but auth service is down
                        //then tier 1 and 2  bahi nahi valid hoye so tier 3 run keya jisse token version ki value null hai but still ham usko allow kar rahe hai  for availbality with safety
                        if (!result.canCheckRevocation()) {
                            // Already logged at SEVERE inside TokenVersionService, with the
                            // reason and a running count. Not logged twice here.
                            logger.warning("[" + traceId + "] Proceeding WITHOUT a revocation"
                                    + " check - signature and expiry were still verified");

                        } else if (!tokenVersion.equals(result.version())) {
                            logger.severe("❌ Token version mismatch - token revoked");
                            return onError(exchange, "TOKEN_REVOKED",
                                    "This token is no longer valid. Please log in again.", HttpStatus.UNAUTHORIZED);
                        }

                        logger.info("✅ Token version verified (source: " + result.source() + ")");
                        logger.info("📤 Adding headers - X-User: " + Userid + ", X-Roles: " + String.join(",", roles));

                        // this is the success case(as token validation is done) so here we are sending the request forward and also modifing the request  by  passing the user and its roles also in the request header
                        return chain.filter(
                                exchange.mutate()
                                        .request(builder ->
                                                // wipe anything the caller sent under these names
                                                // first, then write the real values from the token
                                                stripIdentityHeaders(builder)
                                                        .header("X-User-Id", Userid)
                                                        .header("X-Roles", String.join(",", roles))
                                                        .header("X-User-Email", Useremail)
                                                        // the secret word that tells the service
                                                        // "this really did come from the gateway"
                                                        .header(INTERNAL_SECRET_HEADER, internalSecret)
                                                        // pass the id on so the downstream
                                                        // TraceIdFilter reuses it instead of
                                                        // inventing a second one
                                                        .header(TRACE_ID_HEADER, traceId)
                                        )
                                        .build());
                    });
        }
        // this catch block is to handle any unexcepted error
        catch (Exception e) {
            logger.severe("❌ Token processing exception: " + e.getMessage());
            e.printStackTrace();
            return onError(exchange, "INVALID_TOKEN",
                    "The token could not be processed.", HttpStatus.UNAUTHORIZED);
        }

    }

    /**
     * Writes the SAME JSON error shape that common-web produces in the other services.
     *
     * <p>It is hand-built rather than shared, because common-web relies on
     * {@code @RestControllerAdvice}, which is a Spring MVC mechanism and does nothing in a
     * WebFlux app like this gateway. The field names and order must be kept in step with
     * {@code com.project.web.dto.ErrorResponse} by hand.</p>
     *
     * <p>Previously this sent a status code and an EMPTY body, so a client got a bare 401 with
     * nothing to display or log.</p>
     */
    /**
     * Removes any X-User-Id / X-Roles / X-User-Email the caller sent us.
     *
     * Why bother, when we overwrite them a moment later anyway? Because "overwrite" and "remove
     * then write" are only the same thing while the code below always writes all three. If someone
     * later adds a path that writes only two of them, the third would silently survive from the
     * caller. Wiping first means that can never happen, whatever changes later.
     */
    private ServerHttpRequest.Builder stripIdentityHeaders(ServerHttpRequest.Builder builder) {
        for (String header : IDENTITY_HEADERS) {
            builder.headers(headers -> headers.remove(header));
        }
        return builder;
    }

    // What to answer when the token-version LOOKUP itself fails.
    // (Moved here from the old .onErrorResume, unchanged. Now it only ever sees lookup errors.)
    private Mono<Void> lookupFailed(ServerWebExchange exchange, String traceId, Throwable e) {

        // 404 from AuthService = this user no longer exists. That is a
        // real answer, not an outage - so it's a 401 (log in again),
        // not a 503 (retry later, which would never help).
        if (e instanceof AuthUserNotFoundException) {
            logger.severe("[" + traceId + "] Token belongs to a user that no"
                    + " longer exists - rejecting");
            return onError(exchange, "TOKEN_REVOKED",
                    "This token is no longer valid. Please log in again.",
                    HttpStatus.UNAUTHORIZED);
        }
        logger.log(Level.SEVERE,
                "❌ Auth service call failed: " + FailureDescription.of(e), e);
        // 503, not 401 - AuthService being unreachable is our problem,
        // not a sign the caller's token is bad.
        return onError(exchange, "AUTH_SERVICE_UNAVAILABLE",
                "Unable to verify the session right now. Please try again.",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    // B4 step 6: the JSON writing moved to GatewayErrorWriter, so RequestSizeFilter can share it.
    private Mono<Void> onError(ServerWebExchange exchange, String errorCode, String message, HttpStatus status) {
        String traceId = resolveTraceId(exchange);
        logger.severe("[" + traceId + "] 🚫 Request rejected: " + errorCode + " (Status: " + status + ")");
        return GatewayErrorWriter.write(exchange, status, errorCode, message);
    }

    private String resolveTraceId(ServerWebExchange exchange) {
        return GatewayErrorWriter.resolveTraceId(exchange);
    }

    //Lower number = higher priority...means run this filter early
    @Override
    public int getOrder() {
        return -1;
    }
}

