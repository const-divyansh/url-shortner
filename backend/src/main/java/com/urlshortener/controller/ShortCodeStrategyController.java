package com.urlshortener.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.urlshortener.auth.AuthenticatedOwner;
import com.urlshortener.auth.AuthenticatedPrincipal;
import com.urlshortener.config.OpenApiConfig;
import com.urlshortener.dto.ShortCodeStrategyResponse;
import com.urlshortener.dto.UpdateShortCodeStrategyRequest;
import com.urlshortener.exception.ErrorResponse;
import com.urlshortener.exception.ValidationException;
import com.urlshortener.generator.ShortCodeGeneratorFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

/**
 * Reports and changes the active short-code generation strategy.
 *
 * <p><strong>This is global state, not a per-caller preference.</strong> Switching
 * affects every subsequent link created by anyone, so it is an operator action wearing
 * an API-shaped interface. Existing codes are unaffected - resolution is a lookup by
 * {@code short_code} and does not care which algorithm produced it.
 *
 * <p><strong>Authentication is required but is not a sufficient control.</strong>
 * ADR-007 notes that an attacker who can select an enumerable strategy can then scrape
 * the namespace. Requiring a session raises the bar only as far as session issuance is
 * itself controlled - guest login is intentionally open, so a role/scope check is still
 * the real hardening step.
 */
@RestController
@RequestMapping("/api/shortcode/strategy")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME_NAME)
public class ShortCodeStrategyController {

    private final ShortCodeGeneratorFactory generatorFactory;

    public ShortCodeStrategyController(ShortCodeGeneratorFactory generatorFactory) {
        this.generatorFactory = generatorFactory;
    }

    @GetMapping
    @Operation(summary = "Get the active short-code generation strategy")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Strategy returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid session token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ShortCodeStrategyResponse current(@AuthenticatedOwner AuthenticatedPrincipal principal) {
        return describe();
    }

    @PutMapping
    @Operation(summary = "Change the active short-code generation strategy")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Strategy updated"),
            @ApiResponse(responseCode = "400", description = "Requested strategy is invalid",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid session token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ShortCodeStrategyResponse update(@AuthenticatedOwner AuthenticatedPrincipal principal,
                                            @RequestBody UpdateShortCodeStrategyRequest request) {
        String requested = request.strategy() == null ? null : request.strategy().trim();
        if (requested == null || requested.isBlank()) {
            throw new ValidationException("strategy.missing", "Choose a strategy to use.");
        }
        try {
            generatorFactory.activate(requested);
        } catch (IllegalArgumentException e) {
            // Translated rather than propagated: the factory's message names the
            // available strategies, which is useful, but an unmapped
            // IllegalArgumentException would surface as a 500.
            throw new ValidationException("strategy.unknown",
                    "That strategy is not available. Choose one of: "
                            + String.join(", ", generatorFactory.available()) + ".");
        }
        return describe();
    }

    private ShortCodeStrategyResponse describe() {
        return new ShortCodeStrategyResponse(
                generatorFactory.active().name(), generatorFactory.available());
    }
}
