package com.urlshortener.event;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Pushes a click notification to whichever of an owner's browser tabs is currently
 * connected over the {@code /ws} WebSocket channel.
 *
 * <p>Deliberately a thin wrapper: {@link SimpMessagingTemplate} already tracks which
 * WebSocket sessions are open and which STOMP destinations each is subscribed to, so
 * there is no connection registry to maintain here the way the earlier SSE
 * implementation needed one - Spring's STOMP broker owns that state instead.
 *
 * <p>The payload is a bare ping, not the click itself. Sending the click would tempt the
 * frontend to trust an in-memory count instead of the database, and this class has no
 * ownership of "how many clicks" - that question is answered exactly one way, by
 * {@code AnalyticsService}, whichever caller asks.
 */
@Component
public class ClickBroadcaster {

    /**
     * The destination each owner's own session subscribes to. Combined with
     * {@link SimpMessagingTemplate#convertAndSendToUser}, which routes by the
     * {@link java.security.Principal} the handshake resolved
     * ({@code com.urlshortener.config.WebSocketConfig}), this reaches only that
     * owner's own connections - the frontend never needs to know its own owner id to
     * subscribe to it.
     */
    private static final String CLICKS_DESTINATION = "/queue/clicks";

    private final SimpMessagingTemplate messagingTemplate;

    public ClickBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Notifies every connection currently open for {@code ownerId}.
     *
     * <p>{@code ownerId} is null for a link created before any owner existed, which
     * cannot happen post-launch but is guarded anyway since
     * {@link com.urlshortener.entity.Url#getOwnerId()} is nullable at the type level.
     * Sending to an owner with no open connection is a harmless no-op - Spring's broker
     * simply finds no matching session.
     */
    public void broadcast(Long ownerId) {
        if (ownerId == null) {
            return;
        }
        messagingTemplate.convertAndSendToUser(ownerId.toString(), CLICKS_DESTINATION, "click");
    }
}
