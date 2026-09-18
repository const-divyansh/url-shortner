package com.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.urlshortener.AbstractIntegrationTest;
import com.urlshortener.auth.AuthenticatedPrincipal;
import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.entity.ClickEvent;
import com.urlshortener.entity.Url;
import com.urlshortener.exception.AliasUnavailableException;
import com.urlshortener.exception.ShortCodeExpiredException;

class UrlServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UrlService urlService;

    @Test
    @DisplayName("persists a created link in PostgreSQL and resolves it back")
    void createAndResolvePersistsRealRow() {
        AuthenticatedPrincipal owner = createGooglePrincipal();
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MICROS);

        Url created = urlService.create(
                new CreateUrlRequest("https://example.com/landing", "persist-me", expiresAt),
                owner);

        Url resolved = urlService.resolve("persist-me");

        assertThat(created.getId()).isNotNull();
        assertThat(resolved.getId()).isEqualTo(created.getId());
        assertThat(resolved.getTargetUrl()).isEqualTo("https://example.com/landing");
        assertThat(resolved.getExpiresAt()).isEqualTo(expiresAt);

        var row = jdbcTemplate.queryForMap(
                "select short_code, target_url, owner_id, is_active from urls where id = ?",
                created.getId());
        assertThat(row.get("short_code")).isEqualTo("persist-me");
        assertThat(row.get("target_url")).isEqualTo("https://example.com/landing");
        assertThat(((Number) row.get("owner_id")).longValue()).isEqualTo(owner.ownerId());
        assertThat(row.get("is_active")).isEqualTo(true);
    }

    @Test
    @DisplayName("maps the real short-code unique constraint to alias.unavailable")
    void customAliasCollisionUsesRealDatabaseConstraint() {
        AuthenticatedPrincipal owner = createGooglePrincipal();
        AuthenticatedPrincipal otherOwner = createGooglePrincipal();

        urlService.create(new CreateUrlRequest("https://example.com/first", "taken-alias", null), owner);

        assertThatThrownBy(() -> urlService.create(
                new CreateUrlRequest("https://example.com/second", "taken-alias", null),
                otherOwner))
                .isInstanceOf(AliasUnavailableException.class)
                .hasMessageContaining("taken-alias");

        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from urls where short_code = ?",
                Integer.class,
                "taken-alias");
        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("returns gone once a PostgreSQL-stored expiry has passed")
    void resolveReturnsGoneAfterStoredExpiryPasses() throws InterruptedException {
        AuthenticatedPrincipal owner = createGooglePrincipal();
        Instant expiresAt = Instant.now().plusSeconds(1).truncatedTo(ChronoUnit.MICROS);

        urlService.create(new CreateUrlRequest("https://example.com/expires", "expires-soon", expiresAt), owner);

        Duration remaining = Duration.between(Instant.now(), expiresAt);
        waitFor((remaining.isNegative() ? Duration.ZERO : remaining).plusMillis(300));

        assertThatThrownBy(() -> urlService.resolve("expires-soon"))
                .isInstanceOf(ShortCodeExpiredException.class);
    }

    @Test
    @DisplayName("deleted aliases stay claimed for others but the owner can reclaim them")
    void deleteThenReclaimAliasPreservesOwnershipBoundary() {
        AuthenticatedPrincipal owner = createGooglePrincipal();
        AuthenticatedPrincipal otherOwner = createGooglePrincipal();
        Url original = urlService.create(
                new CreateUrlRequest("https://example.com/original", "reclaim-me", null),
                owner);
        clickEventRepository.saveAndFlush(
                new ClickEvent(original.getId(), Instant.now(), "hash", "https://ref.example", "agent"));

        urlService.delete("reclaim-me", owner);

        assertThatThrownBy(() -> urlService.create(
                new CreateUrlRequest("https://example.com/hijack", "reclaim-me", null),
                otherOwner))
                .isInstanceOf(AliasUnavailableException.class);

        Url reclaimed = urlService.create(
                new CreateUrlRequest("https://example.com/reclaimed", "reclaim-me", null),
                owner);
        Url stored = urlRepository.findByShortCode("reclaim-me").orElseThrow();

        assertThat(reclaimed.getId()).isNotEqualTo(original.getId());
        assertThat(stored.getTargetUrl()).isEqualTo("https://example.com/reclaimed");
        assertThat(stored.getOwnerId()).isEqualTo(owner.ownerId());
        assertThat(stored.isActive()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from urls where short_code = ?",
                Integer.class,
                "reclaim-me")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from click_events where url_id = ?",
                Integer.class,
                original.getId())).isZero();
    }
}
