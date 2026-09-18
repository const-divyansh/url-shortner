package com.urlshortener.exception;

/**
 * Authenticated caller does not own the requested link.
 */
public class OwnershipForbiddenException extends RuntimeException {

    public OwnershipForbiddenException(String message) {
        super(message);
    }
}
