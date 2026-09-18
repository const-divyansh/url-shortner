package com.urlshortener.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.urlshortener.auth.AuthenticatedOwner;
import com.urlshortener.auth.AuthenticatedPrincipal;
import com.urlshortener.config.AppProperties;
import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.CreateUrlResponse;
import com.urlshortener.dto.OwnedUrlSummaryResponse;
import com.urlshortener.entity.Url;
import com.urlshortener.service.AnalyticsService;
import com.urlshortener.service.UrlService;

/**
 * Creation endpoint.
 *
 * <p>Holds no rules: it converts HTTP to a request object, delegates to the service
 * facade, and maps the result back. Validation failures surface as exceptions handled
 * centrally by {@code GlobalExceptionHandler}, so there is no error handling here.
 */
@RestController
@RequestMapping("/api/urls")
public class UrlController {

    private final UrlService urlService;
    private final AnalyticsService analyticsService;
    private final AppProperties properties;

    public UrlController(UrlService urlService, AnalyticsService analyticsService, AppProperties properties) {
        this.urlService = urlService;
        this.analyticsService = analyticsService;
        this.properties = properties;
    }

    /**
     * Creates a short URL.
     *
     * <p>Always 201: this endpoint always creates a new record, since duplicate target
     * URLs are not deduplicated (ADR-009). A 200 would imply an existing resource was
     * returned instead.
     */
    @PostMapping
    public ResponseEntity<CreateUrlResponse> create(@AuthenticatedOwner AuthenticatedPrincipal principal,
                                                    @RequestBody CreateUrlRequest request) {
        Url created = urlService.create(request, principal);
        CreateUrlResponse body = CreateUrlResponse.from(created, properties.baseUrl());

        return ResponseEntity.created(URI.create(body.shortUrl())).body(body);
    }

    /**
     * Lists the authenticated caller's links so earlier analytics can be reopened
     * without remembering or retyping each short code.
     */
    @GetMapping
    public List<OwnedUrlSummaryResponse> listOwned(@AuthenticatedOwner AuthenticatedPrincipal principal) {
        return analyticsService.listOwnedLinks(principal.ownerId());
    }
}

