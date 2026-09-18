package com.urlshortener.exception;

/**
 * The short code existed but has expired. Maps to HTTP 410 Gone.
 *
 * <p>Deliberately distinct from {@link ShortCodeNotFoundException}: 410 tells the
 * caller the link was real and is now permanently finished, which 404 would not.
 * Preserving that distinction is why resolution fetches the row and inspects it rather
 * than filtering expiry in SQL, where both cases would collapse into an empty result.
 */
public class ShortCodeExpiredException extends RuntimeException {

    public ShortCodeExpiredException(String shortCode) {
        super("Short code '" + shortCode + "' has expired");
    }
}
