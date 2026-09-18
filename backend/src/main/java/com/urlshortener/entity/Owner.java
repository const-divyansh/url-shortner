package com.urlshortener.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A caller's identity, independent of how many session tokens they hold or how many
 * times they have logged in. 'guest' owners have no {@code externalSubject}/{@code
 * email}; 'google' owners are keyed by Google's stable {@code sub} claim.
 */
@Entity
@Table(name = "owners")
public class Owner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider", nullable = false, length = 32, updatable = false)
    private String provider;

    @Column(name = "external_subject", length = 255, updatable = false)
    private String externalSubject;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Owner() {
        // Required by JPA.
    }

    /**
     * Creates a guest owner: no external identity provider is involved.
     */
    public static Owner guest() {
        return new Owner("guest", null, null);
    }

    /**
     * Creates an owner backed by an external identity provider (e.g. Google).
     */
    public static Owner fromProvider(String provider, String externalSubject, String email) {
        return new Owner(provider, externalSubject, email);
    }

    private Owner(String provider, String externalSubject, String email) {
        this.provider = provider;
        this.externalSubject = externalSubject;
        this.email = email;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getExternalSubject() {
        return externalSubject;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
