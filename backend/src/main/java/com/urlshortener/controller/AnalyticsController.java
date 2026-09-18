package com.urlshortener.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.urlshortener.auth.AuthenticatedOwner;
import com.urlshortener.auth.AuthenticatedPrincipal;
import com.urlshortener.dto.AnalyticsResponse;
import com.urlshortener.service.AnalyticsService;

/**
 * Click statistics endpoint (FR5).
 */
@RestController
@RequestMapping("/api/urls")
public class AnalyticsController {

    /**
     * Constrains the path variable to the short-code charset, matching
     * {@code RedirectController} and the {@code urls_short_code_charset} database
     * constraint. Anything outside this shape cannot be a stored code, so it is
     * rejected as a 404 by the mapping rather than reaching the database.
     */
    private static final String SHORT_CODE_PATTERN = "{shortCode:[A-Za-z0-9_-]{3,32}}";

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/" + SHORT_CODE_PATTERN + "/analytics")
    public AnalyticsResponse analytics(
            @AuthenticatedOwner AuthenticatedPrincipal principal,
            @PathVariable String shortCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return analyticsService.forShortCode(principal.ownerId(), shortCode, page, size);
    }
}
