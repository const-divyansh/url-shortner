package com.urlshortener.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.urlshortener.auth.AuthenticatedOwner;
import com.urlshortener.auth.AuthenticatedPrincipal;
import com.urlshortener.dto.ShortCodeStrategyResponse;
import com.urlshortener.dto.UpdateShortCodeStrategyRequest;
import com.urlshortener.exception.ValidationException;
import com.urlshortener.generator.ShortCodeGeneratorFactory;

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
 * the namespace. Requiring a key raises the bar only as far as key issuance is itself
 * controlled - while {@code POST /api/auth/keys} is open and unlimited, anyone can mint
 * a key and reach this endpoint. Closing that needs either a role/scope check or
 * rate-limited issuance; recorded here so the gap is visible in the code.
 */
@RestController
@RequestMapping("/api/shortcode/strategy")
public class ShortCodeStrategyController {

    private final ShortCodeGeneratorFactory generatorFactory;

    public ShortCodeStrategyController(ShortCodeGeneratorFactory generatorFactory) {
        this.generatorFactory = generatorFactory;
    }

    @GetMapping
    public ShortCodeStrategyResponse current(@AuthenticatedOwner AuthenticatedPrincipal principal) {
        return describe();
    }

    @PutMapping
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
