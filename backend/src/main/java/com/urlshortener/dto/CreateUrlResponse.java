package com.urlshortener.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.urlshortener.entity.Url;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response body describing a created short URL.
 *
 * <p>Returns the fully-built {@code shortUrl} rather than only the code, so clients do
 * not have to know how to assemble it. The internal database id is deliberately absent
 * (ADR-007: no enumerable identifiers are exposed).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateUrlResponse(
        @Schema(format = "uri", example = "http://localhost:8080/spring-sale")
        String shortUrl,
        @Schema(example = "spring-sale", pattern = "^[A-Za-z0-9_-]{3,32}$")
        String shortCode,
        @Schema(format = "uri", example = "https://example.com/a/very/long/address")
        String targetUrl,
        Instant createdAt,
        @Schema(type = "string", format = "date-time", example = "2026-12-31T23:59:59Z")
        Instant expiresAt) {

    public static CreateUrlResponse from(Url url, String baseUrl) {
        return new CreateUrlResponse(
                baseUrl + "/" + url.getShortCode(),
                url.getShortCode(),
                url.getTargetUrl(),
                url.getCreatedAt(),
                url.getExpiresAt());
    }
}
