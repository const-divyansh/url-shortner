package com.urlshortener.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.urlshortener.config.RateLimitProperties;
import com.urlshortener.config.RateLimitProperties.Budget;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisRateLimiterTest {

    private static final String KEY = "hashed-client-key";

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisRateLimiter limiter;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);

        limiter = new RedisRateLimiter(redis, new RateLimitProperties(
                true,
                Map.of(
                        RateLimitScope.CREATE, new Budget(3, Duration.ofMinutes(1)),
                        RateLimitScope.REDIRECT, new Budget(10, Duration.ofMinutes(1)))));
    }

    private void countReturns(long value) {
        when(valueOps.increment(anyString())).thenReturn(value);
    }

    @Test
    @DisplayName("allows a request inside the budget")
    void allowsWithinBudget() {
        countReturns(1L);

        assertThat(limiter.check(RateLimitScope.CREATE, KEY).allowed()).isTrue();
    }

    @Test
    @DisplayName("allows the request that exactly reaches the limit")
    void allowsExactlyAtLimit() {
        // Off-by-one guard: the limit is the number of permitted requests, so the third
        // of three must still succeed. Rejecting here would quietly enforce limit-1.
        countReturns(3L);

        assertThat(limiter.check(RateLimitScope.CREATE, KEY).allowed()).isTrue();
    }

    @Test
    @DisplayName("rejects the first request past the limit")
    void rejectsPastLimit() {
        countReturns(4L);

        RateLimitDecision decision = limiter.check(RateLimitScope.CREATE, KEY);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfter()).isPositive();
    }

    @Test
    @DisplayName("scopes have independent budgets")
    void scopesDoNotShareABudget() {
        // 4 exceeds CREATE's limit of 3 but is well inside REDIRECT's 10. A shared
        // counter would make exhausting one endpoint block an unrelated one.
        countReturns(4L);

        assertThat(limiter.check(RateLimitScope.CREATE, KEY).allowed()).isFalse();
        assertThat(limiter.check(RateLimitScope.REDIRECT, KEY).allowed()).isTrue();
    }

    @Test
    @DisplayName("sets the expiry only on the first request of a window")
    void setsExpiryOnlyOnFirstRequest() {
        // The window must not be extended on every request; if it were, a caller who
        // keeps knocking would hold the same counter open forever and it would never
        // reset.
        countReturns(1L);
        limiter.check(RateLimitScope.CREATE, KEY);
        verify(redis).expire(anyString(), eq(Duration.ofSeconds(60)));

        countReturns(2L);
        limiter.check(RateLimitScope.CREATE, KEY);
        verify(redis).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("REGRESSION: fails open when Redis is unreachable")
    void failsOpenWhenRedisIsDown() {
        // The reliability contract (NFR3). This limiter sits in front of the redirect,
        // so treating an unreachable Redis as a rejection would turn a cache-tier
        // outage into a total outage - the opposite of the feature's purpose.
        when(valueOps.increment(anyString())).thenThrow(new QueryTimeoutException("down"));

        assertThat(limiter.check(RateLimitScope.REDIRECT, KEY).allowed()).isTrue();
    }

    @Test
    @DisplayName("fails open when the increment returns no value")
    void failsOpenOnNullIncrement() {
        when(valueOps.increment(anyString())).thenReturn(null);

        assertThat(limiter.check(RateLimitScope.REDIRECT, KEY).allowed()).isTrue();
    }

    @Test
    @DisplayName("allows and does not count when no client key is available")
    void allowsWhenKeyIsMissing() {
        // Every keyless caller would otherwise share one bucket, making the limit both
        // meaningless and unfair to whoever arrived first.
        assertThat(limiter.check(RateLimitScope.REDIRECT, null).allowed()).isTrue();
        assertThat(limiter.check(RateLimitScope.REDIRECT, "  ").allowed()).isTrue();

        verify(valueOps, never()).increment(anyString());
    }

    @Test
    @DisplayName("retry-after is never zero")
    void retryAfterIsNeverZero() {
        // A Retry-After of 0 invites an immediate retry that is certain to fail again,
        // turning a throttle into a retry storm.
        countReturns(99L);

        assertThat(limiter.check(RateLimitScope.CREATE, KEY).retryAfter())
                .isGreaterThanOrEqualTo(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("counts different callers separately")
    void countsCallersSeparately() {
        limiter.check(RateLimitScope.CREATE, "caller-a");
        limiter.check(RateLimitScope.CREATE, "caller-b");

        verify(valueOps).increment(org.mockito.ArgumentMatchers.contains("caller-a"));
        verify(valueOps).increment(org.mockito.ArgumentMatchers.contains("caller-b"));
    }
}
