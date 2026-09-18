package com.urlshortener.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.urlshortener.entity.Owner;

/**
 * Persistence access for owner identities.
 */
public interface OwnerRepository extends JpaRepository<Owner, Long> {

    Optional<Owner> findByProviderAndExternalSubject(String provider, String externalSubject);
}
