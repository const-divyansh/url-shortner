package com.urlshortener.exception;

/**
 * Present but invalid credential.
 */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
