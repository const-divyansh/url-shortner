package com.urlshortener.auth;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.urlshortener.config.AuthProperties;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Selects the configured auth provider.
 */
@Component
public class ActiveRequestAuthenticator {

    private final Map<String, RequestAuthenticator> authenticators;
    private final AuthProperties properties;

    public ActiveRequestAuthenticator(List<RequestAuthenticator> authenticators, AuthProperties properties) {
        this.authenticators = authenticators.stream()
                .collect(Collectors.toUnmodifiableMap(RequestAuthenticator::name, Function.identity()));
        this.properties = properties;

        if (!this.authenticators.containsKey(properties.provider())) {
            throw new IllegalStateException("Unknown auth provider: " + properties.provider()
                    + ". Available: " + this.authenticators.keySet());
        }
    }

    public AuthenticatedPrincipal authenticate(HttpServletRequest request) {
        return authenticators.get(properties.provider()).authenticate(request);
    }
}
