package com.urlshortener.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One recorded redirect (FR5).
 *
 * <p>Stores a peppered hash of the client address, never the address itself (NFR8).
 *
 * <p>Holds {@code urlId} as a plain column rather than a JPA association to
 * {@link Url}: rows are inserted in batches by the drainer, and a mapped association
 * would make Hibernate load the parent for every row purely to satisfy the mapping.
 */
@Entity
@Table(name = "click_events")
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url_id", nullable = false, updatable = false)
    private Long urlId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "ip_hash", length = 64, updatable = false)
    private String ipHash;

    @Column(name = "referrer", length = 2048, updatable = false)
    private String referrer;

    @Column(name = "user_agent", length = 512, updatable = false)
    private String userAgent;

    protected ClickEvent() {
        // Required by JPA.
    }

    public ClickEvent(Long urlId, Instant occurredAt, String ipHash, String referrer, String userAgent) {
        this.urlId = urlId;
        this.occurredAt = occurredAt;
        this.ipHash = ipHash;
        this.referrer = referrer;
        this.userAgent = userAgent;
    }

    public Long getId() {
        return id;
    }

    public Long getUrlId() {
        return urlId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getIpHash() {
        return ipHash;
    }

    public String getReferrer() {
        return referrer;
    }

    public String getUserAgent() {
        return userAgent;
    }
}
