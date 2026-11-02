package com.example.AuthService.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * BCR-02 (2026-11-02): the ONE place that builds the refresh-token cookie. Login and refresh set it, refresh and
 * logout clear it - all through here, so the flags can never drift apart (a browser only deletes a cookie whose
 * path matches the one it was set with).
 *
 * Why a cookie: a browser page had to keep the 7-day refresh token where JavaScript can read it. An HttpOnly cookie
 * is kept by the browser, sent back by itself, and no script on the page can read or copy it.
 *
 *   HttpOnly        - JavaScript can not read it. The point of the change.
 *   Secure          - sent over HTTPS only (Chrome and Firefox also allow it on http://localhost).
 *   SameSite=Strict - never sent when the request starts on another website.
 *   Path            - sent ONLY to the refresh endpoint, not with every request. It is the path the BROWSER sees,
 *                     /api/auth/refresh: the frontend's proxy (Vite in development, nginx in production) removes
 *                     /api before the gateway.
 *   Max-Age         - the refresh token's own lifetime, from the same setting, so the two can not disagree.
 */
@Component
public class RefreshCookie {

    public static final String NAME = "imp_refresh";
    // Our tokens are 43 characters. Same limit the old JSON body had: a huge value is never hashed.
    public static final int MAX_LENGTH = 200;

    private final boolean secure;
    private final String path;
    private final Duration lifetime;

    public RefreshCookie(@Value("${auth.refresh-cookie.secure:true}") boolean secure,
                         @Value("${auth.refresh-cookie.path:/api/auth/refresh}") String path,
                         @Value("${auth.refresh-token.lifetime-days:7}") long lifetimeDays) {
        this.secure = secure;
        this.path = path;
        this.lifetime = Duration.ofDays(lifetimeDays);
    }

    /** The Set-Cookie header value that hands a new refresh token to the browser. */
    public String issue(String refreshToken) {
        return build(refreshToken, lifetime);
    }

    /** The Set-Cookie header value that makes the browser delete the cookie. */
    public String clear() {
        return build("", Duration.ZERO);
    }

    private String build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
