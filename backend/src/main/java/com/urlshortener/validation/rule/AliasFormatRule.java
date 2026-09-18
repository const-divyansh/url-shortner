package com.urlshortener.validation.rule;

import java.util.regex.Pattern;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.urlshortener.exception.ValidationException;
import com.urlshortener.validation.ValidationRule;
import com.urlshortener.validation.ValidationTarget;

/**
 * Constrains the shape of a caller-supplied alias.
 *
 * <p>An alias becomes a URL path segment, so characters such as {@code /}, {@code ?}
 * and {@code #} would change the meaning of the resulting link rather than being part
 * of it. The permitted set matches the {@code urls_short_code_charset} database
 * constraint - the database is the backstop, this rule turns the same rule into a
 * clear 400 instead of a persistence failure.
 */
@Component
@Order(RuleOrder.ALIAS_FORMAT)
public class AliasFormatRule implements ValidationRule {

    public static final int MIN_ALIAS_LENGTH = 3;

    /**
     * Matches the {@code VARCHAR(32)} column width.
     */
    public static final int MAX_ALIAS_LENGTH = 32;

    private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9_-]+$");

    @Override
    public void validate(ValidationTarget target) {
        if (!target.hasCustomAlias()) {
            return;
        }
        String alias = target.customAlias();
        if (alias.length() < MIN_ALIAS_LENGTH || alias.length() > MAX_ALIAS_LENGTH) {
            throw new ValidationException("alias.length",
                    "Custom alias must be between " + MIN_ALIAS_LENGTH + " and "
                            + MAX_ALIAS_LENGTH + " characters long.");
        }
        if (!ALLOWED.matcher(alias).matches()) {
            throw new ValidationException("alias.charset",
                    "Custom alias can use only letters, numbers, hyphens, and underscores.");
        }
    }
}
