package com.urlshortener.validation;

import java.time.Instant;

/**
 * The inputs a {@link ValidationRule} inspects.
 *
 * <p>Deliberately a validation-package type rather than the HTTP request DTO: rules
 * stay independent of the API contract, so changing the wire format does not ripple
 * into security checks, and the chain can be exercised in tests without constructing
 * web-layer objects.
 *
 * @param targetUrl   the URL to shorten, as supplied by the caller
 * @param customAlias caller-chosen code, or {@code null} when one should be generated
 * @param expiresAt   expiry instant, or {@code null} for a link that never expires
 */
public record ValidationTarget(String targetUrl, String customAlias, Instant expiresAt) {

    public boolean hasCustomAlias() {
        return customAlias != null && !customAlias.isBlank();
    }

    public boolean hasExpiry() {
        return expiresAt != null;
    }
}
