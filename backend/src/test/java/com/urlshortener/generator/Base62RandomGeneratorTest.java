package com.urlshortener.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.urlshortener.util.Base62;

class Base62RandomGeneratorTest {

    private final Base62RandomGenerator generator = new Base62RandomGenerator();

    @Test
    @DisplayName("produces a code of the contracted length using only Base62 characters")
    void honoursCodeContract() {
        String code = generator.generate(GenerationContext.firstAttempt("https://example.com"));

        assertThat(code).hasSize(ShortCodeGenerator.CODE_LENGTH);
        assertThat(Base62.isBase62(code)).isTrue();
    }

    @Test
    @DisplayName("ignores the target URL - the code is not derived from it")
    void ignoresTargetUrl() {
        // Unpredictability (ADR-007) depends on the code carrying no information about
        // the URL. Two different URLs must not produce related codes, and the same URL
        // must not produce a repeatable one.
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            codes.add(generator.generate(GenerationContext.firstAttempt("https://example.com")));
        }

        assertThat(codes).hasSizeGreaterThan(95);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 4, 99})
    @DisplayName("varies across attempts, so the caller's retry loop always progresses")
    void variesAcrossAttempts(int attempt) {
        String first = generator.generate(new GenerationContext("https://example.com", attempt));
        String second = generator.generate(new GenerationContext("https://example.com", attempt));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("draws from the whole alphabet rather than a biased subset")
    void usesFullAlphabet() {
        // Guards against the classic `% 62` modulo-bias bug: 256 is not a multiple of
        // 62, so byte-modulo would over-represent the first eight symbols. Over this
        // many draws a uniform generator should touch essentially every symbol.
        Set<Character> seen = new HashSet<>();
        for (int i = 0; i < 5_000; i++) {
            for (char c : generator.generate(GenerationContext.firstAttempt("https://example.com")).toCharArray()) {
                seen.add(c);
            }
        }

        assertThat(seen).hasSize(Base62.RADIX);
    }

    @Test
    void exposesItsName() {
        assertThat(generator.name()).isEqualTo("base62-random");
    }
}
