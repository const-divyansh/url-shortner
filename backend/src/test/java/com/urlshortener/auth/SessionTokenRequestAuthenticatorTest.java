package com.urlshortener.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.urlshortener.entity.IdentityProvider;
import com.urlshortener.exception.AuthenticationFailedException;
import com.urlshortener.exception.AuthenticationRequiredException;
import com.urlshortener.repository.OwnerSessionRepository;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class SessionTokenRequestAuthenticatorTest {

    @Mock
    private OwnerSessionRepository repository;
    @Mock
    private HttpServletRequest request;

    private final SessionTokenCodec codec = new SessionTokenCodec();

    @Test
    @DisplayName("missing Authorization header is a 401")
    void missingTokenIsRejected() {
        when(request.getHeader("Authorization")).thenReturn(null);

        assertThatThrownBy(() -> authenticator().authenticate(request))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    @DisplayName("invalid session token is a 401")
    void invalidTokenIsRejected() {
        when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");
        when(repository.findPrincipalByTokenHash(codec.hash("bad-token")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticator().authenticate(request))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("valid session token resolves to the owning caller")
    void validTokenAuthenticatesOwner() {
        String rawToken = "valid-token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + rawToken);
        when(repository.findPrincipalByTokenHash(codec.hash(rawToken)))
                .thenReturn(Optional.of(new AuthenticatedPrincipal(42L, IdentityProvider.GOOGLE)));

        AuthenticatedPrincipal principal = authenticator().authenticate(request);

        assertThat(principal.ownerId()).isEqualTo(42L);
        // The owner's identity provider, not the authenticator's name: authorisation
        // now depends on telling a guest from a verified identity.
        assertThat(principal.provider()).isEqualTo(IdentityProvider.GOOGLE);
        assertThat(principal.isGuest()).isFalse();
    }

    @Test
    @DisplayName("a guest session is reported as a guest")
    void guestSessionIsMarkedGuest() {
        String rawToken = "guest-token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + rawToken);
        when(repository.findPrincipalByTokenHash(codec.hash(rawToken)))
                .thenReturn(Optional.of(new AuthenticatedPrincipal(7L, IdentityProvider.GUEST)));

        assertThat(authenticator().authenticate(request).isGuest()).isTrue();
    }

    private SessionTokenRequestAuthenticator authenticator() {
        return new SessionTokenRequestAuthenticator(repository, codec);
    }
}
