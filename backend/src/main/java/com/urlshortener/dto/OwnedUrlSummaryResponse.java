package com.urlshortener.dto;

import java.time.Instant;

/**
 * One URL owned by the authenticated caller, with summary analytics.
 *
 * <p>{@code shortUrl} is the fully-built link ({@code baseUrl + "/" + shortCode}), the
 * same shape {@code CreateUrlResponse} returns, so a caller never has to know or
 * reconstruct the base URL itself.
 */
public record OwnedUrlSummaryResponse(
        String shortCode,
        String shortUrl,
        String targetUrl,
        Instant createdAt,
        Instant expiresAt,
        long totalClicks,
        Instant lastClickAt) {

    public static OwnedUrlSummaryResponse from(OwnedUrlSummaryProjection projection, String baseUrl) {
        return new OwnedUrlSummaryResponse(
                projection.shortCode(),
                baseUrl + "/" + projection.shortCode(),
                projection.targetUrl(),
                projection.createdAt(),
                projection.expiresAt(),
                projection.totalClicks(),
                projection.lastClickAt());
    }
}
