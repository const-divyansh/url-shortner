package com.urlshortener.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.urlshortener.entity.Url;

/**
 * Response body describing a created short URL.
 *
 * <p>Returns the fully-built {@code shortUrl} rather than only the code, so clients do
 * not have to know how to assemble it. The internal database id is deliberately absent
 * (ADR-007: no enumerable identifiers are exposed).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateUrlResponse(
        String shortUrl,
        String shortCode,
        String targetUrl,
        Instant createdAt,
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
