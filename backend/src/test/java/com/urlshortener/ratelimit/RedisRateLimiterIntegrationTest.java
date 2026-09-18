package com.urlshortener.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.urlshortener.AbstractIntegrationTest;
import com.urlshortener.config.RateLimitProperties;
import com.urlshortener.config.RateLimitProperties.Budget;

class RedisRateLimiterIntegrationTest extends AbstractIntegrationTest {

    @AfterEach
    void resumeRedisIfNeeded() {
        Boolean paused = REDIS.getDockerClient()
                .inspectContainerCmd(REDIS.getContainerId())
                .exec()
                .getState()
                .getPaused();
        if (Boolean.TRUE.equals(paused)) {
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
        }
    }

    @Test
    @DisplayName("real Redis rejects a burst past the limit, then resets on the next fixed window")
    void realRedisEnforcesBurstLimitAndWindowReset() throws InterruptedException {
        Duration window = Duration.ofSeconds(2);
        RedisRateLimiter limiter = limiter(2, window);
        String key = "client-" + UUID.randomUUID();

        waitForFreshWindow(window);

        assertThat(limiter.check(RateLimitScope.CREATE, key).allowed()).isTrue();
        assertThat(limiter.check(RateLimitScope.CREATE, key).allowed()).isTrue();

        RateLimitDecision rejected = limiter.check(RateLimitScope.CREATE, key);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfter()).isPositive();

        waitFor(rejected.retryAfter().plusMillis(250));

        assertThat(limiter.check(RateLimitScope.CREATE, key).allowed()).isTrue();
    }

    @Test
    @DisplayName("fails open when the real Redis container is paused mid-test")
    void failsOpenWhileRedisIsUnavailable() {
        RedisRateLimiter limiter = limiter(1, Duration.ofSeconds(5));

        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();

        assertThat(limiter.check(RateLimitScope.REDIRECT, "paused-" + UUID.randomUUID()).allowed()).isTrue();
    }

    private RedisRateLimiter limiter(int limit, Duration window) {
        return new RedisRateLimiter(
                redisTemplate,
                new RateLimitProperties(true, Map.of(
                        RateLimitScope.CREATE, new Budget(limit, window),
                        RateLimitScope.REDIRECT, new Budget(limit, window))));
    }

    private static void waitForFreshWindow(Duration window) throws InterruptedException {
        long windowMillis = window.toMillis();
        long minimumRemaining = Math.max(250L, windowMillis / 2);

        while (true) {
            long now = System.currentTimeMillis();
            long remaining = windowMillis - (now % windowMillis);
            if (remaining > minimumRemaining) {
                return;
            }
            Thread.sleep(remaining + 50);
        }
    }
}
