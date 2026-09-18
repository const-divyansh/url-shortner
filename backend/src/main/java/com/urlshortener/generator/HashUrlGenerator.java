package com.urlshortener.generator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.stereotype.Component;

import com.urlshortener.util.Base62;

/**
 * Deterministic strategy: the code is derived from the target URL by hashing.
 *
 * <p>This is the "hash + collision resolution" approach from the ByteByteGo
 * reference. It is offered as an alternative, not the default - a code derived from
 * the URL leaks nothing by itself, but identical URLs map to identical codes, which
 * is precisely why ADR-007 prefers the random strategy.
 *
 * <p><strong>Why the attempt number is mixed into the digest.</strong> This project
 * deliberately does not deduplicate: submitting the same URL twice must yield two
 * independent codes. A pure {@code hash(url)} cannot satisfy that - the second
 * submission would produce the identical code, collide, and then produce the identical
 * code again on every retry until the budget was exhausted, turning a duplicate URL
 * into a 500. Salting with {@code attempt} makes each retry a different point in the
 * output space, so the loop makes progress.
 *
 * <p>Consequence worth knowing operationally: with this strategy a repeat URL collides
 * on attempt 0 <em>by construction</em>. Retries are an expected path here, unlike the
 * random strategy where they are near-unreachable.
 *
 * <p>Thread-safe: a fresh {@link MessageDigest} is created per call, since digest
 * instances are stateful and not safe to share.
 */
@Component
public class HashUrlGenerator implements ShortCodeGenerator {

    public static final String NAME = "hash-url";

    private static final String ALGORITHM = "SHA-256";

    /**
     * Number of leading digest bytes folded into the value that gets encoded.
     * Seven bytes (56 bits) stays clear of {@code Long}'s sign bit, so no masking is
     * needed and the value is always non-negative.
     */
    private static final int DIGEST_BYTES_USED = 7;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String generate(GenerationContext context) {
        byte[] digest = digest(context.targetUrl() + ":" + context.attempt());
        long value = 0;
        for (int i = 0; i < DIGEST_BYTES_USED; i++) {
            value = (value << 8) | (digest[i] & 0xFFL);
        }
        return Base62.encodeFixedLength(value, CODE_LENGTH);
    }

    private static byte[] digest(String input) {
        try {
            return MessageDigest.getInstance(ALGORITHM)
                    .digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java platform; absence means a broken JRE.
            throw new IllegalStateException(ALGORITHM + " not available", e);
        }
    }
}
