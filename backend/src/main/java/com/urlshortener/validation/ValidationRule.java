package com.urlshortener.validation;

/**
 * One link in the request-validation chain.
 *
 * <p><strong>Pattern: Chain of Responsibility.</strong> Each rule owns exactly one
 * concern and may reject the request by throwing
 * {@link com.urlshortener.exception.ValidationException}. Adding or reordering a rule
 * means adding a class rather than extending a single growing validator method
 * (Single Responsibility, Open/Closed).
 *
 * <p>Implementations must be stateless and thread-safe: one instance serves all
 * requests.
 *
 * <p>Rules that do not apply to a given request - alias rules when no alias was
 * supplied, for example - return without doing anything rather than rejecting.
 */
public interface ValidationRule {

    /**
     * Inspects the request, throwing {@link com.urlshortener.exception.ValidationException}
     * if it is unacceptable. Returning normally means this rule raises no objection;
     * it does not mean the request is valid overall.
     */
    void validate(ValidationTarget target);
}
