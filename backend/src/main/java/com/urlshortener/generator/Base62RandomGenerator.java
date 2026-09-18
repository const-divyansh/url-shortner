package com.urlshortener.generator;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

import com.urlshortener.util.Base62;

/**
 * Default strategy: a cryptographically random Base62 code, independent of the URL.
 *
 * <p>Chosen as the default per ADR-007 because codes are unguessable and
 * unenumerable. With roughly 3.5 trillion possible codes, the caller's retry loop is
 * a correctness guarantee rather than a routine path - at 100 million stored URLs the
 * chance a single attempt collides is about 1 in 35,000.
 *
 * <p>Ignores both {@link GenerationContext} fields: output depends on nothing but the
 * random source, so successive attempts naturally differ and the retry contract holds
 * for free.
 *
 * <p>Thread-safe: {@link SecureRandom} is safe for concurrent use.
 */
@Component
public class Base62RandomGenerator implements ShortCodeGenerator {

    public static final String NAME = "base62-random";

    /**
     * Deliberately {@link SecureRandom}, not {@code java.util.Random}.
     *
     * <p>{@code Random} is a 48-bit linear congruential generator: observing a
     * handful of outputs is enough to recover its seed and predict every subsequent
     * value. Since a short code is the only thing guarding a link, a predictable
     * generator would let an attacker derive links instead of guessing them.
     */
    private final SecureRandom random = new SecureRandom();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String generate(GenerationContext context) {
        char[] out = new char[CODE_LENGTH];
        for (int i = 0; i < CODE_LENGTH; i++) {
            // nextInt(bound) performs rejection sampling internally and is uniform.
            // A naive `randomByte % 62` would be biased: 256 = 4*62 + 8, so the first
            // eight symbols would appear measurably more often, shrinking the
            // effective keyspace.
            out[i] = Base62.ALPHABET[random.nextInt(Base62.RADIX)];
        }
        return new String(out);
    }
}
