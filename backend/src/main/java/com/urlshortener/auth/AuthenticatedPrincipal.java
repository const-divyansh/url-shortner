package com.urlshortener.auth;

/**
 * The authenticated caller, independent of how they proved their identity.
 */
public record AuthenticatedPrincipal(Long ownerId, String provider) {
}
