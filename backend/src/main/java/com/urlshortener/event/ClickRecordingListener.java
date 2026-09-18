package com.urlshortener.event;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Moves published clicks into the buffer, off the request thread.
 *
 * <p><strong>Why this is {@code @Async} even though the buffer is fast.</strong> The
 * enqueue talks to Redis over the network. If Redis is unreachable the call blocks
 * until the connection timeout, and on the request thread that delay would be added to
 * every redirect. Handing off to a separate executor means even a hanging Redis cannot
 * affect redirect latency.
 *
 * <p>The listener therefore sits between two buffers by design: the executor's queue
 * absorbs request-thread hand-off, and Redis provides durability across restarts.
 */
@Component
public class ClickRecordingListener {

    private final ClickBuffer buffer;

    public ClickRecordingListener(ClickBuffer buffer) {
        this.buffer = buffer;
    }

    @Async("analyticsExecutor")
    @EventListener
    public void onRedirect(UrlRedirectedEvent event) {
        buffer.enqueue(event.click());
    }
}
