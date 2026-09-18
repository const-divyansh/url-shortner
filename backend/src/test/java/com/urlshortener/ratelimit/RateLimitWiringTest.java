package com.urlshortener.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.urlshortener.config.RateLimitConfig;
import com.urlshortener.config.RateLimitProperties;
import com.urlshortener.event.ClientIpResolver;
import com.urlshortener.event.IpHasher;

/**
 * Verifies that the rate limiting beans are assemblable by Spring, not merely
 * constructible by hand.
 *
 * <p><strong>Why this exists.</strong> Every other test in this package builds {@link
 * RateLimitInterceptor} directly with {@code new}, which cannot detect a container
 * wiring fault. A real one slipped through exactly that gap: the class has a second,
 * private constructor for {@code withScope}, and with more than one constructor present
 * Spring refuses to guess and looks for a no-argument one instead - so the application
 * failed at startup while the whole unit suite stayed green.
 *
 * <p>Uses a sliced context, following the convention set by the validation chain test,
 * so this needs neither a database nor Redis to prove the wiring.
 */
@SpringJUnitConfig(classes = {
        RateLimitConfig.class,
        RateLimitInterceptor.class,
        RateLimitKeyNormaliser.class,
        RateLimitWiringTest.StubInfrastructure.class
})
@EnableConfigurationProperties(RateLimitProperties.class)
@TestPropertySource(properties = {
        "app.ratelimit.enabled=true",
        "app.ratelimit.create.limit=20",
        "app.ratelimit.create.window=1m",
        "app.ratelimit.redirect.limit=300",
        "app.ratelimit.redirect.window=1m"
})
class RateLimitWiringTest {

    @Configuration
    static class StubInfrastructure {

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        ClientIpResolver clientIpResolver() {
            return mock(ClientIpResolver.class);
        }

        @Bean
        IpHasher ipHasher() {
            return mock(IpHasher.class);
        }
    }

    @Autowired
    private RateLimitInterceptor interceptor;

    @Autowired
    private RateLimiter rateLimiter;

    @Test
    @DisplayName("REGRESSION: Spring can instantiate the interceptor despite two constructors")
    void interceptorIsWireable() {
        assertThat(interceptor).isNotNull();
    }

    @Test
    @DisplayName("the configured limiter is the Redis one when enabled")
    void selectsRedisLimiterWhenEnabled() {
        assertThat(rateLimiter).isInstanceOf(RedisRateLimiter.class);
    }
}
