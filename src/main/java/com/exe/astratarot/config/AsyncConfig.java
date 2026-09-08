package com.exe.astratarot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for asynchronous execution.
 *
 * <p>Enables Spring {@code @Async} support and provides a dedicated thread pool
 * for SSE streaming tasks so long-running connections do not starve the
 * request-handling threads of the embedded container.
 *
 * <p>The pool is deliberately small — SSE streams are I/O-bound and Gemini's
 * response is typically under two minutes.  A bounded pool acts as a natural
 * backstop against runaway concurrent streams.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Dedicated executor for SSE streaming from the LLM provider.
     *
     * <ul>
     *   <li><b>Core threads:</b> 2 — enough for idle / low traffic</li>
     *   <li><b>Max threads:</b> 5 — hard ceiling to protect the database</li>
     *   <li><b>Queue:</b> 10 — brief bursts are queued rather than rejected</li>
     *   <li><b>Rejection:</b> caller-runs so the request thread blocks instead
     *       of silently dropping the SSE stream</li>
     * </ul>
     */
    @Bean(name = "sseTaskExecutor")
    public Executor sseTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("sse-");
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
