package com.root.vcsbackend.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code app.redis.*} block from application.properties.
 *
 * <ul>
 *   <li>{@code diffJobsStream} — Redis Stream key for publishing diff/reconstruct
 *       tasks to the worker. The worker reads with {@code XREADGROUP} from a consumer
 *       group on this stream. Override via env var {@code WORKER_STREAM}.</li>
 * </ul>
 *
 * <p>No results channel/stream exists: the worker writes outcomes directly to
 * PostgreSQL and the backend receives them via the {@code pg_notify} trigger on
 * the {@code notifications} table.
 */
@ConfigurationProperties(prefix = "app.redis")
public record RedisProperties(
        String diffJobsStream
) {}
