package com.urlshortener.ratelimit;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.urlshortener.config.RateLimitProperties;
import com.urlshortener.config.RateLimitProperties.Budget;

/**
 * Redis-backed fixed-window {@link RateLimiter}.
 *
 * <p><strong>Why Redis rather than an in-process counter.</strong> The application
 * layer is stateless by design (NFR2), so an in-memory counter would be per-instance:
 * with three instances behind a load balancer a caller would get three times the
 * intended budget, and the effective limit would change every time the service scaled.
 * A shared counter is the only version of this that means anything.
 *
 * <p><strong>The algorithm.</strong> {@code INCR} on a key derived from the current
 * wall-clock window, then {@code EXPIRE} on first use so the key disposes of itself.
 * {@code INCR} returns the post-increment value atomically, which is what makes this
 * correct under concurrency: every competing request receives a distinct number, so
 * exactly the first {@code limit} of them are admitted. A read-then-write version would
 * let simultaneous requests all observe the same under-limit count and all proceed.
 *
 * <p>No Lua script and no {@code MULTI}: the increment alone decides the outcome, and
 * the expiry is idempotent, so there is nothing that must be atomic across both.
 *
 * <p><strong>Fails open.</strong> Every Redis failure allows the request. This runs in
 * front of the redirect, so treating an unreachable limiter as a rejection would
 * convert a Redis outage into a total outage - inverting the reliability goal the
 * limiter exists to serve (NFR3). The trade-off is explicit: while Redis is down there
 * is no limiting, which is why the failure is logged at warning rather than swallowed
 * silently.
 */
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    /**
     * Versioned, matching the convention used by the click buffer: if the key shape or
     * counting semantics ever change, a new version counts separately rather than
     * inheriting half-formed state written by the previous implementation.
     */
    private static final String KEY_PREFIX = "ratelimit:v1:";

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;

    public RedisRateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public RateLimitDecision check(RateLimitScope scope, String key) {
        if (key == null || key.isBlank()) {
            // No identity to count against. Rejecting here would penalise a caller for
            // something they did not control, and every such caller would share one
            // bucket anyway, so the limit would be meaningless as well as unfair.
            log.warn("Skipping rate limit for {}: no client key available", scope);
            return RateLimitDecision.allow();
        }

        Budget budget = properties.budgetFor(scope);
        Instant now = Instant.now();
        long windowSeconds = budget.window().toSeconds();
        // Aligning to a wall-clock slot is what lets the key encode the window, so the
        // counter needs no stored start time and no cleanup - the key simply stops
        // being addressed once the window passes, and the TTL removes it.
        long windowIndex = now.getEpochSecond() / windowSeconds;
        String redisKey = KEY_PREFIX + scope.name().toLowerCase() + ':' + key + ':' + windowIndex;

        long count;
        try {
            Long incremented = redis.opsForValue().increment(redisKey);
            if (incremented == null) {
                // Defensive: the template contract permits null, and a null here would
                // otherwise become a NullPointerException on the hot path.
                log.warn("Skipping rate limit for {}: increment returned no value", scope);
                return RateLimitDecision.allow();
            }
            count = incremented;

            if (count == 1L) {
                // First request in this window, so the key is new and has no TTL yet.
                // Setting it only here avoids pushing the expiry further out on every
                // request, which would turn a fixed window into an unbounded one that
                // never resets for a caller who keeps knocking.
                redis.expire(redisKey, Duration.ofSeconds(windowSeconds));
            }
        } catch (RuntimeException e) {
            // Deliberately allowed. See the class note: a broken limiter must not break
            // the request it was only meant to measure.
            log.warn("Rate limiting unavailable for {}, allowing request ({})", scope, e.getMessage());
            return RateLimitDecision.allow();
        }

        if (count <= budget.limit()) {
            return RateLimitDecision.allow();
        }

        return RateLimitDecision.reject(retryAfter(now, windowIndex, windowSeconds));
    }

    /**
     * Time remaining until the current window ends.
     *
     * <p>Never zero: a caller told to retry immediately would retry into the same
     * exhausted window and be rejected again, converting a throttle into a retry storm.
     */
    private static Duration retryAfter(Instant now, long windowIndex, long windowSeconds) {
        long windowEnd = (windowIndex + 1) * windowSeconds;
        long remaining = windowEnd - now.getEpochSecond();

        return Duration.ofSeconds(Math.max(1L, remaining));
    }
}
