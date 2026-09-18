package com.urlshortener.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Analytics for one short code (FR5).
 *
 * @param shortCode    the code these statistics describe
 * @param targetUrl    where it points
 * @param totalClicks  all recorded clicks, not merely those on the returned page
 * @param lastClickAt  most recent click, or null if never clicked
 * @param page         zero-based index of the returned page
 * @param pageSize     maximum entries per page
 * @param clicks       the requested page of click history, most recent first
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalyticsResponse(
        String shortCode,
        String targetUrl,
        long totalClicks,
        Instant lastClickAt,
        int page,
        int pageSize,
        List<ClickEntry> clicks) {

    /**
     * A single recorded click.
     *
     * <p>Exposes no identifier for the visitor. The stored hash is deliberately omitted:
     * returning it would let a caller confirm whether a given address visited a link by
     * hashing candidates, which would undo the point of not storing addresses.
     */
    public record ClickEntry(Instant occurredAt, String referrer, String userAgent) {
    }
}
