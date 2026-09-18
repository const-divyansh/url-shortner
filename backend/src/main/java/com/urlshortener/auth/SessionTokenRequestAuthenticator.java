package com.urlshortener.auth;

import org.springframework.stereotype.Component;

import com.urlshortener.entity.OwnerSession;
import com.urlshortener.exception.AuthenticationFailedException;
import com.urlshortener.exception.AuthenticationRequiredException;
import com.urlshortener.repository.OwnerSessionRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Authenticates callers by an {@code Authorization: Bearer <token>} session token,
 * regardless of whether that session originated from a guest or a Google login -
 * both produce the same kind of opaque, DB-backed token.
 */
@Component
public class SessionTokenRequestAuthenticator implements RequestAuthenticator {

    public static final String PROVIDER_NAME = "session-token";

    private final OwnerSessionRepository repository;
    private final SessionTokenCodec codec;

    public SessionTokenRequestAuthenticator(OwnerSessionRepository repository, SessionTokenCodec codec) {
        this.repository = repository;
        this.codec = codec;
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public AuthenticatedPrincipal authenticate(HttpServletRequest request) {
        String rawToken = extractBearerToken(request);
        if (rawToken == null || rawToken.isBlank()) {
            throw new AuthenticationRequiredException("Sign in to continue.");
        }
        return authenticateToken(rawToken);
    }

    /**
     * Authenticates a raw session token directly, without an {@link HttpServletRequest}.
     *
     * <p>Extracted so the WebSocket handshake can reuse the exact same lookup this
     * class already does for HTTP: a native {@code WebSocket} cannot send an
     * {@code Authorization} header at all, so its token arrives as a handshake query
     * parameter instead of a header - everything downstream of "here is the raw
     * token string" is identical either way.
     */
    public AuthenticatedPrincipal authenticateToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AuthenticationRequiredException("Sign in to continue.");
        }

        OwnerSession session = repository.findByTokenHash(codec.hash(rawToken.trim()))
                .orElseThrow(() -> new AuthenticationFailedException("Your session is no longer valid."));

        return new AuthenticatedPrincipal(session.getOwnerId(), PROVIDER_NAME);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, SessionTokenCodec.AUTH_SCHEME + " ", 0,
                SessionTokenCodec.AUTH_SCHEME.length() + 1)) {
            return null;
        }
        return header.substring(SessionTokenCodec.AUTH_SCHEME.length() + 1);
    }
}
