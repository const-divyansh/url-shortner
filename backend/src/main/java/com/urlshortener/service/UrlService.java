package com.urlshortener.service;

import java.time.Clock;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.auth.AuthenticatedPrincipal;
import com.urlshortener.auth.LinkAuthorizationPolicy;
import com.urlshortener.entity.Url;
import com.urlshortener.exception.AliasUnavailableException;
import com.urlshortener.exception.CodeGenerationException;
import com.urlshortener.exception.ShortCodeDeletedException;
import com.urlshortener.exception.ShortCodeExpiredException;
import com.urlshortener.exception.ShortCodeNotFoundException;
import com.urlshortener.generator.GenerationContext;
import com.urlshortener.generator.ShortCodeGenerator;
import com.urlshortener.generator.ShortCodeGeneratorFactory;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.validation.UrlValidationChain;
import com.urlshortener.validation.ValidationTarget;

/**
 * Creation and resolution of short URLs.
 *
 * <p>Pattern: Facade. Controllers depend on this one type rather than on the
 * validation chain, generator factory, and repository individually.
 *
 * <p><strong>This class is deliberately not {@code @Transactional}.</strong> See
 * {@link #create}.
 */
@Service
public class UrlService {

    private static final Logger log = LoggerFactory.getLogger(UrlService.class);

    /**
     * Not sized to absorb collisions - five consecutive collisions is around 10^-23
     * even at 100 million stored URLs. It is a circuit breaker so that a generator
     * violating the vary-by-attempt contract fails loudly instead of looping forever.
     */
    static final int MAX_ATTEMPTS = 5;

    private final UrlRepository repository;
    private final AnalyticsService analyticsService;
    private final LinkAuthorizationPolicy authorizationPolicy;
    private final ShortCodeGeneratorFactory generatorFactory;
    private final UrlValidationChain validationChain;
    private final Clock clock;

    public UrlService(UrlRepository repository,
                      AnalyticsService analyticsService,
                      LinkAuthorizationPolicy authorizationPolicy,
                      ShortCodeGeneratorFactory generatorFactory,
                      UrlValidationChain validationChain,
                      Clock clock) {
        this.repository = repository;
        this.analyticsService = analyticsService;
        this.authorizationPolicy = authorizationPolicy;
        this.generatorFactory = generatorFactory;
        this.validationChain = validationChain;
        this.clock = clock;
    }

    /**
     * Validates the request and stores a new short URL.
     *
     * <p><strong>Why there is no {@code @Transactional} here.</strong> In PostgreSQL a
     * failed statement aborts the surrounding transaction: every subsequent statement
     * fails with "current transaction is aborted" regardless of whether it would
     * otherwise succeed. If this method held one transaction, the first collision would
     * poison it and every retry would fail for the wrong reason. Leaving it
     * untransacted means each {@code saveAndFlush} runs in its own transaction - which
     * is exactly the required semantics, so the retry loop sits outside the transaction
     * boundary by construction.
     *
     * <p>Duplicate target URLs are not deduplicated (ADR-009): the same URL submitted
     * twice yields two independent codes.
     */
    public Url create(CreateUrlRequest request, AuthenticatedPrincipal principal) {
        validationChain.validate(
                new ValidationTarget(request.targetUrl(), request.customAlias(), request.expiresAt()));

        String targetUrl = request.targetUrl().trim();
        Long ownerId = principal.ownerId();

        return request.customAlias() == null || request.customAlias().isBlank()
                ? createWithGeneratedCode(targetUrl, request, ownerId)
                : createWithAlias(targetUrl, request, ownerId);
    }

    /**
     * Stores the URL under a caller-supplied alias.
     *
     * <p>A single attempt: a taken alias is reported as a conflict rather than retried,
     * because substituting a different code would defeat the point of requesting one.
     */
    private Url createWithAlias(String targetUrl, CreateUrlRequest request, Long ownerId) {
        releaseOwnReclaimableAlias(request.customAlias(), ownerId);

        try {
            return repository.saveAndFlush(new Url(request.customAlias(), targetUrl, request.expiresAt(), ownerId));
        } catch (DataIntegrityViolationException e) {
            if (ConstraintViolations.isShortCodeCollision(e)) {
                throw new AliasUnavailableException(request.customAlias());
            }
            throw e;
        }
    }

    /**
     * Frees an alias the caller previously deleted or let expire, so they can reuse
     * their own code.
     *
     * <p><strong>Only the original owner, and only their own deleted or expired
     * link.</strong> A deleted or expired code stays claimed against everyone else on
     * purpose: a short link that has been shared is still in circulation after
     * deletion or expiry, so handing the code to a different party would silently
     * redirect everyone holding the old link to a destination of that party's
     * choosing. That hijack risk does not exist when the same owner reclaims it - the
     * link was theirs to point wherever they like either way - and blocking them
     * produced an "already taken" message about a link they could no longer reach,
     * which is indistinguishable from a bug.
     *
     * <p><strong>The old row is deleted, not revived.</strong> Reusing it would carry
     * over {@code created_at} and {@code expires_at}, both deliberately immutable
     * ({@code updatable = false}), so a reclaimed alias would silently inherit the
     * previous link's expiry and creation time. A fresh row is also what makes the
     * click history correct: {@code click_events} reference {@code urls(id)}, so a new
     * identity cannot inherit traffic recorded for a different target URL.
     *
     * <p><strong>Accepted data loss, stated plainly.</strong> The previous link's click
     * history is destroyed here. It has to be - those rows reference the row being
     * removed - and keeping them would misattribute another URL's traffic. The history
     * belonged to a link the owner had already deleted or that had already expired.
     *
     * <p>Check-then-act is safe here: if another request claims the alias in between,
     * the insert that follows still fails on the unique index and the caller gets the
     * same conflict they would have got anyway. The database remains the only arbiter.
     */
    private void releaseOwnReclaimableAlias(String alias, Long ownerId) {
        repository.findByShortCode(alias)
                .filter(existing -> !existing.isActive() || existing.isExpiredAt(clock.instant()))
                .filter(existing -> ownerId.equals(existing.getOwnerId()))
                .ifPresent(existing -> {
                    log.info("Releasing {} alias '{}' for reuse by its owner",
                            existing.isActive() ? "expired" : "deleted", alias);
                    analyticsService.discardClickHistory(existing.getId());
                    repository.delete(existing);
                    repository.flush();
                });
    }

    /**
     * Stores the URL under a generated code, retrying on collision.
     *
     * <p>No availability check precedes the insert. Asking "is this code free?" first is
     * both racy - two requests can be told yes for the same code - and an extra round
     * trip on every request. The unique index is the only component that can arbitrate,
     * so its rejection is used as the collision signal (ADR-007).
     */
    private Url createWithGeneratedCode(String targetUrl, CreateUrlRequest request, Long ownerId) {
        ShortCodeGenerator generator = generatorFactory.active();
        GenerationContext context = GenerationContext.firstAttempt(targetUrl);

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = generator.generate(context);
            try {
                return repository.saveAndFlush(new Url(code, targetUrl, request.expiresAt(), ownerId));
            } catch (DataIntegrityViolationException e) {
                if (!ConstraintViolations.isShortCodeCollision(e)) {
                    // A charset or other integrity failure is a defect, not a
                    // collision. Rethrowing keeps it visible instead of letting the
                    // loop mask it as a generation failure.
                    throw e;
                }
                log.debug("Short-code collision on attempt {} using strategy '{}'",
                        attempt, generator.name());
                // Advancing the attempt is what makes the next proposal different.
                // A deterministic strategy would otherwise repeat the same code.
                context = context.nextAttempt();
            }
        }
        throw new CodeGenerationException(generator.name(), MAX_ATTEMPTS);
    }

    /**
     * Resolves a short code to its target URL.
     *
     * @throws ShortCodeNotFoundException if no such code exists (404)
     * @throws ShortCodeExpiredException  if the code existed but has expired (410)
     * @throws ShortCodeDeletedException  if the owner deleted the code (410)
     */
    public Url resolve(String shortCode) {
        Optional<Url> found = repository.findByShortCode(shortCode);
        Url url = found.orElseThrow(() -> new ShortCodeNotFoundException(shortCode));

        if (!url.isActive()) {
            throw new ShortCodeDeletedException(shortCode);
        }
        if (url.isExpiredAt(clock.instant())) {
            throw new ShortCodeExpiredException(shortCode);
        }
        return url;
    }

    /**
     * Soft-deletes a link on behalf of its owner.
     *
     * <p>Soft, never a real {@code DELETE}: removing the row would let the short code
     * be reissued later, letting an attacker hijack a link already shared and in
     * circulation, and would strip {@code click_events} of a valid owner reference.
     *
     * @throws ShortCodeNotFoundException  if no such code exists, or it was already
     *                                     deleted - the caller cannot distinguish
     *                                     "never existed" from "already gone", which is
     *                                     the same non-information a stranger would see
     * @throws OwnershipForbiddenException   if the code exists but belongs to someone else
     * @throws GuestActionForbiddenException if the caller holds an anonymous guest
     *                                       session. Deletion is irreversible for
     *                                       everyone holding the link, and a guest
     *                                       session is minted on demand with nobody
     *                                       vouching for it, so the destructive action
     *                                       is reserved for a verified identity
     */
    public void delete(String shortCode, AuthenticatedPrincipal principal) {
        // Before the lookup, so a guest cannot use the response to learn whether a code
        // exists or who owns it.
        authorizationPolicy.requireVerifiedIdentity(principal);

        Url url = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));

        authorizationPolicy.requireOwnership(url, principal.ownerId());
        if (!url.isActive()) {
            throw new ShortCodeNotFoundException(shortCode);
        }
        url.deactivate();
        repository.save(url);
    }
}
