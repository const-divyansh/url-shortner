package com.urlshortener.auth;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.urlshortener.config.GoogleOAuthProperties;
import com.urlshortener.exception.AuthenticationFailedException;

/**
 * Talks to Google's OAuth endpoints for the Authorization Code flow.
 *
 * <p>Identity verification uses Google's {@code tokeninfo} endpoint rather than local
 * JWKS signature verification. That is a deliberate scope tradeoff for this project's
 * size: tokeninfo is officially supported by Google for exactly this purpose and needs
 * no JWKS-fetching/caching/rotation code, at the cost of one extra HTTP round trip per
 * login (not per request - the verified identity is only used once, to mint our own
 * session token). A higher-traffic production system would verify the signature
 * locally against Google's published keys instead.
 */
@Service
public class GoogleOAuthClient {

    private static final String AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String TOKEN_INFO_ENDPOINT = "https://oauth2.googleapis.com/tokeninfo";

    private final GoogleOAuthProperties properties;
    private final RestClient restClient;

    public GoogleOAuthClient(GoogleOAuthProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    /**
     * Builds the URL to send the browser to for the Google consent screen.
     */
    public String buildAuthorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTHORIZATION_ENDPOINT)
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email")
                .queryParam("state", state)
                // Our sign-out only clears our own session token, never Google's browser
                // session, so without this Google silently re-authenticates as whichever
                // account is already active instead of letting the user choose. This
                // forces the account chooser every time.
                .queryParam("prompt", "select_account")
                .build()
                .toUriString();
    }

    /**
     * Exchanges an authorization code for the caller's verified Google identity.
     */
    public GoogleIdentity exchangeCodeForIdentity(String code) {
        TokenResponse tokenResponse = restClient.post()
                .uri(TOKEN_ENDPOINT)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("code=" + code
                        + "&client_id=" + properties.clientId()
                        + "&client_secret=" + properties.clientSecret()
                        + "&redirect_uri=" + properties.redirectUri()
                        + "&grant_type=authorization_code")
                .retrieve()
                .body(TokenResponse.class);

        if (tokenResponse == null || tokenResponse.idToken() == null) {
            throw new AuthenticationFailedException("Google did not return an identity token.");
        }

        TokenInfo tokenInfo = restClient.get()
                .uri(TOKEN_INFO_ENDPOINT + "?id_token=" + tokenResponse.idToken())
                .retrieve()
                .body(TokenInfo.class);

        if (tokenInfo == null || tokenInfo.sub() == null
                || !properties.clientId().equals(tokenInfo.aud())) {
            throw new AuthenticationFailedException("Google identity token failed verification.");
        }

        return new GoogleIdentity(tokenInfo.sub(), tokenInfo.email());
    }

    /**
     * The subset of Google's token response this client needs. Explicit
     * {@code @JsonProperty} because the app does not enable snake_case-wide Jackson
     * naming, and Google's response uses {@code id_token}.
     */
    private record TokenResponse(@JsonProperty("id_token") String idToken) {
    }

    /**
     * The subset of Google's tokeninfo response this client needs: {@code aud} is
     * checked against our own client ID so a token minted for a different app cannot
     * be replayed against this one.
     */
    private record TokenInfo(String sub, String email, String aud) {
    }

    /**
     * A verified Google identity: stable subject id plus (best-effort) email.
     */
    public record GoogleIdentity(String subject, String email) {
    }
}
