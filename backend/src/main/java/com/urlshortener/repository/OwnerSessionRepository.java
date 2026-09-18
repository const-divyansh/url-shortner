package com.urlshortener.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.urlshortener.entity.OwnerSession;

/**
 * Persistence access for issued owner session credentials.
 */
public interface OwnerSessionRepository extends JpaRepository<OwnerSession, Long> {

    Optional<OwnerSession> findByTokenHash(String tokenHash);
}
