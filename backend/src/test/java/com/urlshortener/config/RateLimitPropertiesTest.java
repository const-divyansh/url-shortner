package com.urlshortener.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.urlshortener.config.RateLimitProperties.Budget;
import com.urlshortener.ratelimit.RateLimitScope;

/**
 * Proves the scope axis is genuinely Open/Closed: {@link RateLimitProperties} carries
 * no per-scope field or branch, so these tests exercise the map directly rather than a
 * named accessor per scope.
 */
class RateLimitPropertiesTest {

    @Test
    @DisplayName("returns the configured budget for a scope")
    void returnsConfiguredBudget() {
        Budget createBudget = new Budget(5, Duration.ofMinutes(1));
        RateLimitProperties properties = new RateLimitProperties(true, Map.of(RateLimitScope.CREATE, createBudget));

        assertThat(properties.budgetFor(RateLimitScope.CREATE)).isEqualTo(createBudget);
    }

    @Test
    @DisplayName("fails loudly, not silently, when a scope has no configured budget")
    void failsLoudlyWhenScopeIsUnconfigured() {
        // A rate limiter that quietly stops limiting is the failure mode worth
        // designing against - nothing reports it. A missing entry must be an explicit
        // startup-time error, not a null slipping through to charge nothing.
        RateLimitProperties properties = new RateLimitProperties(true, Map.of());

        assertThatThrownBy(() -> properties.budgetFor(RateLimitScope.REDIRECT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REDIRECT")
                .hasMessageContaining("app.ratelimit.budgets.redirect");
    }

    @Test
    @DisplayName("a null budgets map is treated as empty, not an NPE")
    void nullBudgetsMapIsTreatedAsEmpty() {
        RateLimitProperties properties = new RateLimitProperties(true, null);

        assertThatThrownBy(() -> properties.budgetFor(RateLimitScope.CREATE))
                .isInstanceOf(IllegalStateException.class);
    }
}
