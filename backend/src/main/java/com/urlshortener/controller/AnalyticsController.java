package com.urlshortener.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.urlshortener.auth.AuthenticatedOwner;
import com.urlshortener.auth.AuthenticatedPrincipal;
import com.urlshortener.config.OpenApiConfig;
import com.urlshortener.dto.AnalyticsResponse;
import com.urlshortener.exception.ErrorResponse;
import com.urlshortener.service.AnalyticsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

/**
 * Click statistics endpoint (FR5).
 */
@RestController
@RequestMapping("/api/urls")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME_NAME)
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
    @Operation(summary = "Get click analytics for one short code")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Analytics returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid session token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller does not own this link",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Short code was not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AnalyticsResponse analytics(
            @AuthenticatedOwner AuthenticatedPrincipal principal,
            @PathVariable String shortCode,
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Requested page size, capped at 100")
            @RequestParam(defaultValue = "20") int size) {
        return analyticsService.forShortCode(principal.ownerId(), shortCode, page, size);
    }
}
