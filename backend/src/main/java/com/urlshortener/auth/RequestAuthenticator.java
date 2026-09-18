package com.urlshortener.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Authenticates one HTTP request.
 */
public interface RequestAuthenticator {

    /**
     * Stable provider name, selected by configuration.
     */
    String name();

    /**
     * Returns the authenticated caller or throws if credentials are missing/invalid.
     */
    AuthenticatedPrincipal authenticate(HttpServletRequest request);
}
