package com.urlshortener.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.urlshortener.auth.AuthenticatedOwnerArgumentResolver;
import com.urlshortener.ratelimit.RateLimitInterceptor;
import com.urlshortener.ratelimit.RateLimitScope;

/**
 * MVC wiring for auth-aware controller parameters and request budgets.
 */
@Configuration
public class AuthWebConfig implements WebMvcConfigurer {

    /**
     * Matches the short-code shape {@code RedirectController} maps, so the redirect
     * budget is charged on redirect attempts only. A broader pattern would also count
     * static assets and unrelated 404s against a visitor's budget.
     */
    private static final String REDIRECT_PATH_PATTERN = "/{shortCode:[A-Za-z0-9_-]{3,32}}";

    private final AuthenticatedOwnerArgumentResolver authenticatedOwnerArgumentResolver;
    private final RateLimitInterceptor rateLimitInterceptor;

    public AuthWebConfig(AuthenticatedOwnerArgumentResolver authenticatedOwnerArgumentResolver,
                         RateLimitInterceptor rateLimitInterceptor) {
        this.authenticatedOwnerArgumentResolver = authenticatedOwnerArgumentResolver;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authenticatedOwnerArgumentResolver);
    }

    /**
     * Registers the two budgets against the paths they protect.
     *
     * <p>Deliberately narrow. Analytics, the owned-links list and deletion are all
     * behind authentication already, and {@code /actuator/**} must stay reachable
     * precisely when the service is under load - health checks are how an orchestrator
     * decides whether to keep an instance, so throttling them would let heavy traffic
     * trigger a restart loop.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor.withScope(RateLimitScope.CREATE, HttpMethod.POST))
                .addPathPatterns("/api/urls");

        registry.addInterceptor(rateLimitInterceptor.withScope(RateLimitScope.REDIRECT, HttpMethod.GET))
                .addPathPatterns(REDIRECT_PATH_PATTERN);
    }
}
