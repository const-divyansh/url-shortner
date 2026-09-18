package com.urlshortener.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.urlshortener.ratelimit.NoOpRateLimiter;
import com.urlshortener.ratelimit.RateLimiter;
import com.urlshortener.ratelimit.RedisRateLimiter;

/**
 * Selects the active {@link RateLimiter} implementation.
 *
 * <p>Factory Method: configuration chooses which object is built, so the on/off
 * decision is made once here rather than being re-checked on every request. Nothing
 * downstream knows whether limiting is enabled - it only knows it has a limiter.
 *
 * <p>The choice is logged at startup. A rate limiter that is off is invisible in normal
 * operation, which makes it exactly the kind of setting that gets disabled during an
 * incident and silently stays that way; a startup line means the state is always
 * recoverable from the logs rather than only from live configuration.
 */
@Configuration
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    @Bean
    public RateLimiter rateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
        if (!properties.enabled()) {
            log.warn("Rate limiting is DISABLED (app.ratelimit.enabled=false); no request budgets are enforced");
            return new NoOpRateLimiter();
        }

        log.info("Rate limiting enabled - create: {}/{}, redirect: {}/{}",
                properties.create().limit(), properties.create().window(),
                properties.redirect().limit(), properties.redirect().window());

        return new RedisRateLimiter(redis, properties);
    }
}
