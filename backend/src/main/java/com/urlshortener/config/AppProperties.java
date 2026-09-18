package com.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application settings supplied by the environment.
 *
 * @param baseUrl public origin used to build returned short URLs. Must be the address
 *                clients actually reach, which is not necessarily the address this
 *                process binds to when running behind a proxy or in a container.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String baseUrl) {

    public AppProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("app.base-url must be configured");
        }
        // Normalised once here so every caller can append "/" + code without each
        // having to consider whether the configured value ended with a slash.
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
