package com.urlshortener.dto;

/**
 * Request to change the active short-code generation strategy.
 */
public record UpdateShortCodeStrategyRequest(
        @io.swagger.v3.oas.annotations.media.Schema(
                description = "Strategy name returned by GET /api/shortcode/strategy.",
                example = "base62-random")
        String strategy) {
}
