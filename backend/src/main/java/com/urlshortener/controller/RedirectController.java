package com.urlshortener.controller;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.urlshortener.entity.Url;
import com.urlshortener.event.ClickPublisher;
import com.urlshortener.service.UrlService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Redirect endpoint - the hot path.
 *
 * <p>Mapped at the application root, so the path pattern is constrained to the short
 * code charset. Without that constraint this handler would also capture requests such
 * as {@code /favicon.ico}, turning unrelated 404s into short-code lookups.
 */
@RestController
public class RedirectController {

    /**
     * Mirrors the alias rules and the {@code urls_short_code_charset} database
     * constraint: anything outside this shape cannot be a stored code, so it is not
     * worth a database round trip.
     */
    private static final String SHORT_CODE_PATTERN = "{shortCode:[A-Za-z0-9_-]{3,32}}";

    private final UrlService urlService;
    private final ClickPublisher clickPublisher;

    public RedirectController(UrlService urlService, ClickPublisher clickPublisher) {
        this.urlService = urlService;
        this.clickPublisher = clickPublisher;
    }

    /**
     * Redirects to the stored target and records the click.
     *
     * <p>302 rather than 301 (ADR-004b): a 301 is cached indefinitely by browsers, so
     * repeat visits would never reach this service again and click analytics would
     * silently undercount.
     *
     * <p>Unknown code produces 404 and an expired one 410, both raised by the service
     * and mapped centrally.
     *
     * <p><strong>Only successful redirects are counted.</strong> A 404 or 410 throws
     * before reaching the publish call, so a click means "a visitor was actually sent
     * somewhere" rather than "someone tried a code". Attempts on dead links are an abuse
     * signal, not link traffic, and belong with rate limiting in M5.
     *
     * <p><strong>Note for M3.</strong> When caching is introduced in front of
     * {@code resolve}, this publish call must still run on a cache hit. Otherwise the
     * redirect gets faster and its click silently disappears - the failure mode that is
     * easy to miss when a cache is added after analytics already works.
     */
    @GetMapping("/" + SHORT_CODE_PATTERN)
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        Url url = urlService.resolve(shortCode);

        // Never throws, and hands off to another thread immediately: the visitor's
        // redirect does not wait on analytics.
        clickPublisher.publish(url, request);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url.getTargetUrl()))
                .build();
    }
}
