package com.example.Ai_Service.service;

import com.example.Ai_Service.exception.AiUnavailableException;
import com.project.web.exception.ApiException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

// AI plan phase 1: every Gemini call goes through here.
// Spring AI has no time-limit setting for Gemini, so the call runs on its own thread and we stop waiting after the limit.
//   too slow    -> 504 AI_TIMEOUT
//   any failure -> 503 AI_UNAVAILABLE (quota, network, an answer that is not valid JSON)
// Without this the user saw a raw 500, or waited until the gateway gave up.
@Component
public class AiCallGuard {

    private static final Logger log = LoggerFactory.getLogger(AiCallGuard.class);

    private final ExecutorService pool = Executors.newFixedThreadPool(8);
    private final long timeoutMillis;

    public AiCallGuard(@Value("${ai.call.timeout-seconds:30}") long timeoutSeconds) {
        this.timeoutMillis = TimeUnit.SECONDS.toMillis(timeoutSeconds);
    }

    public <T> T call(String what, Supplier<T> work) {
        Map<String, String> traceId = MDC.getCopyOfContextMap();   // so the other thread's log lines keep the trace id
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            if (traceId != null) {
                MDC.setContextMap(traceId);
            }
            try {
                return work.get();
            } finally {
                MDC.clear();
            }
        }, pool);

        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("AI call took too long: {}", what);
            throw new AiUnavailableException("AI_TIMEOUT", "The AI took too long to answer. Please try again.",
                    HttpStatus.GATEWAY_TIMEOUT, e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ApiException apiException) {
                throw apiException;
            }
            log.error("AI call failed: {}", what, e.getCause());
            throw unavailable(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable(e);
        }
    }

    private static AiUnavailableException unavailable(Throwable cause) {
        return new AiUnavailableException("AI_UNAVAILABLE", "The AI is not available right now. Please try again later.",
                HttpStatus.SERVICE_UNAVAILABLE, cause);
    }

    @PreDestroy
    void stop() {
        pool.shutdownNow();
    }
}
