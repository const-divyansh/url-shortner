package com.urlshortener.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Cross-origin access for the browser client.
 *
 * <p>The frontend is served by its own dev server on a different port, so every call it
 * makes to this API is cross-origin and the browser blocks it unless the response says
 * otherwise.
 *
 * <p><strong>Origins are configured, never wildcarded.</strong> {@code "*"} would let any
 * website on the internet script requests against this API using a visitor's browser.
 * That matters more once M6 adds authentication, but it is also true today: an open API
 * plus a wildcard origin lets any page create links in a visitor's name and read the
 * analytics of any code it can guess.
 *
 * <p><strong>Credentials are deliberately not enabled.</strong> The session token is sent
 * as an explicit {@code Authorization: Bearer} header, attached by application code, not
 * a browser-managed credential such as a cookie. Enabling credentials would also make a
 * wildcard origin illegal, so leaving it off keeps a future misconfiguration from
 * becoming exploitable. The Google login redirect itself is a full-page navigation, not
 * a CORS request, so it is unaffected by this filter.
 *
 * <p>Applies to {@code /api/**} only. The redirect endpoint is reached by the browser
 * navigating to it, not by a scripted request, so it needs no CORS headers.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter(
            @Value("${app.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split("\\s*,\\s*")));

        // Only what the two screens use. POST covers creation, GET covers analytics,
        // PUT covers switching the active short-code strategy, DELETE covers removing
        // an owned link. Omitting a method here is not a no-op: the browser's preflight
        // fails and the request never reaches the server, surfacing as an unexplained
        // transport error rather than an HTTP status.
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));

        // Content-Type is required for JSON request bodies. Authorization carries the
        // bearer session token on protected requests.
        config.setAllowedHeaders(List.of("Content-Type", "Authorization"));

        config.setAllowCredentials(false);

        // Lets the browser skip the preflight request for an hour, so the analytics
        // screen does not pay an extra round trip on every poll.
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        return new CorsFilter(source);
    }
}
