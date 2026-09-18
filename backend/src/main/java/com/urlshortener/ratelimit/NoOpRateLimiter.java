package com.urlshortener.ratelimit;

/**
 * The disabled {@link RateLimiter}: allows everything, touches nothing.
 *
 * <p>A Null Object, so switching rate limiting off is a different object rather than a
 * condition inside the limiter. The call sites contain no {@code if (enabled)} check at
 * all, which means there is no disabled code path to reason about separately and no way
 * for the flag to be consulted in one place and forgotten in another.
 *
 * <p>This is also what makes the flag a genuine kill switch: with limiting off, the hot
 * path makes no Redis call whatsoever, so a limiter suspected of misbehaving can be
 * removed from the request path entirely without a deployment.
 */
public class NoOpRateLimiter implements RateLimiter {

    @Override
    public RateLimitDecision check(RateLimitScope scope, String key) {
        return RateLimitDecision.allow();
    }
}
