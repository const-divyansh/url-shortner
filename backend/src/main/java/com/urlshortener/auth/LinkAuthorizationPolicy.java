package com.urlshortener.auth;

import org.springframework.stereotype.Component;

import com.urlshortener.entity.Url;
import com.urlshortener.exception.GuestActionForbiddenException;
import com.urlshortener.exception.OwnershipForbiddenException;

/**
 * Decides what a caller is permitted to do with a link.
 *
 * <p><strong>Why this exists.</strong> These rules were previously inline conditions
 * inside the services that needed them, and the ownership check in particular existed
 * twice, byte for byte, including its message. Duplicating an access-control rule is a
 * different class of problem from duplicating ordinary logic: when the rule changes and
 * one copy is missed, the result is not a bug that shows up as a wrong answer, it is a
 * caller quietly retaining access they should have lost.
 *
 * <p>Gathering them here also makes the permission model legible. Previously the only
 * way to answer "what may a guest do?" was to read every service method looking for
 * conditions; now the answer is this file.
 *
 * <p><strong>Throws rather than returns a boolean.</strong> A predicate makes ignoring
 * the answer a silent omission - {@code if (policy.mayDelete(...))} with no else branch
 * compiles and looks deliberate. A method that either returns normally or throws cannot
 * be used incorrectly by accident.
 *
 * <p>Deliberately not a Spring Security configuration. The rules here are about domain
 * ownership of a specific row, which is decided after the entity is loaded - not about
 * which endpoints a role may reach, which is what URL-level security expresses well.
 */
@Component
public class LinkAuthorizationPolicy {

    /**
     * Asserts the caller may read or manage {@code url}.
     *
     * <p>A link with no owner - created before ownership existed - is treated as
     * belonging to nobody rather than to everybody. Failing closed on legacy data is
     * the only safe default: the alternative silently exposes every pre-auth link to
     * the first caller who asks for it.
     */
    public void requireOwnership(Url url, Long ownerId) {
        if (url.getOwnerId() == null || !url.getOwnerId().equals(ownerId)) {
            throw new OwnershipForbiddenException("You do not have access to this link.");
        }
    }

    /**
     * Asserts the caller is permitted to destroy something irreversibly.
     *
     * <p>Guests are excluded. A guest session is issued on demand to anyone who asks,
     * with nothing and nobody vouching for it, so it establishes continuity for one
     * browser rather than identity. Deletion is irreversible and affects everyone still
     * holding the link, so it is reserved for an identity that was actually verified.
     *
     * <p>Callers should invoke this <em>before</em> loading the target row, so the
     * response cannot differ between a link that exists and one that does not - which
     * would otherwise let a guest probe for other people's short codes.
     */
    public void requireVerifiedIdentity(AuthenticatedPrincipal principal) {
        if (principal.isGuest()) {
            throw new GuestActionForbiddenException(
                    "Sign in with Google to delete links. Guest sessions cannot delete.");
        }
    }
}
