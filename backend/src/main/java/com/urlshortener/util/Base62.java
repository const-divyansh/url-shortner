package com.urlshortener.util;

/**
 * Base62 alphabet and fixed-length encoding shared by short-code strategies.
 *
 * <p>Single responsibility: character-set mechanics only. It knows nothing about
 * short codes, collisions, or persistence, so strategies can reuse it without
 * inheriting each other's concerns.
 */
public final class Base62 {

    /**
     * Digits, then lowercase, then uppercase - 62 symbols, as specified by ADR-007.
     */
    public static final char[] ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    public static final int RADIX = ALPHABET.length;

    private Base62() {
    }

    /**
     * Encodes {@code value} into exactly {@code length} Base62 characters.
     *
     * <p>Fixed width on purpose: short codes are a uniform-length namespace, so a
     * small value is left-padded with the zero symbol rather than producing a
     * shorter code.
     *
     * <p>Values of {@code RADIX^length} or greater are reduced modulo that bound rather
     * than rejected. This is relied upon, not merely tolerated: {@code HashUrlGenerator}
     * feeds a 56-bit digest slice into 7 characters (about 41.7 bits of space), so
     * reduction is the intended behaviour and preserves the width guarantee. Callers
     * needing a one-to-one mapping must size {@code length} accordingly.
     *
     * @throws IllegalArgumentException if {@code value} is negative or {@code length} is not positive
     */
    public static String encodeFixedLength(long value, int length) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative, got " + value);
        }
        if (length <= 0) {
            throw new IllegalArgumentException("length must be positive, got " + length);
        }
        char[] out = new char[length];
        long remaining = value;
        for (int i = length - 1; i >= 0; i--) {
            out[i] = ALPHABET[(int) Math.floorMod(remaining, RADIX)];
            remaining /= RADIX;
        }
        return new String(out);
    }

    /**
     * True if every character of {@code candidate} belongs to the Base62 alphabet.
     * Used by tests and validation to assert the charset contract.
     */
    public static boolean isBase62(String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean lower = c >= 'a' && c <= 'z';
            boolean upper = c >= 'A' && c <= 'Z';
            if (!digit && !lower && !upper) {
                return false;
            }
        }
        return true;
    }
}
