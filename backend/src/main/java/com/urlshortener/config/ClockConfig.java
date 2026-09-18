package com.urlshortener.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Makes the system clock injectable.
 *
 * <p>Components that need the current time depend on this bean rather than calling
 * {@code Instant.now()} directly, so tests can pin time instead of racing the wall
 * clock - which matters for expiry, where the interesting cases sit exactly on the
 * boundary.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        // UTC explicitly: the database stores TIMESTAMPTZ and the application must not
        // acquire a timezone from whatever host it happens to run on.
        return Clock.systemUTC();
    }
}
