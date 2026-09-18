package com.urlshortener.config;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.urlshortener.ratelimit.RateLimitScope;

/**
 * Rate limiting settings.
 *
 * <p>Limits are configuration, never constants: the right threshold depends on where
 * this runs and who is in front of it, and discovering a limit is wrong should not
 * require a rebuild. A limit set too low is a self-inflicted outage, so it must be
 * adjustable at the speed of a restart.
 *
 * <p><strong>Open/Closed on the scope axis.</strong> Budgets are held in a map keyed by
 * {@link RateLimitScope} rather than one named field per scope. Adding a new limited
 * endpoint is then an enum value plus one new {@code app.ratelimit.budgets.<scope>} key
 * - this class does not change. The previous shape (separate {@code create}/
 * {@code redirect} fields, mirrored by a {@code switch} in {@code RedisRateLimiter})
 * meant a third scope needed three existing files edited, which is exactly the
 * violation an enum-keyed map removes.
 *
 * @param enabled master switch. When false a no-op limiter is wired in instead, so
 *                nothing on the hot path consults Redis at all - the off state costs
 *                nothing rather than costing a skipped branch
 * @param budgets one budget per {@link RateLimitScope}, keyed by its lower-cased name
 *                (e.g. {@code app.ratelimit.budgets.create.limit})
 */
@ConfigurationProperties(prefix = "app.ratelimit")
public record RateLimitProperties(boolean enabled, Map<RateLimitScope, Budget> budgets) {

    public RateLimitProperties {
        budgets = budgets == null || budgets.isEmpty() ? Map.of() : new EnumMap<>(budgets);
    }

    /**
     * The budget configured for {@code scope}.
     *
     * <p>No per-scope branch lives here or in the caller - this is the one place a
     * missing scope is noticed, and it fails loudly rather than silently limiting to
     * nothing.
     *
     * @throws IllegalStateException if {@code scope} has no configured budget. Deferred
     *         to first use (not the constructor) so tests and the disabled path never
     *         need to supply a full map; the moment it matters is exactly when
     *         {@link com.urlshortener.ratelimit.RedisRateLimiter} tries to charge it.
     */
    public Budget budgetFor(RateLimitScope scope) {
        Budget budget = budgets.get(scope);
        if (budget == null) {
            throw new IllegalStateException("No rate limit budget configured for scope " + scope
                    + " (expected app.ratelimit.budgets." + scope.name().toLowerCase() + ".limit and .window)");
        }
        return budget;
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
