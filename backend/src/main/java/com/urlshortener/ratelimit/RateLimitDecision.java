package com.urlshortener.ratelimit;

import java.time.Duration;

/**
 * The outcome of a {@link RateLimiter#check} call.
 *
 * <p>Carries {@code retryAfter} rather than only a boolean so the caller can tell a
 * rejected client <em>when</em> to come back. Without it a well-behaved client has no
 * choice but to guess, and retrying blindly against a limiter is how a throttle turns
 * into the flood it was meant to prevent.
 *
 * @param allowed    whether the request may proceed
 * @param retryAfter how long until the budget refreshes; {@link Duration#ZERO} when
 *                   allowed, since there is nothing to wait for
 */
public record RateLimitDecision(boolean allowed, Duration retryAfter) {

    private static final RateLimitDecision ALLOWED = new RateLimitDecision(true, Duration.ZERO);

    /**
     * The allowed outcome.
     *
     * <p>A shared constant because this is the answer on virtually every request: the
     * limiter exists for the rare rejection, so the common path should not allocate.
     *
     * <p>Named {@code allow} rather than {@code allowed} because a record cannot
     * declare a static method with the same signature as its generated {@code
     * allowed()} accessor.
     */
    public static RateLimitDecision allow() {
        return ALLOWED;
    }

    /**
     * A rejection, asking the caller to retry after {@code retryAfter}.
     *
     * <p>Rounded up to at least one second by the caller's formatting, never down: a
     * {@code Retry-After: 0} would invite an immediate retry that is certain to be
     * rejected again.
     */
    public static RateLimitDecision reject(Duration retryAfter) {
        return new RateLimitDecision(false, retryAfter);
    }
}
