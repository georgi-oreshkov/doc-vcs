package com.root.vcsbackendworker.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code app.worker.redis.*} block from application.properties.
 *
 * <p>{@code stream} — Redis Stream key to consume tasks from (published by vcs-backend via XADD).
 * <p>{@code consumerGroup} — Consumer group name (must match the backend's group creation).
 * <p>{@code consumerName} — Unique consumer name within the group (defaults to a UUID per instance).
 * <p>{@code listenerEnabled} — When {@code false}, the stream listener bean is not registered (useful in tests).
 */
@ConfigurationProperties(prefix = "app.worker.redis")
public record WorkerRedisProperties(
        String stream,
        String consumerGroup,
        String consumerName,
        boolean listenerEnabled,
        int concurrency
) {
}
