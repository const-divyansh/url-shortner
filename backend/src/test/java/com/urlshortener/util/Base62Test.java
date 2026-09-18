package com.urlshortener.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class Base62Test {

    @Test
    void alphabetHasSixtyTwoSymbols() {
        assertThat(Base62.ALPHABET).hasSize(62);
        assertThat(Base62.RADIX).isEqualTo(62);
    }

    @ParameterizedTest
    @CsvSource({"0, 0000000", "1, 0000001", "61, 000000Z", "62, 0000010"})
    @DisplayName("encodes values with the expected Base62 digits")
    void encodesKnownValues(long value, String expected) {
        assertThat(Base62.encodeFixedLength(value, 7)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 61, 62, 3_521_614_606_207L, Long.MAX_VALUE})
    @DisplayName("always returns exactly the requested width")
    void padsToFixedWidth(long value) {
        assertThat(Base62.encodeFixedLength(value, 7)).hasSize(7);
    }

    @Test
    @DisplayName("rejects negative values rather than emitting a malformed code")
    void rejectsNegativeValues() {
        assertThatThrownBy(() -> Base62.encodeFixedLength(-1, 7))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveLength() {
        assertThatThrownBy(() -> Base62.encodeFixedLength(1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "ABC", "123", "k7Fq2Xa"})
    void acceptsBase62Strings(String candidate) {
        assertThat(Base62.isBase62(candidate)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc-def", "a/b", "a b", "héllo", "a.b"})
    @DisplayName("rejects characters that would be unsafe or ambiguous in a URL path")
    void rejectsNonBase62Strings(String candidate) {
        assertThat(Base62.isBase62(candidate)).isFalse();
    }

    @Test
    void rejectsNullAndEmpty() {
        assertThat(Base62.isBase62(null)).isFalse();
        assertThat(Base62.isBase62("")).isFalse();
    }
}
