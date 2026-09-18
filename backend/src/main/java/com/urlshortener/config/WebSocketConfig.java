package com.urlshortener.config;

import java.security.Principal;
import java.util.Map;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import com.urlshortener.auth.SessionTokenRequestAuthenticator;
import com.urlshortener.auth.WebSocketAuthHandshakeInterceptor;

/**
 * Wires the live click-notification channel: STOMP over WebSocket at {@code /ws}.
 *
 * <p>Replaces the earlier SSE ({@code SseEmitter}) implementation. The behaviour visible
 * to the frontend is unchanged - one push per click, to the caller's own links only -
 * only the transport differs.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final SessionTokenRequestAuthenticator authenticator;
    private final String allowedOrigins;

    public WebSocketConfig(SessionTokenRequestAuthenticator authenticator,
                           @org.springframework.beans.factory.annotation.Value(
                                   "${app.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        this.authenticator = authenticator;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins.split("\\s*,\\s*"))
                .addInterceptors(new WebSocketAuthHandshakeInterceptor(authenticator))
                .setHandshakeHandler(ownerPrincipalHandshakeHandler());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // "/topic" is unused today (no broadcast-to-everyone destination exists) but
        // costs nothing to enable alongside "/queue", which backs the per-owner
        // "/user/queue/clicks" destination that ClickBroadcaster actually sends to.
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Turns the owner id the handshake interceptor resolved into the
     * {@link Principal} Spring's STOMP broker keys {@code /user/queue/...}
     * destinations by.
     *
     * <p>This is what lets the frontend subscribe to the fixed destination
     * {@code /user/queue/clicks} without ever learning or sending its own owner id -
     * the server already knows, from the handshake, which principal a given session
     * belongs to.
     */
    private DefaultHandshakeHandler ownerPrincipalHandshakeHandler() {
        return new DefaultHandshakeHandler() {
            @Override
            protected Principal determineUser(org.springframework.http.server.ServerHttpRequest request,
                                              WebSocketHandler wsHandler,
                                              Map<String, Object> attributes) {
                Object ownerId = attributes.get(WebSocketAuthHandshakeInterceptor.OWNER_ID_ATTRIBUTE);
                if (ownerId == null) {
                    // The handshake interceptor already rejected any request that could
                    // reach here without one; this is an assertion, not a real path.
                    throw new IllegalStateException("WebSocket handshake completed without an authenticated owner");
                }
                return new OwnerPrincipal(ownerId.toString());
            }
        };
    }

    /**
     * A {@link Principal} whose name is the owner id, and nothing else - {@code
     * SimpMessagingTemplate.convertAndSendToUser(ownerId, ...)} matches subscribers by
     * this name, so it need not carry anything beyond that.
     */
    private record OwnerPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}
