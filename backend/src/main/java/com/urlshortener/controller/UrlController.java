package com.urlshortener.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.urlshortener.auth.AuthenticatedOwner;
import com.urlshortener.auth.AuthenticatedPrincipal;
import com.urlshortener.config.AppProperties;
import com.urlshortener.config.OpenApiConfig;
import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.CreateUrlResponse;
import com.urlshortener.dto.OwnedUrlSummaryResponse;
import com.urlshortener.entity.Url;
import com.urlshortener.exception.ErrorResponse;
import com.urlshortener.service.AnalyticsService;
import com.urlshortener.service.UrlService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

/**
 * Creation endpoint.
 *
 * <p>Holds no rules: it converts HTTP to a request object, delegates to the service
 * facade, and maps the result back. Validation failures surface as exceptions handled
 * centrally by {@code GlobalExceptionHandler}, so there is no error handling here.
 */
@RestController
@RequestMapping("/api/urls")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME_NAME)
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
    @Operation(summary = "Create a short URL")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Short URL created"),
            @ApiResponse(responseCode = "400", description = "Request validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid session token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Requested alias is unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Create rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Short-code generation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
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
    @Operation(summary = "List links owned by the current caller")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Owned links returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid session token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<OwnedUrlSummaryResponse> listOwned(@AuthenticatedOwner AuthenticatedPrincipal principal) {
        return analyticsService.listOwnedLinks(principal.ownerId());
    }

    /**
     * Soft-deletes one of the caller's own links.
     *
     * <p>204, not 200: there is no representation to return, and the request is
     * idempotent from the caller's point of view (repeating it after success reports
     * 404, which is correct - it is already gone).
     */
    @DeleteMapping("/{shortCode}")
    @Operation(summary = "Soft-delete one of the caller's links")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Link deleted"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid session token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller cannot delete this link",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Short code was not found or is already deleted",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(@AuthenticatedOwner AuthenticatedPrincipal principal,
                                        @PathVariable String shortCode) {
        urlService.delete(shortCode, principal);
        return ResponseEntity.noContent().build();
    }
}
