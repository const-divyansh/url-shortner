package com.urlshortener.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShortCodeGeneratorFactoryTest {

    private final Base62RandomGenerator random = new Base62RandomGenerator();
    private final HashUrlGenerator hash = new HashUrlGenerator();

    private ShortCodeGeneratorFactory factoryWithDefault(String strategy) {
        return new ShortCodeGeneratorFactory(List.of(random, hash), strategy);
    }

    @Test
    @DisplayName("activates the configured strategy at startup")
    void activatesConfiguredStrategy() {
        assertThat(factoryWithDefault("hash-url").active()).isSameAs(hash);
        assertThat(factoryWithDefault("base62-random").active()).isSameAs(random);
    }

    @Test
    @DisplayName("registers every implementation Spring provides")
    void registersAllImplementations() {
        assertThat(factoryWithDefault("base62-random").available())
                .containsExactlyInAnyOrder("base62-random", "hash-url");
    }

    @Test
    @DisplayName("switches the active strategy at runtime")
    void switchesAtRuntime() {
        ShortCodeGeneratorFactory factory = factoryWithDefault("base62-random");

        factory.activate("hash-url");

        assertThat(factory.active()).isSameAs(hash);
    }

    @Test
    @DisplayName("fails fast on an unknown configured strategy instead of falling back")
    void unknownConfiguredStrategyFailsStartup() {
        // A silent fallback would start the application with a strategy nobody chose -
        // worse than refusing to start, since the mistake would surface only later as
        // unexpected code shapes.
        assertThatThrownBy(() -> factoryWithDefault("does-not-exist"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does-not-exist")
                .hasMessageContaining("base62-random");
    }

    @Test
    @DisplayName("rejects switching to an unknown strategy and keeps the current one")
    void unknownSwitchIsRejected() {
        ShortCodeGeneratorFactory factory = factoryWithDefault("base62-random");

        assertThatThrownBy(() -> factory.activate("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");
        assertThat(factory.active()).isSameAs(random);
    }

    @Test
    @DisplayName("rejects duplicate strategy names rather than silently shadowing one")
    void duplicateNamesFailStartup() {
        ShortCodeGenerator impostor = new ShortCodeGenerator() {
            @Override
            public String name() {
                return "base62-random";
            }

            @Override
            public String generate(GenerationContext context) {
                return "0000000";
            }
        };

        assertThatThrownBy(() -> new ShortCodeGeneratorFactory(List.of(random, impostor), "base62-random"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    @DisplayName("refuses to start with no strategies at all")
    void emptyRegistryFailsStartup() {
        assertThatThrownBy(() -> new ShortCodeGeneratorFactory(List.of(), "base62-random"))
                .isInstanceOf(IllegalStateException.class);
    }
}
