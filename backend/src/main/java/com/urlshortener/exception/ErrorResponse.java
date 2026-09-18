package com.urlshortener.exception;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The single error shape returned by every failing request.
 *
 * @param code      stable machine-readable identifier; clients branch on this rather
 *                  than on message wording
 * @param message   human-readable explanation, safe to show a caller
 * @param timestamp when the error was produced
 */
public record ErrorResponse(
        @Schema(description = "Stable machine-readable error code.", example = "alias.unavailable")
        String code,
        @Schema(description = "Human-readable error message.", example = "customAlias 'spring-sale' is already taken")
        String message,
        @Schema(description = "UTC time when the error was produced.", type = "string", format = "date-time")
        Instant timestamp) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Instant.now());
    }
}
