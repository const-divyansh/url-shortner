package com.urlshortener.ratelimit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.urlshortener.event.ClientIpResolver;
import com.urlshortener.event.IpHasher;
import com.urlshortener.exception.RateLimitExceededException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Applies request budgets before a handler runs.
 *
 * <p>An interceptor rather than a servlet filter, because the decision needs to know
 * <em>which</em> endpoint is being called - creation and redirects have different
 * budgets - and interceptors are registered against path patterns, so that mapping is
 * declared in configuration instead of being re-derived from the URL here.
 *
 * <p><strong>The counted identity is a hashed address.</strong> Reusing
 * {@link IpHasher} rather than keying on the raw IP means rate limiting introduces no
 * new privacy surface: the same guarantee that applies to stored analytics (NFR8)
 * applies to counters that briefly exist in Redis. It also costs nothing - the hash is
 * stable, so a caller maps to the same bucket every time.
 *
 * <p><strong>X-Forwarded-For is not trusted by default.</strong> {@link
 * ClientIpResolver} honours it only behind a configured trusted proxy. That matters far
 * more here than it does for analytics: if a client could set its own address, it could
 * present a new one per request and never be limited at all. Conversely, deploying
 * behind a proxy without configuring {@code trusted-proxies} collapses every caller
 * onto the proxy's address and turns a per-IP budget into a single global one - so the
 * setting must be revisited whenever the network topology changes.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final RateLimitKeyNormaliser keyNormaliser;
    private final IpHasher ipHasher;
    private final RateLimitScope scope;
    private final HttpMethod method;

    /**
     * Marked explicitly because this class has a second, private constructor for
     * {@link #withScope}. With more than one constructor present Spring will not guess,
     * and without this annotation it looks for a no-argument constructor instead and
     * fails to start. Unit tests construct this directly, so only a real context
     * catches that - which is why it is called out here rather than left implicit.
     */
    @Autowired
    public RateLimitInterceptor(RateLimiter rateLimiter,
                                ClientIpResolver clientIpResolver,
                                RateLimitKeyNormaliser keyNormaliser,
                                IpHasher ipHasher) {
        this(rateLimiter, clientIpResolver, keyNormaliser, ipHasher,
                RateLimitScope.REDIRECT, HttpMethod.GET);
    }

    private RateLimitInterceptor(RateLimiter rateLimiter,
                                 ClientIpResolver clientIpResolver,
                                 RateLimitKeyNormaliser keyNormaliser,
                                 IpHasher ipHasher,
                                 RateLimitScope scope,
                                 HttpMethod method) {
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.keyNormaliser = keyNormaliser;
        this.ipHasher = ipHasher;
        this.scope = scope;
        this.method = method;
    }

    /**
     * Returns a copy of this interceptor charging {@code newScope} for {@code newMethod}
     * requests only.
     *
     * <p>Lets one implementation serve both registrations rather than duplicating the
     * resolve-hash-check sequence per scope, where the two copies could drift.
     *
     * <p>The method is part of the registration because path patterns alone are too
     * coarse: {@code /api/urls} serves both creation and the owned-links listing, and
     * charging a cheap authenticated read against the creation budget would let simply
     * refreshing the analytics screen exhaust a user's ability to create links.
     */
    public RateLimitInterceptor withScope(RateLimitScope newScope, HttpMethod newMethod) {
        return new RateLimitInterceptor(rateLimiter, clientIpResolver, keyNormaliser, ipHasher,
                newScope, newMethod);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!method.matches(request.getMethod())) {
            return true;
        }

        String countedIdentity = keyNormaliser.normalise(clientIpResolver.resolve(request));
        String hashedIp = ipHasher.hash(countedIdentity);
        RateLimitDecision decision = rateLimiter.check(scope, hashedIp);

        if (!decision.allowed()) {
            // Thrown rather than written directly, so the 429 body and headers are
            // produced by the same GlobalExceptionHandler that shapes every other
            // error. Writing a response here would create a second, divergent error
            // format that no controller advice governs.
            throw new RateLimitExceededException(decision.retryAfter());
        }

        return true;
    }
}
