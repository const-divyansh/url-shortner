package com.urlshortener.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate limiting settings.
 *
 * <p>Limits are configuration, never constants: the right threshold depends on where
 * this runs and who is in front of it, and discovering a limit is wrong should not
 * require a rebuild. A limit set too low is a self-inflicted outage, so it must be
 * adjustable at the speed of a restart.
 *
 * @param enabled  master switch. When false a no-op limiter is wired in instead, so
 *                 nothing on the hot path consults Redis at all - the off state costs
 *                 nothing rather than costing a skipped branch
 * @param create   budget for link creation
 * @param redirect budget for the redirect hot path
 */
@ConfigurationProperties(prefix = "app.ratelimit")
public record RateLimitProperties(boolean enabled, Budget create, Budget redirect) {

    /**
     * Defaults chosen to be generous enough that no legitimate user meets them, because
     * the first version of a limit should only catch abuse. Tightening later is safe;
     * shipping too tight means real users hit 429s and the feature gets switched off
     * wholesale rather than tuned.
     */
    private static final Budget DEFAULT_CREATE = new Budget(20, Duration.ofMinutes(1));
    private static final Budget DEFAULT_REDIRECT = new Budget(300, Duration.ofMinutes(1));

    public RateLimitProperties {
        create = create == null ? DEFAULT_CREATE : create;
        redirect = redirect == null ? DEFAULT_REDIRECT : redirect;
    }

    /**
     * A permitted number of requests within a repeating window.
     *
     * <p><strong>Fixed window, and its known weakness.</strong> The window is a wall
     * clock slot, not a sliding one, so a caller can spend a full budget at the very
     * end of one window and another immediately at the start of the next - up to twice
     * {@code limit} across the boundary. A sliding window or token bucket removes that,
     * at the cost of more state and more Redis round trips per request.
     *
     * <p>Accepted deliberately: the purpose here is to stop sustained abuse, and a
     * doubled burst for a few seconds is not the thing being defended against. Recorded
     * so the limitation is a decision rather than a surprise found later.
     *
     * @param limit  maximum requests permitted per window
     * @param window length of the window
     */
    public record Budget(int limit, Duration window) {

        public Budget {
            if (limit <= 0) {
                // A zero or negative limit would reject every request, including the
                // operator's own attempt to look at the service. Failing at startup is
                // far kinder than a service that appears to run and refuses everyone.
                throw new IllegalArgumentException("Rate limit must be positive, was " + limit);
            }
            if (window == null || window.isZero() || window.isNegative()) {
                throw new IllegalArgumentException("Rate limit window must be positive, was " + window);
            }
        }
    }
}
