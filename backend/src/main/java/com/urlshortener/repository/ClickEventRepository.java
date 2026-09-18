package com.urlshortener.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Removes every click recorded against a link.
     *
     * <p>Used when an owner reclaims their own deleted alias. The clicks belong to the
     * link that previously held that code; carrying them over would report another
     * URL's traffic as the new link's own, and {@code click_events.url_id} references
     * {@code urls(id)}, so they must go before that row can be released.
     *
     * <p>{@code @Transactional} because a derived modifying query has no ambient
     * transaction of its own here - the creation path is deliberately untransacted so
     * its retry loop is not poisoned by a failed statement.
     */
    @Modifying
    @Transactional
    @Query("delete from ClickEvent c where c.urlId = :urlId")
    int deleteByUrlId(@Param("urlId") Long urlId);
}
