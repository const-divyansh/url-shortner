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

import com.urlshortener.entity.OwnerSession;
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
        when(repository.findByTokenHash(codec.hash("bad-token")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticator().authenticate(request))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("valid session token resolves to the owning caller")
    void validTokenAuthenticatesOwner() {
        String rawToken = "valid-token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + rawToken);
        when(repository.findByTokenHash(codec.hash(rawToken)))
                .thenReturn(Optional.of(ownerSession()));

        AuthenticatedPrincipal principal = authenticator().authenticate(request);

        assertThat(principal.ownerId()).isEqualTo(42L);
        assertThat(principal.provider()).isEqualTo(SessionTokenRequestAuthenticator.PROVIDER_NAME);
    }

    private SessionTokenRequestAuthenticator authenticator() {
        return new SessionTokenRequestAuthenticator(repository, codec);
    }

    private static OwnerSession ownerSession() {
        return new OwnerSession(42L, "ignored-hash");
    }
}
