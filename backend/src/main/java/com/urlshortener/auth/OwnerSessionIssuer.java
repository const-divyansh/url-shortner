package com.urlshortener.auth;

import org.springframework.stereotype.Service;

import com.urlshortener.entity.Owner;
import com.urlshortener.entity.OwnerSession;
import com.urlshortener.repository.OwnerRepository;
import com.urlshortener.repository.OwnerSessionRepository;

/**
 * Issues session tokens for an owner, regardless of how that owner logged in.
 */
@Service
public class OwnerSessionIssuer {

    private final OwnerRepository ownerRepository;
    private final OwnerSessionRepository sessionRepository;
    private final SessionTokenCodec codec;

    public OwnerSessionIssuer(OwnerRepository ownerRepository, OwnerSessionRepository sessionRepository,
            SessionTokenCodec codec) {
        this.ownerRepository = ownerRepository;
        this.sessionRepository = sessionRepository;
        this.codec = codec;
    }

    /**
     * Creates a brand-new guest owner and issues a session for it.
     */
    public IssuedSession issueForNewGuest() {
        Owner owner = ownerRepository.saveAndFlush(Owner.guest());
        return issueFor(owner);
    }

    /**
     * Finds the existing owner for this provider+subject, creating one on first
     * login, then issues a fresh session token for it.
     */
    public IssuedSession issueForProviderIdentity(String provider, String externalSubject, String email) {
        Owner owner = ownerRepository.findByProviderAndExternalSubject(provider, externalSubject)
                .orElseGet(() -> ownerRepository.saveAndFlush(Owner.fromProvider(provider, externalSubject, email)));
        return issueFor(owner);
    }

    private IssuedSession issueFor(Owner owner) {
        String rawToken = codec.generate();
        sessionRepository.saveAndFlush(new OwnerSession(owner.getId(), codec.hash(rawToken)));

        return new IssuedSession(owner.getId(), rawToken, owner.getProvider());
    }
}
