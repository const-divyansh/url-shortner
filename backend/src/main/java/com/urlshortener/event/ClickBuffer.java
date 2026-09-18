package com.urlshortener.event;

import java.util.List;

/**
 * Buffer holding clicks between the redirect and their eventual persistence.
 *
 * <p>An interface so the recording path can be tested without Redis, and so the
 * buffering technology can change without touching the listener or the drainer
 * (Dependency Inversion).
 *
 * <p><strong>Contract:</strong> {@link #enqueue} must never throw. Analytics is lossy
 * by design; a redirect must never fail because the buffer is unavailable (NFR3).
 * Implementations absorb and log their own failures.
 */
public interface ClickBuffer {

    /**
     * Offers a click for eventual persistence. Never throws; a rejected click is
     * dropped and logged.
     */
    void enqueue(ClickRecord record);

    /**
     * Removes and returns up to {@code maxBatchSize} buffered clicks, oldest first.
     * Returns an empty list when the buffer is empty or unavailable.
     */
    List<ClickRecord> drain(int maxBatchSize);
}
