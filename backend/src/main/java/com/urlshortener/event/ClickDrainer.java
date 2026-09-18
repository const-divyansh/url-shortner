package com.urlshortener.event;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.urlshortener.entity.ClickEvent;
import com.urlshortener.repository.ClickEventRepository;

/**
 * Moves buffered clicks from Redis into PostgreSQL, which is the system of record.
 *
 * <p>Batched rather than one row at a time: the buffer naturally accumulates between
 * runs, so a single multi-row insert replaces one round trip per click.
 *
 * <p>Runs on every instance. {@code LPOP} is atomic, so instances never process the
 * same click twice.
 *
 * <p><strong>Also the sole source of the live click broadcast.</strong> An earlier
 * design broadcast synchronously, straight off the redirect event - simpler, but wrong:
 * that fires before the click is anywhere durable, since recording is {@code @Async}
 * and persistence itself is batched here. A client reacting to that broadcast by
 * re-reading analytics could race ahead of this drainer and see the count from before
 * its own click. Broadcasting only after a batch is confirmed persisted removes that
 * race entirely - "you were just notified" and "the data now reflects it" become the
 * same guarantee.
 */
@Component
public class ClickDrainer {

    private static final Logger log = LoggerFactory.getLogger(ClickDrainer.class);

    private final ClickBuffer buffer;
    private final ClickEventRepository repository;
    private final ClickBroadcaster broadcaster;
    private final int batchSize;

    public ClickDrainer(ClickBuffer buffer,
                        ClickEventRepository repository,
                        ClickBroadcaster broadcaster,
                        @Value("${app.analytics.batch-size:200}") int batchSize) {
        this.buffer = buffer;
        this.repository = repository;
        this.broadcaster = broadcaster;
        this.batchSize = batchSize;
    }

    /**
     * Drains one batch.
     *
     * <p>{@code fixedDelay} rather than {@code fixedRate}: the delay is measured from
     * the end of the previous run, so a slow database cannot cause overlapping runs to
     * pile up.
     *
     * <p>Not annotated {@code @Transactional}. Spring Data's {@code saveAll} already
     * runs the batch in a single transaction, so the annotation added nothing - and
     * rolling back would not recover anything, because the clicks have already been
     * removed from Redis by the time the insert runs. A failed batch is simply lost,
     * which is the trade documented on {@link RedisClickBuffer}.
     */
    @Scheduled(fixedDelayString = "${app.analytics.drain-interval-ms:1000}")
    public void drain() {
        List<ClickRecord> records = buffer.drain(batchSize);
        if (records.isEmpty()) {
            return;
        }
        try {
            repository.saveAll(records.stream().map(ClickDrainer::toEntity).toList());
            log.debug("Persisted {} click(s)", records.size());
        } catch (RuntimeException e) {
            // Swallowed, not rethrown. Rethrowing would have the scheduler log the same
            // failure a second time while changing nothing: the batch is unrecoverable
            // either way, so this logs the one fact worth knowing - how many were lost.
            log.error("Failed to persist {} click(s); batch discarded", records.size(), e);
            return;
        }

        // Broadcast only for a batch that made it into Postgres - see class javadoc.
        // A Set collapses a burst of several clicks for the same owner into one
        // notification rather than one per click, which the frontend would otherwise
        // have to debounce itself.
        Set<Long> owners = records.stream()
                .map(ClickRecord::ownerId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        owners.forEach(broadcaster::broadcast);
    }

    private static ClickEvent toEntity(ClickRecord record) {
        return new ClickEvent(
                record.urlId(),
                record.occurredAt(),
                record.ipHash(),
                truncate(record.referrer(), 2048),
                truncate(record.userAgent(), 512));
    }

    /**
     * Headers are caller-supplied and unbounded; truncating here keeps an oversized
     * value from failing the insert for the entire batch.
     */
    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
