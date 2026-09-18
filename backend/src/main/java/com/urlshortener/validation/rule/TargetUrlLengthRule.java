package com.urlshortener.validation.rule;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.urlshortener.exception.ValidationException;
import com.urlshortener.validation.ValidationRule;
import com.urlshortener.validation.ValidationTarget;

/**
 * Caps the stored URL length.
 *
 * <p>Bounds abuse - without a limit a caller could post megabytes - and keeps values
 * inside the {@code VARCHAR(2048)} column, so an over-long URL is a 400 rather than a
 * database error surfacing as a 500.
 */
@Component
@Order(RuleOrder.LENGTH)
public class TargetUrlLengthRule implements ValidationRule {

    /**
     * 2048 is the pragmatic interoperability ceiling: many servers and proxies refuse
     * more, so a longer link would break in the wild regardless of what we store.
     */
    public static final int MAX_URL_LENGTH = 2048;

    @Override
    public void validate(ValidationTarget target) {
        String url = target.targetUrl();
        if (url != null && url.trim().length() > MAX_URL_LENGTH) {
            throw new ValidationException("url.too_long",
                    "URL is too long. Keep it under " + MAX_URL_LENGTH + " characters.");
        }
    }
}
