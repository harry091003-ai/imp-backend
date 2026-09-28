package com.example.APIGateway.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import io.github.bucket4j.local.LocalBucketBuilder;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

// B5 step 1: a rate limiter that keeps a SEPARATE bucket for each key.
// The key is "who is calling": a user id for /ai/** (step 1), an IP for login (step 2).
//
// How a token bucket works:
//   - Each key gets a bucket that starts full, e.g. 5 tokens.
//   - Every request takes 1 token. No token left -> the request is refused.
//   - Tokens come back slowly over time (5 per minute = 1 every 12 seconds).
// So a person gets a small burst, then a steady pace. A loop runs dry in seconds.
//
// One bucket can have several limits at once (for example per minute AND per day).
// A request needs a token from EVERY limit to pass.
public class KeyedRateLimiter {

    // One limit: "count requests per period". Example: new Limit(5, Duration.ofMinutes(1)).
    public record Limit(long count, Duration period) {
    }

    private final List<Limit> limits;
    private final TimeMeter clock;

    // key -> that key's bucket. Caffeine keeps memory bounded:
    //   maximumSize       = never more than this many keys in memory
    //   expireAfterAccess = forget a key that has been quiet for the LONGEST period.
    //                       By then its bucket would be full again anyway, so forgetting
    //                       it changes nothing. A shorter time would let someone reset
    //                       their daily limit just by waiting a few minutes.
    private final Cache<String, Bucket> buckets;

    public KeyedRateLimiter(List<Limit> limits, long maxTrackedKeys, TimeMeter clock) {
        this.limits = List.copyOf(limits);
        this.clock = clock;
        Duration longestPeriod = this.limits.stream()
                .map(Limit::period)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        this.buckets = Caffeine.newBuilder()
                .maximumSize(maxTrackedKeys)
                .expireAfterAccess(longestPeriod)
                .build();
    }

    // Try to take 1 token for this key.
    // The answer says: allowed or not, and if not, how long until a token comes back.
    public ConsumptionProbe tryConsume(String key) {
        return buckets.get(key, k -> newBucket()).tryConsumeAndReturnRemaining(1);
    }

    private Bucket newBucket() {
        LocalBucketBuilder builder = Bucket.builder();
        for (Limit limit : limits) {
            // refillGreedy = tokens come back a little at a time, not all at once.
            // 5 per minute -> 1 token every 12 seconds, not 5 tokens at the top of each minute.
            builder.addLimit(b -> b.capacity(limit.count()).refillGreedy(limit.count(), limit.period()));
        }
        // The clock is passed in so tests can move time forward without sleeping.
        return builder.withCustomTimePrecision(clock).build();
    }
}
