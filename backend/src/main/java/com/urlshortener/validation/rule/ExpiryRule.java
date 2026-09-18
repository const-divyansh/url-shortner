package com.urlshortener.validation.rule;

import java.time.Clock;
import java.time.Instant;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.urlshortener.exception.ValidationException;
import com.urlshortener.validation.ValidationRule;
import com.urlshortener.validation.ValidationTarget;

/**
 * Rejects an expiry that has already passed.
 *
 * <p>Creating an already-dead link is almost certainly a mistake - a timezone error or
 * a stale value - and silently accepting it would produce a link that returns 410 on
 * its very first use.
 *
 * <p>Takes a {@link Clock} rather than calling {@link Instant#now()} so that tests can
 * pin "now" instead of relying on wall-clock timing near the boundary.
 */
@Component
@Order(RuleOrder.EXPIRY)
public class ExpiryRule implements ValidationRule {

    private final Clock clock;

    public ExpiryRule(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void validate(ValidationTarget target) {
        if (!target.hasExpiry()) {
            return;
        }
        if (!target.expiresAt().isAfter(clock.instant())) {
            throw new ValidationException("expiry.in_past",
                    "Expiry time must be in the future.");
        }
    }
}
