package com.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.urlshortener.auth.AuthenticatedPrincipal;
import com.urlshortener.auth.LinkAuthorizationPolicy;
import com.urlshortener.entity.IdentityProvider;
import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.entity.Url;
import com.urlshortener.exception.AliasUnavailableException;
import com.urlshortener.exception.CodeGenerationException;
import com.urlshortener.exception.GuestActionForbiddenException;
import com.urlshortener.exception.OwnershipForbiddenException;
import com.urlshortener.exception.ShortCodeDeletedException;
import com.urlshortener.exception.ShortCodeExpiredException;
import com.urlshortener.exception.ShortCodeNotFoundException;
import com.urlshortener.exception.ValidationException;
import com.urlshortener.generator.GenerationContext;
import com.urlshortener.generator.ShortCodeGenerator;
import com.urlshortener.generator.ShortCodeGeneratorFactory;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.validation.UrlValidationChain;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String TARGET = "https://example.com/page";
    private static final AuthenticatedPrincipal OWNER =
            new AuthenticatedPrincipal(7L, IdentityProvider.GOOGLE);
    private static final AuthenticatedPrincipal GUEST =
            new AuthenticatedPrincipal(7L, IdentityProvider.GUEST);

    @Mock
    private UrlRepository repository;
    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private ShortCodeGeneratorFactory generatorFactory;
    @Mock
    private UrlValidationChain validationChain;

    private RecordingGenerator generator;
    private UrlService service;

    /**
     * Records every context it is handed, so tests can assert how the retry loop drives
     * the attempt counter - the contract that makes deterministic strategies work.
     */
    private static final class RecordingGenerator implements ShortCodeGenerator {
        private final List<GenerationContext> contexts = new ArrayList<>();
        private int counter;

        @Override
        public String name() {
            return "recording";
        }

        @Override
        public String generate(GenerationContext context) {
            contexts.add(context);
            return "code" + (counter++);
        }
    }

    @BeforeEach
    void setUp() {
        generator = new RecordingGenerator();
        service = new UrlService(repository, analyticsService, new LinkAuthorizationPolicy(),
                generatorFactory, validationChain, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CreateUrlRequest request(String alias) {
        return new CreateUrlRequest(TARGET, alias, null);
    }

    private static DataIntegrityViolationException collision() {
        return new DataIntegrityViolationException(
                "could not execute statement; duplicate key value violates unique constraint \""
                        + ConstraintViolations.SHORT_CODE_UNIQUE + "\"");
    }

    private static DataIntegrityViolationException otherIntegrityFailure() {
        return new DataIntegrityViolationException(
                "new row violates check constraint \"urls_short_code_charset\"");
    }

    // --- creation with a generated code -----------------------------------------

    @Test
    @DisplayName("stores on the first attempt when no collision occurs")
    void storesOnFirstAttempt() {
        when(generatorFactory.active()).thenReturn(generator);
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        Url created = service.create(request(null), OWNER);

        assertThat(created.getShortCode()).isEqualTo("code0");
        assertThat(created.getOwnerId()).isEqualTo(OWNER.ownerId());
        assertThat(generator.contexts).hasSize(1);
    }

    @Test
    @DisplayName("REGRESSION: increments the attempt on each retry")
    void incrementsAttemptOnRetry() {
        // The contract deterministic strategies depend on. If the loop reused attempt 0,
        // a hash-based generator would propose the identical code forever and any
        // duplicate URL would end as a 500.
        when(generatorFactory.active()).thenReturn(generator);
        when(repository.saveAndFlush(any()))
                .thenThrow(collision())
                .thenThrow(collision())
                .thenAnswer(i -> i.getArgument(0));

        Url created = service.create(request(null), OWNER);

        assertThat(created.getShortCode()).isEqualTo("code2");
        assertThat(generator.contexts).extracting(GenerationContext::attempt)
                .containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("keeps the target URL stable across retries")
    void targetUrlUnchangedAcrossRetries() {
        when(generatorFactory.active()).thenReturn(generator);
        when(repository.saveAndFlush(any()))
                .thenThrow(collision())
                .thenAnswer(i -> i.getArgument(0));

        service.create(request(null), OWNER);

        assertThat(generator.contexts).extracting(GenerationContext::targetUrl)
                .containsOnly(TARGET);
    }

    @Test
    @DisplayName("gives up after the retry budget rather than looping forever")
    void failsAfterMaxAttempts() {
        when(generatorFactory.active()).thenReturn(generator);
        when(repository.saveAndFlush(any())).thenThrow(collision());

        assertThatThrownBy(() -> service.create(request(null), OWNER))
                .isInstanceOf(CodeGenerationException.class);

        assertThat(generator.contexts).hasSize(UrlService.MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("does not retry integrity failures that are not short-code collisions")
    void doesNotRetryUnrelatedIntegrityFailures() {
        // Retrying a charset violation would burn the budget and report a generation
        // failure, hiding the actual defect.
        when(generatorFactory.active()).thenReturn(generator);
        when(repository.saveAndFlush(any())).thenThrow(otherIntegrityFailure());

        assertThatThrownBy(() -> service.create(request(null), OWNER))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(CodeGenerationException.class);

        assertThat(generator.contexts).hasSize(1);
    }

    // --- creation with a custom alias --------------------------------------------

    @Test
    @DisplayName("uses the alias verbatim without consulting a generator")
    void usesAliasVerbatim() {
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        Url created = service.create(request("spring-sale"), OWNER);

        assertThat(created.getShortCode()).isEqualTo("spring-sale");
        assertThat(created.getOwnerId()).isEqualTo(OWNER.ownerId());
        verifyNoInteractions(generatorFactory);
    }

    @Test
    @DisplayName("reports a taken alias as a conflict instead of substituting another code")
    void takenAliasIsAConflict() {
        when(repository.saveAndFlush(any())).thenThrow(collision());

        assertThatThrownBy(() -> service.create(request("spring-sale"), OWNER))
                .isInstanceOf(AliasUnavailableException.class);

        // Exactly one attempt: silently returning a different code would defeat the
        // purpose of requesting a specific alias.
        verify(repository).saveAndFlush(any());
    }

    @Test
    @DisplayName("blank alias is treated as absent, so a code is generated")
    void blankAliasFallsBackToGeneration() {
        when(generatorFactory.active()).thenReturn(generator);
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        Url created = service.create(request("   "), OWNER);

        assertThat(created.getShortCode()).isEqualTo("code0");
    }

    // --- reclaiming an alias the caller previously deleted --------------------------

    @Test
    @DisplayName("REGRESSION: the owner may reuse an alias they deleted")
    void ownerCanReuseTheirOwnDeletedAlias() {
        // Reported from manual testing: after deleting a link, its alias still reported
        // "already taken" - about a link the user could no longer see anywhere, which is
        // indistinguishable from a bug.
        Url deleted = new Url("spring-sale", "https://old.example.com", null, OWNER.ownerId());
        deleted.deactivate();
        when(repository.findByShortCode("spring-sale")).thenReturn(Optional.of(deleted));
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        Url created = service.create(request("spring-sale"), OWNER);

        assertThat(created.getShortCode()).isEqualTo("spring-sale");
        assertThat(created.getTargetUrl()).isEqualTo(TARGET);
        verify(repository).delete(deleted);
    }

    @Test
    @DisplayName("reclaiming discards the previous link's clicks rather than inheriting them")
    void reclaimingClearsPreviousClickHistory() {
        // click_events reference urls(id). Carrying them onto the new link would report
        // a different target URL's traffic as this link's own.
        Url deleted = new Url("spring-sale", "https://old.example.com", null, OWNER.ownerId());
        deleted.deactivate();
        when(repository.findByShortCode("spring-sale")).thenReturn(Optional.of(deleted));
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        service.create(request("spring-sale"), OWNER);

        verify(analyticsService).discardClickHistory(deleted.getId());
    }

    @Test
    @DisplayName("SECURITY: another owner's deleted alias stays claimed")
    void cannotReuseSomeoneElsesDeletedAlias() {
        // The hijack case the retirement rule exists for: a shared link is still in
        // circulation after deletion, so handing its code to a different party would
        // silently redirect everyone holding the old link.
        Url someoneElses = new Url("spring-sale", "https://old.example.com", null, 999L);
        someoneElses.deactivate();
        when(repository.findByShortCode("spring-sale")).thenReturn(Optional.of(someoneElses));
        when(repository.saveAndFlush(any())).thenThrow(collision());

        assertThatThrownBy(() -> service.create(request("spring-sale"), OWNER))
                .isInstanceOf(AliasUnavailableException.class);

        verify(repository, never()).delete(any());
        verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("SECURITY: a LIVE alias of the caller's own is not silently replaced")
    void doesNotReplaceOwnLiveAlias() {
        // Only a deleted link is reclaimable. Releasing a live one would destroy a
        // working link and its history on what looks like an ordinary create.
        Url live = new Url("spring-sale", "https://live.example.com", null, OWNER.ownerId());
        when(repository.findByShortCode("spring-sale")).thenReturn(Optional.of(live));
        when(repository.saveAndFlush(any())).thenThrow(collision());

        assertThatThrownBy(() -> service.create(request("spring-sale"), OWNER))
                .isInstanceOf(AliasUnavailableException.class);

        verify(repository, never()).delete(any());
        verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("an unowned legacy link's alias is not reclaimable")
    void cannotReuseUnownedDeletedAlias() {
        Url legacy = new Url("spring-sale", "https://old.example.com", null);
        legacy.deactivate();
        when(repository.findByShortCode("spring-sale")).thenReturn(Optional.of(legacy));
        when(repository.saveAndFlush(any())).thenThrow(collision());

        assertThatThrownBy(() -> service.create(request("spring-sale"), OWNER))
                .isInstanceOf(AliasUnavailableException.class);

        verify(repository, never()).delete(any());
    }

    // --- validation ordering -------------------------------------------------------

    @Test
    @DisplayName("validates before touching the database")
    void validatesBeforePersisting() {
        org.mockito.Mockito.doThrow(new ValidationException("url.scheme_not_allowed", "nope"))
                .when(validationChain).validate(any());

        assertThatThrownBy(() -> service.create(request(null), OWNER))
                .isInstanceOf(ValidationException.class);

        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(generatorFactory);
    }

    // --- resolution ------------------------------------------------------------------

    @Test
    void resolvesAnActiveCode() {
        Url url = new Url("abc1234", TARGET, null);
        when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(url));

        assertThat(service.resolve("abc1234")).isSameAs(url);
    }

    @Test
    void unknownCodeIsNotFound() {
        when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("missing"))
                .isInstanceOf(ShortCodeNotFoundException.class);
    }

    @Test
    @DisplayName("an expired code is gone, not missing - the distinction 404 cannot express")
    void expiredCodeIsGone() {
        Url expired = new Url("abc1234", TARGET, NOW.minusSeconds(1));
        when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.resolve("abc1234"))
                .isInstanceOf(ShortCodeExpiredException.class);
    }

    @Test
    @DisplayName("expiry exactly at now counts as expired")
    void expiryBoundaryIsInclusive() {
        Url boundary = new Url("abc1234", TARGET, NOW);
        when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(boundary));

        assertThatThrownBy(() -> service.resolve("abc1234"))
                .isInstanceOf(ShortCodeExpiredException.class);
    }

    @Test
    void futureExpiryStillResolves() {
        Url live = new Url("abc1234", TARGET, NOW.plusSeconds(1));
        when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(live));

        assertThat(service.resolve("abc1234").getShortCode()).isEqualTo("abc1234");
    }

    @Test
    @DisplayName("a deleted code is gone, distinct from expired - the owner acted, not the clock")
    void deletedCodeIsGone() {
        Url deleted = new Url("abc1234", TARGET, null);
        deleted.deactivate();
        when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.resolve("abc1234"))
                .isInstanceOf(ShortCodeDeletedException.class);
    }

    // --- deletion --------------------------------------------------------------------

    @Test
    @DisplayName("owner can delete their own link")
    void ownerCanDeleteTheirOwnLink() {
        Url url = new Url("abc1234", TARGET, null, OWNER.ownerId());
        when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(url));

        service.delete("abc1234", OWNER);

        assertThat(url.isActive()).isFalse();
        verify(repository).save(url);
    }

    @Test
    @DisplayName("deleting someone else's link is forbidden, not silently ignored")
    void deletingSomeoneElsesLinkIsForbidden() {
        Url url = new Url("abc1234", TARGET, null, 999L);
        when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(url));

        assertThatThrownBy(() -> service.delete("abc1234", OWNER))
                .isInstanceOf(OwnershipForbiddenException.class);

        assertThat(url.isActive()).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("deleting an unowned (pre-auth) link is forbidden, not silently allowed")
    void deletingLinkWithNoOwnerIsForbidden() {
        Url url = new Url("abc1234", TARGET, null);
        when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(url));

        assertThatThrownBy(() -> service.delete("abc1234", OWNER))
                .isInstanceOf(OwnershipForbiddenException.class);
    }

    @Test
    @DisplayName("SECURITY: a guest session cannot delete, even its own link")
    void guestCannotDelete() {
        // A guest session is minted on demand with nobody vouching for it, and deletion
        // is irreversible for everyone still holding the link.
        assertThatThrownBy(() -> service.delete("abc1234", GUEST))
                .isInstanceOf(GuestActionForbiddenException.class);
    }

    @Test
    @DisplayName("SECURITY: the guest check runs before the link is looked up")
    void guestCheckPrecedesLookup() {
        // Otherwise the response would differ for a code that exists versus one that
        // does not, letting a guest probe for other people's short codes.
        assertThatThrownBy(() -> service.delete("abc1234", GUEST))
                .isInstanceOf(GuestActionForbiddenException.class);

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("a principal with an unknown provider is treated as a guest")
    void unknownProviderIsTreatedAsGuest() {
        // Fail closed: an identity we cannot classify must get the smaller set of
        // permissions, never the larger one.
        assertThatThrownBy(() -> service.delete("abc1234", new AuthenticatedPrincipal(7L, null)))
                .isInstanceOf(GuestActionForbiddenException.class);
    }

    @Test
    @DisplayName("deleting an unknown code is not found")
    void deletingUnknownCodeIsNotFound() {
        when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("missing", OWNER))
                .isInstanceOf(ShortCodeNotFoundException.class);
    }

    @Test
    @DisplayName("deleting an already-deleted code reports not found, not forbidden or a no-op success")
    void deletingAlreadyDeletedCodeIsNotFound() {
        Url url = new Url("abc1234", TARGET, null, OWNER.ownerId());
        url.deactivate();
        when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(url));

        assertThatThrownBy(() -> service.delete("abc1234", OWNER))
                .isInstanceOf(ShortCodeNotFoundException.class);

        verify(repository, never()).save(any());
    }
}
