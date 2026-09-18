package com.urlshortener.dto;

/**
 * Who the caller currently is, as the API sees them.
 *
 * <p>Exists so the client never has to infer its own identity from which login button
 * was pressed. Remembering that locally would drift the moment a session is restored in
 * a new tab, replaced by a different login, or cleared - and the UI would then offer or
 * hide actions based on a stale guess rather than on what the API will actually permit.
 *
 * <p>Deliberately carries no token and no email: this answers "what may I do", and
 * anything beyond that would be handing out more than the question requires.
 *
 * @param provider the identity provider backing this session, e.g. {@code guest} or
 *                 {@code google}
 * @param guest    whether this is an anonymous session. Sent explicitly rather than
 *                 leaving the client to compare {@code provider} against a literal, so
 *                 adding a third provider later does not silently promote guests
 */
public record SessionInfoResponse(String provider, boolean guest) {
}
