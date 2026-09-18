package com.urlshortener.dto;

/**
 * A newly issued session response.
 *
 * <p>The raw token is returned once only. The backend stores only its hash.
 */
public record SessionResponse(
        @io.swagger.v3.oas.annotations.media.Schema(
                description = "Opaque session token to send as Authorization: Bearer <token>.",
                example = "yfGfN1_w-0YPlfD7m4h7Z1oE8Q7p3bKX")
        String token,
        @io.swagger.v3.oas.annotations.media.Schema(example = "guest")
        String provider) {
}
