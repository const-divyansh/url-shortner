package com.urlshortener.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.urlshortener.entity.ClickEvent;

/**
 * Persistence access for recorded clicks.
 *
 * <p>Queries are parameterised, satisfying the "no raw SQL concatenation" rule.
 */
public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    long countByUrlId(Long urlId);

    /**
     * Most recent clicks first, matching the {@code (url_id, occurred_at DESC)} index so
     * the database can satisfy the ordering from the index rather than sorting.
     */
    List<ClickEvent> findByUrlIdOrderByOccurredAtDesc(Long urlId, Pageable pageable);

    /**
     * Timestamp of the most recent click, or null if there have been none.
     *
     * <p>A dedicated aggregate rather than fetching a page and reading its first row,
     * so "last accessed" does not depend on the history query's pagination.
     */
    @Query("select max(c.occurredAt) from ClickEvent c where c.urlId = :urlId")
    java.time.Instant findLastClickAt(@Param("urlId") Long urlId);
}
