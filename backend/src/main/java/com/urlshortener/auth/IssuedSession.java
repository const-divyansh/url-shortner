package com.urlshortener.auth;

/**
 * A session token returned once, at login time.
 */
public record IssuedSession(Long ownerId, String token, String provider) {
}
