package com.urlshortener.ratelimit;

/**
 * The budgets a request can be charged against.
 *
 * <p>An enum rather than free-form strings so a typo is a compile error instead of a
 * silently separate counter that never limits anything - a rate limiter that quietly
 * stops limiting is the failure mode worth designing against, because nothing reports
 * it.
 *
 * <p>Scopes are counted independently and configured independently. Creating a link
 * costs a database write, a uniqueness check and possibly several generation attempts,
 * while a redirect is a single lookup, so one shared budget would either throttle
 * ordinary browsing or leave creation wide open.
 */
public enum RateLimitScope {

    /** Link creation - expensive, authenticated, and the abuse target that matters. */
    CREATE,

    /**
     * The redirect hot path - cheap per request, but the endpoint a flood would aim at.
     * Limits here are deliberately generous: a real visitor following several links in
     * quick succession must never see a 429.
     */
    REDIRECT
}
