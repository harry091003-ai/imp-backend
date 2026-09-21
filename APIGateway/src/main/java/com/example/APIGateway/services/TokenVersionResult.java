package com.example.APIGateway.services;

/**
 * The answer to "is this token revoked?" - plus how sure we are about it.
 *
 * Before this, we just returned an Integer (the version number). That does
 * not work when AuthService is down AND we have nothing cached - there is
 * no number to give back. So this record carries the answer (if we have
 * one) plus WHERE it came from, so the caller knows how much to trust it.
 *
 * (record = a simple, read-only data holder. Java auto-generates the
 * constructor and getter-style methods version() and source() for us.)
 */
public record TokenVersionResult(Integer version, Source source) {

    /** Which tier answered. Listed best to worst. */
    public enum Source {

        /** TIER 1: cached, under 30s old. Fast path, no network call. */
        FRESH_CACHE,

        /** We called AuthService and it replied. Fully trustworthy. */
        LIVE,

        /** TIER 2: AuthService did not reply, using a cached value (30s-5min old). */
        STALE_CACHE,

        /** TIER 3: AuthService did not reply and nothing usable is cached. */
        UNAVAILABLE
    }

    /**
     * Can we actually compare versions right now?
     * MUST be checked before reading version(). In the UNAVAILABLE case it
     * is null - comparing that the wrong way would reject every user.
     */
    public boolean canCheckRevocation() {
        return version != null;
    }

    /** True if this came from a fallback (Tier 2 or 3), so we can log it. */
    public boolean isFallback() {
        return source == Source.STALE_CACHE || source == Source.UNAVAILABLE;
    }
}
