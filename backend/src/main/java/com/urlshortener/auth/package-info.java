/**
 * Authentication boundary.
 *
 * <p>Pattern: Strategy. One active {@link com.urlshortener.auth.RequestAuthenticator}
 * is selected by configuration, so API-key auth can be the first provider without
 * hard-coding the rest of the application to that mechanism. Controllers depend on an
 * authenticated principal, not on header parsing or key lookup details.
 */
package com.urlshortener.auth;
