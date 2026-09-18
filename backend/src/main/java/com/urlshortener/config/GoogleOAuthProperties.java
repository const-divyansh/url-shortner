package com.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google OAuth settings. Credentials come from the environment only - see
 * {@code .env.example} - never hardcoded, per the security checklist.
 */
@ConfigurationProperties(prefix = "app.oauth.google")
public record GoogleOAuthProperties(String clientId, String clientSecret, String redirectUri,
        String frontendRedirectUri) {
}
