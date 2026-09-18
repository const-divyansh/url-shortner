package com.urlshortener.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A shortened URL - the canonical record in PostgreSQL (NFR9).
 *
 * <p>Generated codes and custom aliases share the {@code shortCode} column rather than
 * living in separate tables: they are one redirect namespace, so a single unique
 * constraint lets the database enforce uniqueness instead of application code
 * attempting a check-then-act.
 *
 * <p>Deliberately not exposed over HTTP - controllers map to DTOs so that storage
 * layout and API contract can change independently, and so {@link #id} (an internal
 * surrogate key) never leaks.
 */
@Entity
@Table(name = "urls")
public class Url {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, unique = true, length = 32, updatable = false)
    private String shortCode;

    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Null means the link never expires.
     *
     * <p>Marked {@code updatable = false} to enforce the immutability constraint from
     * requirements.md: an editable expiry would become an external event and would then
     * require cache invalidation, exactly like deletion does.
     */
    @Column(name = "expires_at", updatable = false)
    private Instant expiresAt;

    @Column(name = "owner_id", updatable = false)
    private Long ownerId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected Url() {
        // Required by JPA.
    }

    public Url(String shortCode, String targetUrl, Instant expiresAt) {
        this(shortCode, targetUrl, expiresAt, null);
    }

    public Url(String shortCode, String targetUrl, Instant expiresAt, Long ownerId) {
        this.shortCode = shortCode;
        this.targetUrl = targetUrl;
        this.expiresAt = expiresAt;
        this.ownerId = ownerId;
        this.createdAt = Instant.now();
    }

    /**
     * True if this link has an expiry that has already passed.
     *
     * <p>Lives on the entity rather than in the service so that every caller - the
     * database path today, the cache path once caching exists - applies one definition
     * of "expired" instead of duplicating the comparison.
     */
    public boolean isExpiredAt(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    /**
     * Soft-deletes this link. Idempotent by nature - calling it again on an
     * already-deleted link is a no-op, not an error; the service layer decides whether
     * a repeat call should surface as "not found".
     */
    public void deactivate() {
        this.active = false;
    }

    public boolean isActive() {
        return active;
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Long getOwnerId() {
        return ownerId;
    }
}
