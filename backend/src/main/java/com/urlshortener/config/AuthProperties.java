package com.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Authentication settings.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(String provider) {

    public AuthProperties {
        provider = provider == null || provider.isBlank() ? "session-token" : provider.trim();
    }
}
