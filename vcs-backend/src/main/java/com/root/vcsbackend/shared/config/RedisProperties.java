package com.root.vcsbackend.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code app.redis.*} block from application.properties.
 *
 * <ul>
 *   <li>{@code diffJobsChannel}    — Redis Pub/Sub channel for publishing diff/reconstruct
 *       tasks to the worker.  Override via env var {@code WORKER_CHANNEL}.</li>
 *   <li>{@code diffResultsChannel} — Redis Pub/Sub channel the worker publishes results on.
 *       Override via env var {@code WORKER_RESULT_CHANNEL}.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.redis")
public record RedisProperties(
        String diffJobsChannel,
        String diffResultsChannel
) {}

