package com.urlshortener.exception;

/**
 * Raised when an action is permitted only to owners with a verified external identity,
 * and the caller holds an anonymous guest session.
 *
 * <p>Distinct from {@link OwnershipForbiddenException}, which means "this belongs to
 * someone else". Here the caller genuinely owns the resource - they are simply not
 * trusted to destroy it, because a guest session is minted on demand with no
 * verification. Collapsing the two would tell a guest their own link is not theirs,
 * which is both false and unactionable.
 */
public class GuestActionForbiddenException extends RuntimeException {

    public GuestActionForbiddenException(String message) {
        super(message);
    }
}
