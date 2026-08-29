package com.example.APIGateway.filter;
import com.example.APIGateway.services.TokenVersionService;
import com.example.APIGateway.utils.JwtUtil;
import jakarta.validation.constraints.Email;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
    private static final Logger logger = Logger.getLogger(JwtGatewayFilter.class.getName());

    // Must match TraceIdFilter.TRACE_ID_HEADER in common-web. The gateway cannot share that
    // class: common-web is Spring MVC (servlet) and this app is WebFlux (reactive), so the
    // constant is duplicated on purpose rather than dragging the servlet stack in here.
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    public JwtGatewayFilter(JwtUtil jwtUtil,
                            TokenVersionService tokenVersionService) {
        this.jwtUtil = jwtUtil;
        this.tokenVersionService = tokenVersionService;
    }


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
            return chain.filter(exchange);//passing request forward without token validation as here no need
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
            return tokenVersionService.getTokenVersion(Userid)
                    .flatMap(latestVersion -> {

                        if ( !tokenVersion.equals(latestVersion)) {
                            logger.severe("❌ Token version mismatch - token revoked");
                            return onError(exchange, "TOKEN_REVOKED",
                                    "This token is no longer valid. Please log in again.", HttpStatus.UNAUTHORIZED);
                        }

                        logger.info("✅ Token version verified");
                        logger.info("📤 Adding headers - X-User: " + Userid + ", X-Roles: " + String.join(",", roles));

                        // this is the success case(as token validation is done) so here we are sending the request forward and also modifing the request  by  passing the user and its roles also in the request header
                        return chain.filter(
                                exchange.mutate()
                                        .request(builder ->
                                                builder.header("X-User-Id", Userid)
                                                        .header("X-Roles", String.join(",", roles))
                                                        .header("X-User-Email", Useremail)
                                                        // pass the id on so the downstream
                                                        // TraceIdFilter reuses it instead of
                                                        // inventing a second one
                                                        .header(TRACE_ID_HEADER, traceId)
                                        )
                                        .build());
                    })
                    //onErrorResume handles asynchronous (reactive) errors!
                    //this onErrorResume is to handle as if tokenVersionService fails
                    .onErrorResume(e -> {
                        logger.severe("❌ Auth service error: " + e.getClass() + " - " + e.getMessage());
                        e.printStackTrace();
                        // CHANGED: 503, not 401. AuthService being unreachable is our problem, not a
                        // sign the caller's token is bad - telling them "unauthorized" made them
                        // re-login pointlessly and hid a real outage.
                        return onError(exchange, "AUTH_SERVICE_UNAVAILABLE",
                                "Unable to verify the session right now. Please try again.",
                                HttpStatus.SERVICE_UNAVAILABLE);
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
    private Mono<Void> onError(ServerWebExchange exchange, String errorCode, String message, HttpStatus status) {

        String traceId = resolveTraceId(exchange);
        String path = exchange.getRequest().getURI().getPath();

        logger.severe("[" + traceId + "] 🚫 Request rejected: " + errorCode + " (Status: " + status + ")");

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(TRACE_ID_HEADER, traceId);

        String body = "{"
                + "\"timestamp\":\"" + Instant.now() + "\","
                + "\"status\":" + status.value() + ","
                + "\"error\":\"" + jsonEscape(errorCode) + "\","
                + "\"message\":\"" + jsonEscape(message) + "\","
                + "\"path\":\"" + jsonEscape(path) + "\","
                + "\"traceId\":\"" + jsonEscape(traceId) + "\""
                + "}";

        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /** Reuse an inbound trace id if the caller sent one, otherwise mint a short new one. */
    private String resolveTraceId(ServerWebExchange exchange) {
        String inbound = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (inbound != null && !inbound.isBlank()) {
            return inbound;
        }
        String existing = exchange.getResponse().getHeaders().getFirst(TRACE_ID_HEADER);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /** Minimal JSON string escaping - the values here are our own codes, paths and messages. */
    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }

    //Lower number = higher priority...means run this filter early
    @Override
    public int getOrder() {
        return -1;
    }
}

