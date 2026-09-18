package com.urlshortener.validation.rule;

import java.util.Locale;
import java.util.Set;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.urlshortener.exception.ValidationException;
import com.urlshortener.validation.ValidationRule;
import com.urlshortener.validation.ValidationTarget;

/**
 * Refuses aliases that would shadow a real route.
 *
 * <p>Redirects are served from the root ({@code GET /{shortCode}}), so an alias sits in
 * the same namespace as the application's own paths. Without this rule a caller could
 * register {@code api} or {@code actuator} and capture requests intended for them.
 *
 * <p><strong>Maintenance note.</strong> This list must grow whenever a new root-level
 * route is introduced. The mapping is checked in
 * {@code ReservedAliasRuleTest#coversEveryRootRoute}, so an unlisted route fails the
 * build rather than silently becoming claimable.
 */
@Component
@Order(RuleOrder.RESERVED_ALIAS)
public class ReservedAliasRule implements ValidationRule {

    public static final Set<String> RESERVED = Set.of(
            "api",
            "actuator",
            "health",
            "metrics",
            "admin",
            "login",
            "logout",
            "static",
            "assets",
            "favicon",
            "robots");

    @Override
    public void validate(ValidationTarget target) {
        if (!target.hasCustomAlias()) {
            return;
        }
        if (RESERVED.contains(target.customAlias().toLowerCase(Locale.ROOT))) {
            throw new ValidationException("alias.reserved",
                    "That custom alias is reserved. Try a different one.");
        }
    }
}
