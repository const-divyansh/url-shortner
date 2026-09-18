package com.urlshortener.event;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Redis-backed {@link ClickBuffer}.
 *
 * <p>Uses a Redis list as a FIFO queue: {@code RPUSH} to append, {@code LPOP count} to
 * take a batch. {@code LPOP} with a count is atomic, so multiple application instances
 * can drain the same queue concurrently without any instance seeing another's items.
 *
 * <p><strong>Why Redis rather than an in-process queue.</strong> Buffered clicks
 * survive an application restart, which an in-memory queue cannot offer.
 *
 * <p><strong>Accepted loss windows.</strong> Analytics is deliberately lossy so that the
 * redirect never is:
 * <ul>
 *   <li>Redis unavailable at enqueue - the click is dropped and logged, and the
 *       redirect still succeeds (NFR3).</li>
 *   <li>A crash after {@code LPOP} but before the batch is persisted loses that batch.
 *       Avoiding this needs a processing-list handshake plus a sweeper for instances
 *       that die mid-batch; that complexity is not justified for data we already
 *       discard when Redis is down.</li>
 * </ul>
 */
@Component
public class RedisClickBuffer implements ClickBuffer {

    private static final Logger log = LoggerFactory.getLogger(RedisClickBuffer.class);

    /**
     * Versioned so a future change to the serialised shape can use a new key rather
     * than encountering records it cannot deserialise.
     */
    private final String queueKey;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisClickBuffer(StringRedisTemplate redis,
                            ObjectMapper objectMapper,
                            @Value("${app.analytics.queue-key:clicks:v1}") String queueKey) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.queueKey = queueKey;
    }

    @Override
    public void enqueue(ClickRecord record) {
        try {
            redis.opsForList().rightPush(queueKey, objectMapper.writeValueAsString(record));
        } catch (JsonProcessingException e) {
            log.warn("Dropping click: could not serialise", e);
        } catch (RuntimeException e) {
            // Deliberately swallowed. The redirect has already been served; failing here
            // must not propagate, and must not be retried on the caller's behalf.
            log.warn("Dropping click: buffer unavailable ({})", e.getMessage());
        }
    }

    @Override
    public List<ClickRecord> drain(int maxBatchSize) {
        List<String> payloads;
        try {
            payloads = redis.opsForList().leftPop(queueKey, maxBatchSize);
        } catch (RuntimeException e) {
            log.warn("Click buffer unavailable while draining ({})", e.getMessage());
            return List.of();
        }
        if (payloads == null || payloads.isEmpty()) {
            return List.of();
        }

        List<ClickRecord> records = new ArrayList<>(payloads.size());
        for (String payload : payloads) {
            try {
                records.add(objectMapper.readValue(payload, ClickRecord.class));
            } catch (JsonProcessingException e) {
                // One unreadable entry must not discard the rest of the batch.
                log.warn("Discarding unreadable click payload", e);
            }
        }
        return records;
    }
}
