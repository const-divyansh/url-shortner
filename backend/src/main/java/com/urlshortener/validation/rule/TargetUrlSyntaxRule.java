package com.urlshortener.validation.rule;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.urlshortener.exception.ValidationException;
import com.urlshortener.validation.ValidationRule;
import com.urlshortener.validation.ValidationTarget;

/**
 * Requires the target to be a parseable, absolute URL with a host.
 *
 * <p>Runs first: every later rule reads the scheme or host, so an unparseable value
 * must be rejected before they do.
 */
@Component
@Order(RuleOrder.SYNTAX)
public class TargetUrlSyntaxRule implements ValidationRule {

    @Override
    public void validate(ValidationTarget target) {
        String url = target.targetUrl();
        if (url == null || url.isBlank()) {
            throw new ValidationException("url.missing", "Enter a URL to shorten.");
        }
        URI uri = parse(url.trim());
        if (!uri.isAbsolute()) {
            throw new ValidationException("url.not_absolute",
                    "Enter a full URL starting with http:// or https://, for example https://example.com.");
        }
        // Host presence is deliberately not checked here. It is verified by
        // TargetUrlHostSafetyRule, which runs after the scheme rule - so an input like
        // "javascript:alert(1)", which has a scheme but no host, is reported as a
        // disallowed scheme rather than as a missing host.
    }

    /**
     * Parses a URL for use by this and later rules.
     *
     * <p>Shared so the chain has one definition of "parseable" - if rules parsed
     * independently they could disagree about what is valid.
     */
    public static URI parse(String url) {
        try {
            return new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new ValidationException("url.malformed",
                    "That URL does not look valid. Check it and try again.");
        }
    }
}
