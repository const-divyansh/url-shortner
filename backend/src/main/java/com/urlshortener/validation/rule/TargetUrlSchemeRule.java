package com.urlshortener.validation.rule;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.urlshortener.exception.ValidationException;
import com.urlshortener.validation.ValidationRule;
import com.urlshortener.validation.ValidationTarget;

/**
 * Restricts the scheme to an allow-list.
 *
 * <p>An allow-list, not a block-list: enumerating dangerous schemes is a losing game,
 * since anything missed is permitted by default. Only {@code http} and {@code https}
 * make sense for a redirect target, so everything else - {@code javascript:},
 * {@code data:}, {@code file:} - is refused without needing to be named.
 *
 * <p>{@code javascript:} matters most here: a redirect to one would execute in the
 * visitor's browser in the context of whatever page followed the link.
 */
@Component
@Order(RuleOrder.SCHEME)
public class TargetUrlSchemeRule implements ValidationRule {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    @Override
    public void validate(ValidationTarget target) {
        URI uri = TargetUrlSyntaxRule.parse(target.targetUrl());
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new ValidationException("url.scheme_not_allowed",
                    "Only http:// and https:// links are supported.");
        }
    }
}
