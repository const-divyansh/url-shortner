package com.urlshortener.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

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
public record CreateUrlRequest(
        @Schema(format = "uri", example = "https://example.com/a/very/long/address")
        String targetUrl,
        @Schema(description = "Optional alias using 3-32 letters, digits, underscores, or hyphens.",
                example = "spring-sale",
                minLength = 3,
                maxLength = 32,
                pattern = "^[A-Za-z0-9_-]{3,32}$")
        String customAlias,
        @Schema(description = "Optional UTC instant after which the short code returns 410 Gone.",
                type = "string",
                format = "date-time",
                example = "2026-12-31T23:59:59Z")
        Instant expiresAt) {
}
