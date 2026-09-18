package com.urlshortener.event;

/**
 * Published when a redirect is served.
 *
 * <p>Pattern: Observer. The redirect path announces what happened and returns; it holds
 * no reference to analytics. Adding another consumer later requires no change to the
 * redirect code (Open/Closed), and analytics can fail without affecting it.
 *
 * <p>A plain record rather than a subclass of Spring's {@code ApplicationEvent}: since
 * Spring 4.2 any object may be published, so extending the framework class would couple
 * a domain concept to Spring for no benefit and force callers to pass a {@code source}
 * argument that nothing reads.
 *
 * <p>Carries only {@code click}. {@code ownerId} used to travel alongside it so the live
 * broadcast listener could route immediately on this event - but that meant the
 * broadcast fired the instant a redirect happened, racing ahead of the click's actual
 * persistence (which is asynchronous, then batched). {@link ClickRecord} now carries its
 * own {@code ownerId}, and {@link ClickDrainer} broadcasts only after a batch is
 * successfully persisted, so a client that reacts to the broadcast by re-reading
 * analytics is guaranteed to see the click it was just told about.
 */
public record UrlRedirectedEvent(ClickRecord click) {
}
