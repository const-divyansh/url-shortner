package com.urlshortener.exception;

/**
 * A unique short code could not be produced within the retry budget. Maps to HTTP 500.
 *
 * <p>Should be unreachable in practice - with roughly 3.5 trillion codes, consecutive
 * collisions are vanishingly unlikely. Reaching this almost always means a generator is
 * violating the vary-by-attempt contract and returning the same code every time, so it
 * is reported loudly rather than retried forever.
 */
public class CodeGenerationException extends RuntimeException {

    public CodeGenerationException(String strategy, int attempts) {
        super("Strategy '" + strategy + "' failed to produce a free code in " + attempts + " attempts");
    }
}
