package com.urlshortener.exception;

/**
 * No URL exists for the requested short code. Maps to HTTP 404.
 */
public class ShortCodeNotFoundException extends RuntimeException {

    public ShortCodeNotFoundException(String shortCode) {
        super("No URL found for code '" + shortCode + "'");
    }
}
