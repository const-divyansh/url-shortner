package com.urlshortener.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.urlshortener.auth.AuthenticatedPrincipal;
import com.urlshortener.entity.OwnerSession;

/**
 * Persistence access for issued owner session credentials.
 */
public interface OwnerSessionRepository extends JpaRepository<OwnerSession, Long> {

    Optional<OwnerSession> findByTokenHash(String tokenHash);

    /**
     * Resolves a session token straight to the caller it authenticates, including the
     * owner's identity provider.
     *
     * <p>One query rather than "find the session, then load its owner". This runs on
     * every authenticated request, so a second round trip here would be paid by every
     * create, list, analytics and delete call for a single column.
     *
     * <p>An explicit join on {@code Owner}, because {@code OwnerSession} stores a plain
     * {@code ownerId} rather than a mapped association - deliberately, so issuing a
     * session never drags an owner entity into the persistence context.
     */
    @Query("select new com.urlshortener.auth.AuthenticatedPrincipal(s.ownerId, o.provider) "
            + "from OwnerSession s, Owner o "
            + "where o.id = s.ownerId and s.tokenHash = :tokenHash")
    Optional<AuthenticatedPrincipal> findPrincipalByTokenHash(@Param("tokenHash") String tokenHash);
}
