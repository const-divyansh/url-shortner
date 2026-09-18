package com.urlshortener.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.urlshortener.auth.AuthenticatedOwnerArgumentResolver;

/**
 * MVC wiring for auth-aware controller parameters.
 */
@Configuration
public class AuthWebConfig implements WebMvcConfigurer {

    private final AuthenticatedOwnerArgumentResolver authenticatedOwnerArgumentResolver;

    public AuthWebConfig(AuthenticatedOwnerArgumentResolver authenticatedOwnerArgumentResolver) {
        this.authenticatedOwnerArgumentResolver = authenticatedOwnerArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authenticatedOwnerArgumentResolver);
    }
}
