package com.urlshortener.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Authenticates the WebSocket handshake before the connection is accepted.
 *
 * <p>A native {@code WebSocket} cannot carry a custom {@code Authorization} header the
 * way every other request in this app does - the browser's WebSocket API only lets you
 * set the target URL and a list of subprotocols. The session token therefore travels as
 * a {@code ?token=} query parameter on the handshake request instead, which is why this
 * lives beside {@link SessionTokenRequestAuthenticator} rather than reusing
 * {@link ActiveRequestAuthenticator} - there is exactly one auth provider that can ever
 * apply to a browser WebSocket client, so there is nothing to select between.
 *
 * <p>The resolved owner id is stashed in the handshake attributes, where
 * {@link com.urlshortener.config.WebSocketConfig}'s handshake handler picks it up to
 * build the {@link java.security.Principal} Spring's STOMP broker uses for
 * {@code /user/queue/...} destinations - so a subscriber only ever needs to know "my own
 * queue", never its own owner id.
 */
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    /** Attribute key the custom handshake handler reads to build the STOMP principal. */
    public static final String OWNER_ID_ATTRIBUTE = "ownerId";

    private final SessionTokenRequestAuthenticator authenticator;

    public WebSocketAuthHandshakeInterceptor(SessionTokenRequestAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            attributes.put(OWNER_ID_ATTRIBUTE, authenticator.authenticateToken(token).ownerId());
            return true;
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Nothing to do: any cleanup need is per-session, not per-handshake.
    }

    private String extractToken(ServerHttpRequest request) {
        String token = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("token");
        return (token == null || token.isBlank()) ? null : token;
    }
}
