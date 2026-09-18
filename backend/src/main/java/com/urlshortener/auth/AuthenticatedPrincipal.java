package com.urlshortener.auth;

import com.urlshortener.entity.IdentityProvider;

/**
 * The authenticated caller.
 *
 * @param ownerId  the owner this request acts as
 * @param provider how that owner's identity was established - see
 *                 {@link IdentityProvider}. This is the owner's <em>identity</em>
 *                 provider, not the mechanism that authenticated this particular
 *                 request: every request is authenticated by the same session token
 *                 regardless of how the session was obtained, so recording that here
 *                 would say nothing a caller could act on.
 */
public record AuthenticatedPrincipal(Long ownerId, String provider) {

    /**
     * Whether this caller holds an anonymous, self-service session.
     *
     * <p>Exists because guests are permitted less: a guest session is minted on demand
     * with no verification at all, so destructive actions are reserved for owners whose
     * identity someone actually vouched for.
     */
    public boolean isGuest() {
        return IdentityProvider.isGuest(provider);
    }
}
