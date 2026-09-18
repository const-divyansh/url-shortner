package com.urlshortener.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One issued, revocable session credential for an {@link Owner}. Split from the
 * owner identity so a single owner can hold multiple valid sessions (e.g. logged in
 * from two browsers) and one session can be revoked - delete the row - without
 * touching the identity it belongs to.
 */
@Entity
@Table(name = "owner_sessions")
public class OwnerSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OwnerSession() {
        // Required by JPA.
    }

    public OwnerSession(Long ownerId, String tokenHash) {
        this.ownerId = ownerId;
        this.tokenHash = tokenHash;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
