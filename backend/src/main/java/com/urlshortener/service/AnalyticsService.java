package com.urlshortener.service;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.urlshortener.config.AppProperties;
import com.urlshortener.dto.AnalyticsResponse;
import com.urlshortener.dto.OwnedUrlSummaryResponse;
import com.urlshortener.entity.ClickEvent;
import com.urlshortener.entity.Url;
import com.urlshortener.exception.OwnershipForbiddenException;
import com.urlshortener.exception.ShortCodeNotFoundException;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlRepository;

/**
 * Click statistics for a short code (FR5).
 */
@Service
public class AnalyticsService {

    /**
     * Caps how much history one request can pull, so a large {@code size} cannot be
     * used to force an expensive query.
     */
    static final int MAX_PAGE_SIZE = 100;

    private final UrlRepository urlRepository;
    private final ClickEventRepository clickRepository;
    private final AppProperties properties;

    public AnalyticsService(UrlRepository urlRepository, ClickEventRepository clickRepository,
                            AppProperties properties) {
        this.urlRepository = urlRepository;
        this.clickRepository = clickRepository;
        this.properties = properties;
    }

    /**
     * Returns statistics for {@code shortCode}.
     *
     * <p>Expired links still report their statistics - the link stops redirecting, but
     * its history remains meaningful and is not deleted. Only a genuinely unknown code
     * is a 404.
     */
    public AnalyticsResponse forShortCode(Long ownerId, String shortCode, int page, int size) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));
        verifyOwnership(url, ownerId);

        int safePage = Math.max(page, 0);
        // Not Math.clamp - that is Java 21 and this project targets 17 (ADR-001).
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        long total = clickRepository.countByUrlId(url.getId());
        Instant lastClickAt = clickRepository.findLastClickAt(url.getId());
        List<ClickEvent> events = clickRepository.findByUrlIdOrderByOccurredAtDesc(
                url.getId(), PageRequest.of(safePage, safeSize));

        return new AnalyticsResponse(
                url.getShortCode(),
                url.getTargetUrl(),
                total,
                lastClickAt,
                safePage,
                safeSize,
                events.stream().map(AnalyticsService::toEntry).toList());
    }

    public List<OwnedUrlSummaryResponse> listOwnedLinks(Long ownerId) {
        return urlRepository.findOwnedSummaries(ownerId).stream()
                .map(projection -> OwnedUrlSummaryResponse.from(projection, properties.baseUrl()))
                .toList();
    }

    private static void verifyOwnership(Url url, Long ownerId) {
        if (url.getOwnerId() == null || !url.getOwnerId().equals(ownerId)) {
            throw new OwnershipForbiddenException("You do not have access to this link.");
        }
    }

    private static AnalyticsResponse.ClickEntry toEntry(ClickEvent event) {
        return new AnalyticsResponse.ClickEntry(
                event.getOccurredAt(), event.getReferrer(), event.getUserAgent());
    }
}
