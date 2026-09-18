package com.urlshortener.exception;

/**
 * The short code existed but its owner deleted it. Maps to HTTP 410 Gone.
 *
 * <p>Deliberately distinct from {@link ShortCodeExpiredException}, even though both
 * map to the same status code: they are different facts about the link (a fixed
 * expiry it was created with, versus a deliberate action its owner took later), worth
 * keeping separate in logs and the error body's {@code code} field, mirroring the
 * existing precedent of {@link ShortCodeNotFoundException} versus
 * {@link ShortCodeExpiredException} both existing despite similar handling.
 */
public class ShortCodeDeletedException extends RuntimeException {

    public ShortCodeDeletedException(String shortCode) {
        super("Short code '" + shortCode + "' has been deleted");
    }
}
