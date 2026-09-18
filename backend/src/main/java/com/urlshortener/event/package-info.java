/**
 * Click analytics events and their listeners.
 *
 * <p>Pattern: Observer, via Spring's application-event mechanism. The redirect path
 * publishes an event and returns; an async listener buffers it and a scheduled drainer
 * persists it, so recording a click never adds latency to the redirect (FR5, NFR1).
 *
 * <p>Privacy rule (NFR8): raw client IPs are never persisted - they are hashed
 * or truncated before leaving this package.
 */
package com.urlshortener.event;
