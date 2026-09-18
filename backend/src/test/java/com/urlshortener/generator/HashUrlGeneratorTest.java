package com.urlshortener.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.urlshortener.util.Base62;

class HashUrlGeneratorTest {

    private final HashUrlGenerator generator = new HashUrlGenerator();

    @Test
    @DisplayName("produces a code of the contracted length using only Base62 characters")
    void honoursCodeContract() {
        String code = generator.generate(GenerationContext.firstAttempt("https://example.com"));

        assertThat(code).hasSize(ShortCodeGenerator.CODE_LENGTH);
        assertThat(Base62.isBase62(code)).isTrue();
    }

    @Test
    @DisplayName("is deterministic for the same URL and attempt")
    void deterministicForSameInput() {
        GenerationContext context = GenerationContext.firstAttempt("https://example.com/page");

        assertThat(generator.generate(context)).isEqualTo(generator.generate(context));
    }

    @Test
    @DisplayName("REGRESSION: varies by attempt, so a duplicate URL cannot exhaust the retry budget")
    void variesByAttempt() {
        // The reason GenerationContext carries `attempt`. This project does not
        // deduplicate, so resubmitting a URL must yield a second, independent code.
        // Without the attempt salt every retry would recompute the identical digest,
        // collide forever, and turn a duplicate submission into a 500.
        String url = "https://example.com/page";

        Set<String> codes = new HashSet<>();
        for (int attempt = 0; attempt < 5; attempt++) {
            codes.add(generator.generate(new GenerationContext(url, attempt)));
        }

        assertThat(codes).hasSize(5);
    }

    @Test
    @DisplayName("different URLs produce different codes")
    void differentUrlsDiffer() {
        String first = generator.generate(GenerationContext.firstAttempt("https://example.com/a"));
        String second = generator.generate(GenerationContext.firstAttempt("https://example.com/b"));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void exposesItsName() {
        assertThat(generator.name()).isEqualTo("hash-url");
    }
}
