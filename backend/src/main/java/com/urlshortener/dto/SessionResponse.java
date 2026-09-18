package com.urlshortener.dto;

/**
 * A newly issued session response.
 *
 * <p>The raw token is returned once only. The backend stores only its hash.
 */
public record SessionResponse(String token, String provider) {
}
