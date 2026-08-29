package com.project.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 /**
 * Request Trace / Correlation ID Filter
 *
 * Purpose of TraceFilter is:
 * - To Gives every incoming HTTP request a unique traceId.
 * - That same traceId is used in logs and the HTTP response.
 * - This helps us to trace one request across multiple services.
 *
 * Flow:
 * 1. Check the incoming "X-Trace-Id" header.
 *    - If present -> reuse it.
 *    - If missing -> generate a new traceId.
 *
 * 2. Store the traceId in SLF4J MDC.
 *    - MDC = it is the small thread-local map where we temporarily store information about the current request/thread.
 *    - by using this MDC our  logs will automatically include the traceId with them.
 *
 * 3. so how the Logs looks when we use MDC :
 * The logging pattern uses "%X{traceId}".
 *    - Example:
 *        [traceId=ABC123] Creating incident
 *        [traceId=ABC123] Saving incident
 *
 * 4. Continue the request using filterChain.doFilter().
 *    - The controller/service/repository can now execute normally.
 *    - All logs during this request can use the same traceId.
 *
 * 5. Add the traceId to the response as "X-Trace-Id".
 *    - Example:
 *        X-Trace-Id: ABC123
 *    - The frontend/client can give this ID to us when reporting an error.
 *
 * 6. Always remove the traceId from MDC in the finally block.
 *    - Servlet containers use a thread pool.
 *    - Threads are reused for different requests.
 *    - If we don't clear MDC, Request B could accidentally inherit
 *      Request A's traceId.
 *
 * Example:
 *
 *    Client
 *      |
 *      | GET /incidents/123
 *      | X-Trace-Id: ABC123
 *      v
 *    Trace Filter
 *      |
 *      | MDC: traceId=ABC123
 *      v
 *    Controller -> Service -> Repository
 *      |
 *      v
 *    Response
 *      X-Trace-Id: ABC123
 *
 * Why this is useful:
 * - User reports: "My request failed. Trace ID = ABC123."
 * - Search logs for "ABC123".
 * - We can immediately find logs belonging to that exact request.
 *
 * Important:
 * - traceId is NOT an error ID and does not fix errors.
 * - It is simply a tracking/correlation ID for connecting a request
 *   with its logs and response.
 */
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** Key used both in the SLF4J MDC and as a servlet request attribute. */
    public static final String TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String traceId = request.getHeader(TRACE_ID_HEADER);

        if (traceId == null || traceId.isBlank()) {
            // 8 hex chars is enough to be unique within a log retention window and short
            // enough that a user can read it off a screen and tell it to you over the phone.
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }

        MDC.put(TRACE_ID_KEY, traceId);
        request.setAttribute(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }

    /**
     * Reads the current request's traceId. Used by the exception handler.
     * Falls back to the MDC, then to a placeholder, so it never returns null.
     */
    public static String currentTraceId(HttpServletRequest request) {
        if (request != null) {
            Object attribute = request.getAttribute(TRACE_ID_KEY);
            if (attribute instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        String fromMdc = MDC.get(TRACE_ID_KEY);
        return (fromMdc != null && !fromMdc.isBlank()) ? fromMdc : "unknown";
    }
}
