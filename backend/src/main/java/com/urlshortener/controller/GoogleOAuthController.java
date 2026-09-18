package com.urlshortener.controller;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.urlshortener.auth.GoogleOAuthClient;
import com.urlshortener.auth.IssuedSession;
import com.urlshortener.auth.OwnerSessionIssuer;
import com.urlshortener.config.GoogleOAuthProperties;
import com.urlshortener.exception.AuthenticationFailedException;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

/**
 * "Sign in with Google" login: a full-page redirect flow (Authorization Code), not a
 * CORS/XHR request, so it needs no CORS configuration of its own.
 *
 * <p>The {@code state} cookie is the standard OAuth CSRF defence: a random value set
 * on {@code /start} that Google echoes back as a query parameter on {@code /callback}.
 * Because it is a same-site, HttpOnly cookie, only a request that genuinely
 * originated from this browser's {@code /start} redirect can present the matching
 * value - a forged callback link cannot.
 */
@RestController
@RequestMapping("/api/auth/google")
public class GoogleOAuthController {

    private static final String STATE_COOKIE_NAME = "oauth_state";
    private static final int STATE_BYTES = 24;

    private final GoogleOAuthClient googleOAuthClient;
    private final OwnerSessionIssuer sessionIssuer;
    private final GoogleOAuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public GoogleOAuthController(GoogleOAuthClient googleOAuthClient, OwnerSessionIssuer sessionIssuer,
            GoogleOAuthProperties properties) {
        this.googleOAuthClient = googleOAuthClient;
        this.sessionIssuer = sessionIssuer;
        this.properties = properties;
    }

    @GetMapping("/start")
    public ResponseEntity<Void> start(HttpServletResponse response) {
        String state = generateState();

        Cookie stateCookie = new Cookie(STATE_COOKIE_NAME, state);
        stateCookie.setHttpOnly(true);
        stateCookie.setPath("/api/auth/google");
        stateCookie.setMaxAge(300); // five minutes: long enough for a real login, short-lived by design
        response.addCookie(stateCookie);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, googleOAuthClient.buildAuthorizationUrl(state))
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam("code") String code,
            @RequestParam("state") String state,
            @CookieValue(name = STATE_COOKIE_NAME, required = false) String stateCookie) {

        if (stateCookie == null || !stateCookie.equals(state)) {
            throw new AuthenticationFailedException("Google login could not be verified. Please try again.");
        }

        GoogleOAuthClient.GoogleIdentity identity = googleOAuthClient.exchangeCodeForIdentity(code);
        IssuedSession issued = sessionIssuer.issueForProviderIdentity("google", identity.subject(), identity.email());

        String redirectTo = properties.frontendRedirectUri() + "#session=" + issued.token();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectTo)
                .build();
    }

    private String generateState() {
        byte[] bytes = new byte[STATE_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
