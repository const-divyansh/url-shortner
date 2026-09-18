package com.urlshortener.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps exceptions to HTTP responses in one place.
 *
 * <p>Centralised so the error contract is defined once rather than being re-implemented
 * per controller, which is how inconsistent shapes and accidental detail leaks arise.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationRequired(AuthenticationRequiredException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("auth.required", e.getMessage()));
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationFailed(AuthenticationFailedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("auth.invalid", e.getMessage()));
    }

    @ExceptionHandler(OwnershipForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleOwnershipForbidden(OwnershipForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("auth.forbidden", e.getMessage()));
    }

    @ExceptionHandler(GuestActionForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleGuestForbidden(GuestActionForbiddenException e) {
        // 403 with its own code, not auth.forbidden: the caller does own the resource,
        // so telling them it is not theirs would be false. The distinct code also lets
        // the client offer the actual remedy - sign in with Google.
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("auth.guest_forbidden", e.getMessage()));
    }

    @ExceptionHandler(AliasUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAliasTaken(AliasUnavailableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("alias.unavailable", e.getMessage()));
    }

    @ExceptionHandler(ShortCodeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ShortCodeNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("code.not_found", e.getMessage()));
    }

    @ExceptionHandler(ShortCodeExpiredException.class)
    public ResponseEntity<ErrorResponse> handleExpired(ShortCodeExpiredException e) {
        // 410, not 404: the link existed and is permanently finished. A 404 would
        // wrongly suggest it never existed.
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ErrorResponse.of("code.expired", e.getMessage()));
    }

    @ExceptionHandler(ShortCodeDeletedException.class)
    public ResponseEntity<ErrorResponse> handleDeleted(ShortCodeDeletedException e) {
        // Also 410: same "existed, now permanently finished" contract as expiry, just
        // a different cause - kept as a distinct exception/code so logs and API
        // consumers can tell the two apart.
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ErrorResponse.of("code.deleted", e.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimited(RateLimitExceededException e) {
        // Retry-After is the whole point of answering 429 rather than dropping the
        // request: it converts "you failed" into "come back at this time", which is
        // what stops a throttled client from retrying straight back into the limit.
        // Logged at debug, not warn - being rate limited is the feature working, and
        // a flood would otherwise fill the log with the very traffic being rejected.
        log.debug("Rate limit exceeded", e);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfter().toSeconds()))
                .body(ErrorResponse.of("rate_limit.exceeded", e.getMessage()));
    }

    /**
     * Unparseable body, or a field of the wrong type - for example a malformed
     * {@code expiresAt}.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        // The exception text can quote the offending payload and internal type names,
        // so a fixed message is returned instead of echoing it back.
        log.debug("Unreadable request body", e);
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("request.malformed",
                        "Request body is malformed or contains an invalid field value"));
    }

    @ExceptionHandler(CodeGenerationException.class)
    public ResponseEntity<ErrorResponse> handleGenerationFailure(CodeGenerationException e) {
        // Genuinely unexpected - logged at error level because it normally indicates a
        // generator defect rather than bad input.
        log.error("Short-code generation failed", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("code.generation_failed",
                        "Could not allocate a short code, please retry"));
    }

    /**
     * A request for a static resource that does not exist - most commonly a
     * browser's unconditional {@code /favicon.ico} request. Routine, not a
     * defect, so it is answered with a plain 404 and no stack trace, rather than
     * falling into the catch-all below and being reported as a 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoStaticResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("resource.not_found", "No such resource"));
    }

    /**
     * Catch-all, so an unanticipated failure still returns the documented error shape
     * rather than a servlet-container HTML page.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("internal_error", "An unexpected error occurred"));
    }
}
