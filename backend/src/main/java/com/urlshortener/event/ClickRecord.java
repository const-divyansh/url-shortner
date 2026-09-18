package com.urlshortener.event;

import java.time.Instant;

/**
 * A redirect that occurred, in transit between the request thread and storage.
 *
 * <p>Serialised into the Redis buffer, so it is a plain record rather than an entity.
 * It carries {@code urlId} - already known because the redirect just resolved the URL -
 * so the drainer needs no lookup to attribute the click.
 *
 * <p>{@code ownerId} travels alongside for the same reason: it is already known at
 * publish time (the resolved {@code Url} carries it), and the drainer needs it too - to
 * route the live click broadcast to the right owner once the batch is actually
 * persisted (see {@link ClickDrainer}). Carrying it here, rather than looking it up
 * again per batch, costs nothing extra: no additional query, just one more field on a
 * record that already exists.
 *
 * <p>Contains a hashed client address only. The raw IP is hashed at the edge and never
 * enters the buffer, so it is not present even transiently in Redis (NFR8).
 */
public record ClickRecord(
        Long urlId,
        Long ownerId,
        Instant occurredAt,
        String ipHash,
        String referrer,
        String userAgent) {
}
