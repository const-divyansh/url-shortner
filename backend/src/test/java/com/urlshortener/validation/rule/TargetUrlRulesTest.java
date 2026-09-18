package com.urlshortener.validation.rule;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.urlshortener.exception.ValidationException;
import com.urlshortener.validation.ValidationTarget;

class TargetUrlRulesTest {

    private static ValidationTarget url(String value) {
        return new ValidationTarget(value, null, null);
    }

    // --- syntax -----------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"not a url", "example.com", "/relative/path"})
    @DisplayName("rejects values that are not absolute URLs")
    void rejectsMalformedUrls(String value) {
        assertThatThrownBy(() -> new TargetUrlSyntaxRule().validate(url(value)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsMissingUrl() {
        assertThatThrownBy(() -> new TargetUrlSyntaxRule().validate(url(null)))
                .isInstanceOf(ValidationException.class)
                .extracting("code").isEqualTo("url.missing");
    }

    @Test
    void acceptsWellFormedUrl() {
        assertThatCode(() -> new TargetUrlSyntaxRule().validate(url("https://example.com/a?b=c#d")))
                .doesNotThrowAnyException();
    }

    // --- scheme -----------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)//x.com",
            "file:///etc/passwd",
            "ftp://example.com/f",
            "gopher://example.com/"
    })
    @DisplayName("allows only http and https, so dangerous schemes need not be enumerated")
    void rejectsDisallowedSchemes(String value) {
        assertThatThrownBy(() -> new TargetUrlSchemeRule().validate(url(value)))
                .isInstanceOf(ValidationException.class)
                .extracting("code").isEqualTo("url.scheme_not_allowed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com/", "https://example.com/", "HTTPS://example.com/"})
    @DisplayName("accepts http and https in any case")
    void acceptsAllowedSchemes(String value) {
        assertThatCode(() -> new TargetUrlSchemeRule().validate(url(value)))
                .doesNotThrowAnyException();
    }

    // --- length -----------------------------------------------------------------

    @Test
    void rejectsOverLongUrl() {
        String tooLong = "https://example.com/" + "a".repeat(TargetUrlLengthRule.MAX_URL_LENGTH);

        assertThatThrownBy(() -> new TargetUrlLengthRule().validate(url(tooLong)))
                .isInstanceOf(ValidationException.class)
                .extracting("code").isEqualTo("url.too_long");
    }

    @Test
    @DisplayName("accepts a URL exactly at the limit")
    void acceptsUrlAtExactLimit() {
        String prefix = "https://example.com/";
        String atLimit = prefix + "a".repeat(TargetUrlLengthRule.MAX_URL_LENGTH - prefix.length());

        assertThatCode(() -> new TargetUrlLengthRule().validate(url(atLimit)))
                .doesNotThrowAnyException();
    }

    // --- expiry -----------------------------------------------------------------

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void rejectsExpiryInThePast() {
        var target = new ValidationTarget("https://example.com", null, NOW.minusSeconds(1));

        assertThatThrownBy(() -> new ExpiryRule(FIXED).validate(target))
                .isInstanceOf(ValidationException.class)
                .extracting("code").isEqualTo("expiry.in_past");
    }

    @Test
    @DisplayName("rejects an expiry exactly at now - it would be dead on arrival")
    void rejectsExpiryExactlyNow() {
        var target = new ValidationTarget("https://example.com", null, NOW);

        assertThatThrownBy(() -> new ExpiryRule(FIXED).validate(target))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void acceptsFutureExpiry() {
        var target = new ValidationTarget("https://example.com", null, NOW.plusSeconds(1));

        assertThatCode(() -> new ExpiryRule(FIXED).validate(target)).doesNotThrowAnyException();
    }

    @Test
    void acceptsAbsentExpiry() {
        var target = new ValidationTarget("https://example.com", null, null);

        assertThatCode(() -> new ExpiryRule(FIXED).validate(target)).doesNotThrowAnyException();
    }
}
