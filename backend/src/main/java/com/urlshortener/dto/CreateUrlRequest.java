package com.urlshortener.dto;

import java.time.Instant;

/**
 * Request body for creating a short URL.
 *
 * <p>Intentionally free of bean-validation annotations. All rules live in the
 * validation chain so there is exactly one place rules are defined and one error
 * format - splitting them between annotations and the chain would let the two drift
 * and would produce two different-shaped 400 responses.
 *
 * @param targetUrl   the URL to shorten
 * @param customAlias caller-chosen code; null to have one generated
 * @param expiresAt   ISO-8601 instant after which the link stops resolving; null for never
 */
public record CreateUrlRequest(String targetUrl, String customAlias, Instant expiresAt) {
}
