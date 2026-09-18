package com.urlshortener.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Executor for analytics work, kept separate from request handling.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean("analyticsExecutor")
    public Executor analyticsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);

        // Bounded deliberately. An unbounded queue turns a Redis outage into unbounded
        // heap growth: clicks would accumulate faster than they drain until the process
        // fails - trading a lost click for a lost application.
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("analytics-");

        // Discard rather than block. The default CallerRunsPolicy would execute the task
        // on the request thread, which is precisely what this executor exists to
        // prevent: under load the redirect would inherit the analytics latency.
        executor.setRejectedExecutionHandler((runnable, pool) ->
                log.warn("Analytics queue full; dropping click"));

        // Give in-flight hand-offs a chance to reach Redis during shutdown, so a normal
        // restart does not discard clicks that were already accepted.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);

        executor.initialize();
        return executor;
    }
}
