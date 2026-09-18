package com.urlshortener.exception;

/**
 * Rejects a caller-supplied value. Maps to HTTP 400.
 *
 * <p>Carries a stable {@code code} alongside the human-readable message so the error
 * contract does not depend on message wording - clients can branch on the code while
 * the message stays free to change.
 *
 * <p>Messages must describe what the caller did wrong without echoing internal detail
 * such as stack traces, SQL, or infrastructure hostnames.
 */
public class ValidationException extends RuntimeException {

    private final String code;

    public ValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
