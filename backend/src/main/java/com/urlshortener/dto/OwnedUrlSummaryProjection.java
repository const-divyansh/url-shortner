package com.urlshortener.dto;

import java.time.Instant;

/**
 * Repository-only shape for one URL owned by the authenticated caller, with summary
 * analytics aggregated in the database.
 *
 * <p>Deliberately separate from {@link OwnedUrlSummaryResponse}, the API-facing record:
 * this one is built directly by a JPQL constructor expression
 * ({@code UrlRepository.findOwnedSummaries}), which only has access to columns and
 * aggregates the query can compute. {@code shortUrl} needs {@code app.base-url}, a
 * runtime config value with no meaning inside a query, so it is added afterward by
 * {@code AnalyticsService} when it maps this projection to the response record.
 */
public record OwnedUrlSummaryProjection(
        String shortCode,
        String targetUrl,
        Instant createdAt,
        Instant expiresAt,
        long totalClicks,
        Instant lastClickAt) {
}
