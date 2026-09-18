package com.urlshortener.generator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Selects the active {@link ShortCodeGenerator} and allows switching it at runtime.
 *
 * <p><strong>Pattern: Factory Method.</strong> Callers ask for a strategy by name, or
 * for whichever is currently active, and receive the abstraction - never a concrete
 * type. Adding a strategy means adding a class annotated as a component: Spring
 * injects it into {@code generators} automatically and this factory requires no edit
 * (Open/Closed).
 *
 * <p><strong>Why switching is safe.</strong> The strategy governs creation only.
 * Resolution is a lookup by {@code short_code} and is indifferent to which algorithm
 * produced it, so codes minted under a previous strategy keep working and no migration
 * or dual-read path is needed.
 *
 * <p>Thread-safe: the active strategy is held in an {@link AtomicReference} because
 * request threads read it concurrently while an operator may replace it.
 */
@Component
public class ShortCodeGeneratorFactory {

    private static final Logger log = LoggerFactory.getLogger(ShortCodeGeneratorFactory.class);

    private final Map<String, ShortCodeGenerator> byName;
    private final AtomicReference<ShortCodeGenerator> active = new AtomicReference<>();

    public ShortCodeGeneratorFactory(List<ShortCodeGenerator> generators,
                                     @Value("${app.shortcode.strategy}") String initialStrategy) {
        if (generators.isEmpty()) {
            throw new IllegalStateException("No ShortCodeGenerator implementations found");
        }
        this.byName = index(generators);

        ShortCodeGenerator initial = byName.get(initialStrategy);
        if (initial == null) {
            // Fail fast rather than silently falling back: starting with a strategy
            // the operator did not ask for is worse than not starting at all.
            throw new IllegalStateException(
                    "Unknown app.shortcode.strategy '" + initialStrategy + "'. Available: " + available());
        }
        this.active.set(initial);
        log.info("Short-code strategies available={}, active='{}'", available(), initialStrategy);
    }

    /**
     * Builds the name index, rejecting duplicates at startup.
     *
     * <p>Two strategies sharing a name would make selection ambiguous and silently
     * shadow one implementation, so this is a startup failure rather than a
     * last-one-wins merge.
     */
    private static Map<String, ShortCodeGenerator> index(List<ShortCodeGenerator> generators) {
        Map<String, ShortCodeGenerator> index = new LinkedHashMap<>();
        for (ShortCodeGenerator generator : generators) {
            String name = generator.name();
            if (name == null || name.isBlank()) {
                throw new IllegalStateException(
                        generator.getClass().getName() + " returned a blank name()");
            }
            ShortCodeGenerator previous = index.put(name, generator);
            if (previous != null) {
                throw new IllegalStateException("Duplicate ShortCodeGenerator name '" + name + "': "
                        + previous.getClass().getName() + " and " + generator.getClass().getName());
            }
        }
        return Map.copyOf(index);
    }

    /**
     * The strategy currently used for new short codes.
     */
    public ShortCodeGenerator active() {
        return active.get();
    }

    /**
     * Names of every registered strategy.
     */
    public Set<String> available() {
        return byName.keySet();
    }

    /**
     * Looks up a strategy by name.
     *
     * @throws IllegalArgumentException if no strategy is registered under that name
     */
    public ShortCodeGenerator byName(String name) {
        ShortCodeGenerator generator = byName.get(name);
        if (generator == null) {
            throw new IllegalArgumentException(
                    "Unknown short-code strategy '" + name + "'. Available: " + available());
        }
        return generator;
    }

    /**
     * Switches the active strategy. Existing short codes are unaffected.
     *
     * @throws IllegalArgumentException if no strategy is registered under that name
     */
    public void activate(String name) {
        ShortCodeGenerator generator = byName(name);
        ShortCodeGenerator previous = active.getAndSet(generator);
        if (previous != generator) {
            log.info("Short-code strategy switched from '{}' to '{}'", previous.name(), name);
        }
    }
}
