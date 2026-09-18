package com.urlshortener.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.urlshortener.dto.OwnedUrlSummaryProjection;
import com.urlshortener.entity.Url;

/**
 * Persistence access for {@link Url}.
 *
 * <p>Pattern: Repository. Spring Data generates the implementation from the method
 * name, and every query it derives is parameterised - satisfying the "no raw SQL
 * concatenation" rule without hand-written JPQL.
 *
 * <p>Note the absence of an {@code existsByShortCode} method. That would invite
 * check-then-insert, which is both racy and an extra round trip on every request; the
 * unique constraint is the sole arbiter of availability (ADR-007).
 */
public interface UrlRepository extends JpaRepository<Url, Long> {

    /**
     * Resolves a short code. Returns the row regardless of expiry - the caller
     * distinguishes "never existed" (404) from "expired" (410), which a filtered query
     * could not express, since both would yield an empty result.
     */
    Optional<Url> findByShortCode(String shortCode);

    @Query("""
            select new com.urlshortener.dto.OwnedUrlSummaryProjection(
                u.shortCode,
                u.targetUrl,
                u.createdAt,
                u.expiresAt,
                count(c.id),
                max(c.occurredAt)
            )
            from Url u
            left join ClickEvent c on c.urlId = u.id
            where u.ownerId = :ownerId
            group by u.id, u.shortCode, u.targetUrl, u.createdAt, u.expiresAt
            order by u.createdAt desc
            """)
    List<OwnedUrlSummaryProjection> findOwnedSummaries(@Param("ownerId") Long ownerId);
}
