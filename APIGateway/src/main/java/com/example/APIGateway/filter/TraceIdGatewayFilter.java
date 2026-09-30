package com.example.APIGateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

// C1 step 2b: gives every request its tracking number (trace id). This is its ONLY job.
//
// Before, this was mixed into JwtGatewayFilter (the token checker), and the gateway reused
// ANY X-Trace-Id a caller sent, unchecked. A caller could send a huge value, odd characters,
// or copy someone else's id - and it went straight into every service's logs.
//
// Now, first filter of all (order -3):
//   caller sent a safe-looking id?  -> reuse it   (so a caller can still follow their request)
//   missing or not safe?            -> make a new one
//   then: save it on the request (for the filters after this one), put it on the forwarded
//         request (the services' TraceIdFilter reads it), and on the reply (the caller sees it).
//
// This is the gateway's version of common-web's TraceIdFilter. It can't reuse that class:
// that one is for Spring MVC, the gateway is WebFlux.
@Component
public class TraceIdGatewayFilter implements GlobalFilter, Ordered {

    // Must match TraceIdFilter.TRACE_ID_HEADER in common-web.
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    // Where the checked id is kept while the request is inside the gateway.
    // An "attribute" is a small note that travels with the request. Why not just read the
    // header later? Because the last-resort error handler (GatewayExceptionHandler) sees the
    // ORIGINAL request - with the caller's raw header still on it. Attributes are shared by
    // every copy of the request, so everyone reads the same checked id.
    public static final String TRACE_ID_ATTRIBUTE = "gateway.traceId";

    // "Safe" = only letters, digits and "-", 1 to 64 characters.
    // Fits our 8-character ids and full UUIDs (36). Rejects spaces, quotes, control
    // characters and anything huge.
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = currentTraceId(exchange);

        // On the reply, so the caller can quote it back to us.
        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);

        // On the forwarded request. set() REPLACES the caller's value, so an unsafe one never
        // reaches a service.
        ServerWebExchange withTraceId = exchange.mutate()
                .request(builder -> builder.headers(headers -> headers.set(TRACE_ID_HEADER, traceId)))
                .build();
        return chain.filter(withTraceId);
    }

    // The ONE way to get this request's trace id, for every filter and handler in the gateway.
    // The first call decides the id and saves it; every later call gets the same one.
    // (Also works for requests that never reach the filters - e.g. a path with no route -
    // because the error handler calls this too.)
    public static String currentTraceId(ServerWebExchange exchange) {
        Object saved = exchange.getAttributes().get(TRACE_ID_ATTRIBUTE);
        if (saved instanceof String id) {
            return id;
        }
        String fromCaller = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        String traceId = isSafe(fromCaller) ? fromCaller : newTraceId();
        exchange.getAttributes().put(TRACE_ID_ATTRIBUTE, traceId);
        return traceId;
    }

    static boolean isSafe(String candidate) {
        return candidate != null && SAFE_ID.matcher(candidate).matches();
    }

    // Same style as common-web: 8 characters, short enough to read out over the phone.
    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // -3 = before everything: RequestSizeFilter (-2), JwtGatewayFilter (-1), RateLimitFilter (1).
    // So even a request refused for its size already has its id.
    @Override
    public int getOrder() {
        return -3;
    }
}
