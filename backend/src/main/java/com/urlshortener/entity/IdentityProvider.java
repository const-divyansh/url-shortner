package com.urlshortener.entity;

/**
 * The ways an owner can prove who they are.
 *
 * <p>Centralised because authorisation now depends on these values: deletion is
 * restricted to owners backed by a real external identity. While they were string
 * literals spread across the entity and the OAuth controller, a typo in either place
 * would not fail to compile - it would silently produce an owner whose provider matches
 * nothing, and such an owner would be denied deletion with no obvious cause.
 *
 * <p>Deliberately constants rather than an enum: the value is persisted in {@code
 * owners.provider} as text, and adding a second identity provider later should not
 * require a migration of existing rows or a database enum type.
 */
public final class IdentityProvider {

    /**
     * An anonymous, self-service session. Nobody vouched for this owner: a guest
     * session is minted on request with no verification whatsoever, so it establishes
     * continuity for one browser rather than identity.
     */
    public static final String GUEST = "guest";

    /** An owner authenticated by Google, carrying a verified external subject. */
    public static final String GOOGLE = "google";

    private IdentityProvider() {
    }

    /**
     * Whether {@code provider} denotes an anonymous session.
     *
     * <p>Null counts as guest: an unknown identity must get the smaller set of
     * permissions, never the larger one.
     */
    public static boolean isGuest(String provider) {
        return provider == null || GUEST.equals(provider);
    }
}
