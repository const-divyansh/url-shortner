package com.urlshortener.validation;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Runs the validation rules in order, stopping at the first objection.
 *
 * <p>Pattern: Chain of Responsibility. Spring injects every {@link ValidationRule}
 * bean, ordered by {@code @Order}, so introducing a rule requires no change here
 * (Open/Closed).
 *
 * <p>Fails fast rather than accumulating every problem: later rules assume earlier ones
 * passed - the scheme and host rules parse a URL the syntax rule has already accepted -
 * so continuing after a failure would mean reporting errors derived from input already
 * known to be invalid.
 */
@Component
public class UrlValidationChain {

    private final List<ValidationRule> rules;

    public UrlValidationChain(List<ValidationRule> rules) {
        if (rules.isEmpty()) {
            // An empty chain would silently accept everything, including the inputs
            // these rules exist to block.
            throw new IllegalStateException("No ValidationRule beans found");
        }
        this.rules = List.copyOf(rules);
    }

    /**
     * Validates the request, throwing
     * {@link com.urlshortener.exception.ValidationException} on the first rule that
     * objects.
     */
    public void validate(ValidationTarget target) {
        for (ValidationRule rule : rules) {
            rule.validate(target);
        }
    }

    /**
     * Number of active rules. Used by tests to assert the chain is fully wired.
     */
    public int size() {
        return rules.size();
    }
}
