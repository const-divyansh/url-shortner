package com.urlshortener.exception;

import java.time.Instant;

/**
 * The single error shape returned by every failing request.
 *
 * @param code      stable machine-readable identifier; clients branch on this rather
 *                  than on message wording
 * @param message   human-readable explanation, safe to show a caller
 * @param timestamp when the error was produced
 */
public record ErrorResponse(String code, String message, Instant timestamp) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Instant.now());
    }
}
