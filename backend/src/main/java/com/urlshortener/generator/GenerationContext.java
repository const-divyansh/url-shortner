package com.urlshortener.generator;

/**
 * Inputs available to a {@link ShortCodeGenerator} for one generation attempt.
 *
 * <p>Exists because deterministic strategies need context that random ones do not.
 * {@code hash-url} derives its code from {@link #targetUrl()} and must vary by
 * {@link #attempt()}; without the attempt number a deterministic strategy would
 * return the identical code on every retry and exhaust the retry budget on any
 * duplicate URL.
 *
 * <p>Random strategies ignore both fields. That asymmetry is intentional: the
 * contract carries what the most demanding implementation needs, so adding a
 * strategy never forces a signature change on the others (Open/Closed).
 *
 * @param targetUrl the URL being shortened, already validated and normalised
 * @param attempt   zero-based retry counter; increments when a generated code collides
 */
public record GenerationContext(String targetUrl, int attempt) {

    public GenerationContext {
        if (targetUrl == null || targetUrl.isBlank()) {
            throw new IllegalArgumentException("TargetUrl must not be blank");
        }
        if (attempt < 0) {
            throw new IllegalArgumentException("Attempt must be non-negative, got " + attempt);
        }
    }

    /**
     * Convenience factory for a first attempt.
     */
    public static GenerationContext firstAttempt(String targetUrl) {
        return new GenerationContext(targetUrl, 0);
    }

    /**
     * Returns a context for the next retry, leaving this instance unchanged.
     */
    public GenerationContext nextAttempt() {
        return new GenerationContext(targetUrl, attempt + 1);
    }
}
