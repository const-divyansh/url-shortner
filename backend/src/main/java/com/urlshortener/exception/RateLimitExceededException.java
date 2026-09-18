package com.urlshortener.exception;

import java.time.Duration;

/**
 * Raised when a caller exceeds their request budget.
 *
 * <p>Carries {@code retryAfter} so the handler can emit a {@code Retry-After} header.
 * A 429 without one tells a client that it failed but not when to try again, and the
 * usual response to that is an immediate retry - which is precisely the behaviour the
 * limit exists to suppress.
 */
public class RateLimitExceededException extends RuntimeException {

    private final transient Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        super("Too many requests. Please retry in " + retryAfter.toSeconds() + " second(s).");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
