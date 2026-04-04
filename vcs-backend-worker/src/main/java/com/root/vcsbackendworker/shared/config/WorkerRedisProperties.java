package com.root.vcsbackendworker.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.worker.redis")
public record WorkerRedisProperties(
        String channel,
        String resultChannel
) {
}

