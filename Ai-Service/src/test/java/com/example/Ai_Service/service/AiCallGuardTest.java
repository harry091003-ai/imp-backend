package com.example.Ai_Service.service;

import com.example.Ai_Service.exception.AiUnavailableException;
import com.project.web.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI plan phase 1 (2026-11-01): Spring AI has no time limit for Gemini, so we made our own.
 */
class AiCallGuardTest {

    private final AiCallGuard guard = new AiCallGuard(1);

    @Test
    void aNormalAnswerPassesThrough() {
        assertEquals("ok", guard.call("test", () -> "ok"));
    }

    @Test
    void aCallThatTakesTooLongIsA504Quickly() {
        long start = System.nanoTime();

        AiUnavailableException error = assertThrows(AiUnavailableException.class, () -> guard.call("test", () -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "too late";
        }));

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, error.getStatus());
        assertEquals("AI_TIMEOUT", error.getErrorCode());
        assertTrue((System.nanoTime() - start) / 1_000_000 < 5000, "gave up after about 1 second");
    }

    @Test
    void aFailureIsA503AndHidesTheDetails() {
        AiUnavailableException error = assertThrows(AiUnavailableException.class, () -> guard.call("test", () -> {
            throw new IllegalStateException("api key abc123 rejected");
        }));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertFalse(error.getMessage().contains("abc123"));
    }

    @Test
    void ourOwnErrorsKeepTheirMeaning() {
        assertThrows(ValidationException.class, () -> guard.call("test", () -> {
            throw new ValidationException("BAD", "bad input");
        }));
    }

    @Test
    void theTraceIdFollowsTheCallToTheOtherThread() {
        MDC.put("traceId", "abc123");
        try {
            assertEquals("abc123", guard.call("test", () -> MDC.get("traceId")));
        } finally {
            MDC.clear();
        }
    }
}
