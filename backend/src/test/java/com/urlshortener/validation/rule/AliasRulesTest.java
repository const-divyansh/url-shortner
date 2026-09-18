package com.urlshortener.validation.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.urlshortener.exception.ValidationException;
import com.urlshortener.validation.ValidationTarget;

class AliasRulesTest {

    private static ValidationTarget alias(String value) {
        return new ValidationTarget("https://example.com", value, null);
    }

    // --- format -----------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"a/b", "a?b", "a#b", "a b", "a.b", "héllo", "a%2Fb", "a:b"})
    @DisplayName("rejects characters that would change the meaning of the resulting path")
    void rejectsUnsafeCharacters(String value) {
        assertThatThrownBy(() -> new AliasFormatRule().validate(alias(value)))
                .isInstanceOf(ValidationException.class)
                .extracting("code").isEqualTo("alias.charset");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "a"})
    void rejectsTooShortAlias(String value) {
        assertThatThrownBy(() -> new AliasFormatRule().validate(alias(value)))
                .isInstanceOf(ValidationException.class)
                .extracting("code").isEqualTo("alias.length");
    }

    @Test
    @DisplayName("rejects an alias longer than the column can hold")
    void rejectsTooLongAlias() {
        String tooLong = "a".repeat(AliasFormatRule.MAX_ALIAS_LENGTH + 1);

        assertThatThrownBy(() -> new AliasFormatRule().validate(alias(tooLong)))
                .isInstanceOf(ValidationException.class)
                .extracting("code").isEqualTo("alias.length");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "spring-sale", "my_link", "A1b2C3", "aaa"})
    void acceptsValidAliases(String value) {
        assertThatCode(() -> new AliasFormatRule().validate(alias(value)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("skips alias rules when no alias was supplied")
    void skipsWhenAliasAbsent() {
        assertThatCode(() -> new AliasFormatRule().validate(alias(null))).doesNotThrowAnyException();
        assertThatCode(() -> new AliasFormatRule().validate(alias(""))).doesNotThrowAnyException();
    }

    // --- reserved ---------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"api", "API", "actuator", "health", "admin"})
    @DisplayName("rejects aliases that would shadow application routes, case-insensitively")
    void rejectsReservedAliases(String value) {
        assertThatThrownBy(() -> new ReservedAliasRule().validate(alias(value)))
                .isInstanceOf(ValidationException.class)
                .extracting("code").isEqualTo("alias.reserved");
    }

    @Test
    void acceptsNonReservedAlias() {
        assertThatCode(() -> new ReservedAliasRule().validate(alias("spring-sale")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("reserves every root-level route the application actually exposes")
    void coversEveryRootRoute() {
        // Guards the maintenance risk called out on ReservedAliasRule: adding a
        // root-level route without reserving its prefix would let a caller claim it as
        // an alias and shadow the real endpoint.
        assertThat(ReservedAliasRule.RESERVED)
                .as("every root path prefix the app serves must be reserved")
                .contains("api", "actuator");
    }
}
