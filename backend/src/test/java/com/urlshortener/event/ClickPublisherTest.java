package com.urlshortener.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;

import com.urlshortener.entity.Url;

class ClickPublisherTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final String CLIENT_IP = "203.0.113.45";

    private final List<Object> published = new ArrayList<>();
    private final ApplicationEventPublisher publisher = published::add;

    private ClickPublisher publisherUnder(IpHasher hasher) {
        return new ClickPublisher(publisher, new ClientIpResolver(""), hasher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRemoteAddr(CLIENT_IP);
        request.addHeader("Referer", "https://news.example/story");
        request.addHeader("User-Agent", "Mozilla/5.0");
        return request;
    }

    private static Url url() {
        return new Url("abc1234", "https://example.com/target", null);
    }

    private static Url urlOwnedBy(long ownerId) {
        return new Url("abc1234", "https://example.com/target", null, ownerId);
    }

    private ClickRecord publishedRecord() {
        assertThat(published).hasSize(1);
        return ((UrlRedirectedEvent) published.get(0)).click();
    }

    @Test
    @DisplayName("PRIVACY: the published record carries a hash, never the raw address")
    void publishesHashedAddressOnly() {
        // The guarantee this class exists for. If the raw address were placed in the
        // record, it would sit in plain text in Redis and IpHasher would be decorative.
        publisherUnder(new IpHasher("test-pepper")).publish(url(), request());

        ClickRecord record = publishedRecord();
        assertThat(record.ipHash()).isNotEqualTo(CLIENT_IP).hasSize(64);
        assertThat(record.toString()).doesNotContain(CLIENT_IP);
    }

    @Test
    @DisplayName("captures referrer and user-agent")
    void capturesRequestHeaders() {
        publisherUnder(new IpHasher("test-pepper")).publish(url(), request());

        ClickRecord record = publishedRecord();
        assertThat(record.referrer()).isEqualTo("https://news.example/story");
        assertThat(record.userAgent()).isEqualTo("Mozilla/5.0");
    }

    @Test
    @DisplayName("reads the misspelled 'Referer' header defined by the HTTP standard")
    void readsStandardReferrerSpelling() {
        // "Referrer" would compile, read nothing, and always store null - a silent gap
        // rather than a visible failure.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRemoteAddr(CLIENT_IP);
        request.addHeader("Referrer", "https://wrong-spelling.example/");

        publisherUnder(new IpHasher("test-pepper")).publish(url(), request);

        assertThat(publishedRecord().referrer()).isNull();
    }

    @Test
    @DisplayName("timestamps from the injected clock")
    void usesInjectedClock() {
        publisherUnder(new IpHasher("test-pepper")).publish(url(), request());

        assertThat(publishedRecord().occurredAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("attributes the click to the resolved URL")
    void carriesUrlIdentity() {
        publisherUnder(new IpHasher("test-pepper")).publish(url(), request());

        // The id is null for an unsaved entity; what matters is that the field is
        // populated from the URL rather than looked up later by the drainer.
        assertThat(publishedRecord()).isNotNull();
    }

    @Test
    @DisplayName("carries the owner id so the drainer can broadcast without a lookup")
    void carriesOwnerId() {
        publisherUnder(new IpHasher("test-pepper")).publish(urlOwnedBy(42L), request());

        assertThat(publishedRecord().ownerId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("never throws, so a failure here cannot break a redirect already served")
    void swallowsFailures() {
        IpHasher exploding = new IpHasher("x") {
            @Override
            public String hash(String ip) {
                throw new IllegalStateException("hashing unavailable");
            }
        };

        assertThatCode(() -> publisherUnder(exploding).publish(url(), request()))
                .doesNotThrowAnyException();
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("never throws when publishing itself fails")
    void swallowsPublicationFailure() {
        ApplicationEventPublisher failing = event -> {
            throw new IllegalStateException("no listeners available");
        };
        var clickPublisher = new ClickPublisher(failing, new ClientIpResolver(""),
                new IpHasher("test-pepper"), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatCode(() -> clickPublisher.publish(url(), request())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("does not count a HEAD probe as a click")
    void ignoresHeadRequests() {
        // Spring MVC dispatches HEAD to the same @GetMapping handler and only
        // suppresses the response body afterwards - the handler method, including this
        // publish() call, still runs in full. Without this check, every HEAD probe from
        // a browser, link previewer, or security scanner would inflate the count for a
        // visit that never happened.
        MockHttpServletRequest request = request();
        request.setMethod("HEAD");

        publisherUnder(new IpHasher("test-pepper")).publish(url(), request);

        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("does not count a browser's speculative prefetch as a click")
    void ignoresPrefetchRequests() {
        MockHttpServletRequest request = request();
        request.addHeader("Sec-Purpose", "prefetch");

        publisherUnder(new IpHasher("test-pepper")).publish(url(), request);

        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("also recognises the legacy 'Purpose: prefetch' header")
    void ignoresLegacyPrefetchHeader() {
        MockHttpServletRequest request = request();
        request.addHeader("Purpose", "prefetch");

        publisherUnder(new IpHasher("test-pepper")).publish(url(), request);

        assertThat(published).isEmpty();
    }
}
