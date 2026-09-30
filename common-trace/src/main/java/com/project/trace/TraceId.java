package com.project.trace;

import java.util.UUID;
import java.util.regex.Pattern;

// The trace id (a request's tracking number) - the things everyone must agree on.
//
// Before, "traceId" and "X-Trace-Id" were typed separately in common-web, and would have been
// typed again in common-kafka. Two typed copies eventually differ. Now there is one.
//
// Used by: common-web (HTTP requests), common-kafka (Kafka messages).
// (The gateway is a separate reactive app that does not use these modules; it keeps its own
// copy of the header name and the same safety rule, in TraceIdGatewayFilter.)
public final class TraceId {

    private TraceId() {
    }

    // The name it is saved under on a thread's notepad (the MDC). The log format reads it by
    // this name, so writer and printer must use the same word.
    public static final String MDC_KEY = "traceId";

    // The name it travels under on the "envelope": an HTTP header, and a Kafka message header.
    public static final String HEADER = "X-Trace-Id";

    // "Safe" = only letters, digits and "-", 1 to 64 characters. Fits our 8-character ids and
    // full UUIDs (36). Rejects spaces, quotes, control characters and anything huge.
    // Same rule as the gateway's TraceIdGatewayFilter.
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9-]{1,64}");

    public static boolean isSafe(String candidate) {
        return candidate != null && SAFE.matcher(candidate).matches();
    }

    // 8 hex characters: short enough to read out over the phone.
    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
