package com.urlshortener.exception;

/**
 * The requested custom alias is already taken. Maps to HTTP 409.
 *
 * <p>Distinct from a generated-code collision: a generated code is retried silently,
 * but a caller who asked for a specific alias must be told, since quietly substituting
 * a different code would defeat the reason for requesting one.
 */
public class AliasUnavailableException extends RuntimeException {

    public AliasUnavailableException(String alias) {
        super("customAlias '" + alias + "' is already taken");
    }
}
