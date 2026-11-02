package com.example.AuthService.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BCR-02 (2026-11-02): the cookie's settings really reach the cookie. Only RefreshCookie is started (no database,
 * no web server), with the same setting names application.properties uses.
 */
class RefreshCookieSettingsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RefreshCookie.class);

    @Test
    void withNoSettingsTheSafeDefaultsAreUsed() {
        runner.run(context -> {
            String cookie = context.getBean(RefreshCookie.class).issue("abc");
            assertTrue(cookie.startsWith("imp_refresh=abc;"), cookie);
            for (String flag : new String[]{"Path=/api/auth/refresh", "Max-Age=604800", "Secure", "HttpOnly", "SameSite=Strict"}) {
                assertTrue(cookie.contains(flag), "missing " + flag + " in: " + cookie);
            }
        });
    }

    @Test
    void secureOffAnotherPathAndAnotherLifetimeAreRespected() {
        runner.withPropertyValues(
                        "auth.refresh-cookie.secure=false",
                        "auth.refresh-cookie.path=/auth/refresh",
                        "auth.refresh-token.lifetime-days=2")
                .run(context -> {
                    RefreshCookie refreshCookie = context.getBean(RefreshCookie.class);
                    String cookie = refreshCookie.issue("abc");
                    assertTrue(cookie.contains("Path=/auth/refresh;"), cookie);
                    assertTrue(cookie.contains("Max-Age=172800"), cookie);
                    assertFalse(cookie.contains("Secure"), cookie);
                    assertTrue(cookie.contains("HttpOnly") && cookie.contains("SameSite=Strict"),
                            "these two are never switched off: " + cookie);
                    // Clearing uses the same path, or the browser would keep the cookie.
                    assertTrue(refreshCookie.clear().contains("Path=/auth/refresh;"), refreshCookie.clear());
                });
    }
}
