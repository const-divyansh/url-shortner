package com.urlshortener.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.urlshortener.config.ClockConfig;
import com.urlshortener.exception.ValidationException;
import com.urlshortener.validation.rule.AliasFormatRule;
import com.urlshortener.validation.rule.ExpiryRule;
import com.urlshortener.validation.rule.ReservedAliasRule;
import com.urlshortener.validation.rule.TargetUrlHostSafetyRule;
import com.urlshortener.validation.rule.TargetUrlLengthRule;
import com.urlshortener.validation.rule.TargetUrlSchemeRule;
import com.urlshortener.validation.rule.TargetUrlSyntaxRule;

/**
 * Verifies the chain as Spring assembles it - that every rule is registered and that
 * {@code @Order} genuinely sequences them.
 *
 * <p>Uses a sliced context rather than {@code @SpringBootTest} so the test needs no
 * database or Redis, and substitutes a stub {@link HostResolver} so it needs no
 * network either.
 */
@SpringJUnitConfig(classes = {
        UrlValidationChain.class,
        ClockConfig.class,
        UrlValidationChainTest.StubResolverConfig.class,
        TargetUrlSyntaxRule.class,
        TargetUrlLengthRule.class,
        TargetUrlSchemeRule.class,
        AliasFormatRule.class,
        ReservedAliasRule.class,
        ExpiryRule.class,
        TargetUrlHostSafetyRule.class
})
class UrlValidationChainTest {

    @Configuration
    static class StubResolverConfig {
        /**
         * Stands in for {@link DnsHostResolver}. Without it these tests would perform
         * real lookups, making them slow, offline-hostile, and dependent on DNS records
         * outside this repository.
         */
        @Bean
        HostResolver hostResolver() {
            return host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")};
        }
    }

    @Autowired
    private UrlValidationChain chain;

    @Test
    @DisplayName("registers every rule bean")
    void registersAllRules() {
        assertThat(chain.size()).isEqualTo(7);
    }

    @Test
    @DisplayName("runs the syntax rule before rules that parse the URL")
    void syntaxRuleRunsFirst() {
        // Ordering is correctness-critical: the scheme and host rules parse the URL, so
        // if one ran first this unparseable input would surface as its error - or an
        // unhandled parse failure - instead of a clear syntax rejection.
        assertThatThrownBy(() -> chain.validate(new ValidationTarget("not a url", null, null)))
                .isInstanceOf(ValidationException.class)
                .extracting("code")
                .isEqualTo("url.malformed");
    }

    @Test
    @DisplayName("reports the cheap alias rejection without performing DNS")
    void cheapRulesPrecedeHostResolution() {
        // The host rule is the only one doing I/O and is ordered last. A bad alias must
        // be reported without a lookup ever being attempted.
        assertThatThrownBy(() -> chain.validate(
                new ValidationTarget("https://definitely-not-a-real-domain.invalid", "a/b", null)))
                .isInstanceOf(ValidationException.class)
                .extracting("code")
                .isEqualTo("alias.charset");
    }

    @Test
    @DisplayName("diagnoses a dangerous scheme as such, not as a missing host")
    void schemeIsDiagnosedBeforeHost() {
        // "javascript:alert(1)" has a scheme but no authority. Checking host presence
        // in the syntax rule would report "must include a host", which misdiagnoses the
        // actual problem; host presence is therefore checked after the scheme rule.
        assertThatThrownBy(() -> chain.validate(
                new ValidationTarget("javascript:alert(1)", null, null)))
                .isInstanceOf(ValidationException.class)
                .extracting("code")
                .isEqualTo("url.scheme_not_allowed");
    }

    @Test
    @DisplayName("accepts a well-formed request end to end")
    void acceptsValidRequest() {
        var target = new ValidationTarget(
                "https://example.com/page", "spring-sale", Instant.now().plus(1, ChronoUnit.DAYS));

        assertThatCode(() -> chain.validate(target)).doesNotThrowAnyException();
    }
}
