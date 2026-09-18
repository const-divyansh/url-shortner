package com.urlshortener.ratelimit;

/**
 * Decides whether a request may proceed.
 *
 * <p>An interface so the limiting technology and the decision to limit at all are both
 * swappable without the calling code knowing (Dependency Inversion). {@code
 * NoOpRateLimiter} is selected by configuration when limiting is disabled, which keeps
 * the on/off choice out of the hot path entirely - there is no {@code if (enabled)}
 * anywhere, because the disabled case is a different object rather than a branch.
 *
 * <p><strong>Contract: never throw.</strong> This runs in front of the redirect, the
 * one path that must keep working when everything else is degraded. An implementation
 * that cannot reach its backing store must <em>allow</em> the request and log, never
 * propagate (NFR3). Rejecting a visitor because the limiter itself is broken would turn
 * a Redis outage into a full outage - the opposite of what a reliability feature is
 * for.
 *
 * <p>The decision is returned rather than enforced here: raising the 429 is the
 * caller's job, so this stays a pure policy decision that can be unit tested without a
 * servlet context.
 */
public interface RateLimiter {

    /**
     * Records one request against {@code key} and reports whether it is within budget.
     *
     * <p>Counting and deciding are deliberately one operation: splitting them into
     * "read the count" then "increment it" would leave a gap in which concurrent
     * requests all observe the same under-limit value and all proceed.
     *
     * @param scope which budget to charge against - separate scopes do not share a
     *              count, so a visitor who exhausts their redirect budget can still
     *              create a link
     * @param key   the caller identity to count per. Always a <em>hashed</em> client
     *              address, never a raw one (NFR8)
     * @return the decision; never null, and never an exception
     */
    RateLimitDecision check(RateLimitScope scope, String key);
}
