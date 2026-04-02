package com.root.vcsbackendworker.infrastructure.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.worker.redis")
public record WorkerRedisProperties(
        String channel,
        String resultChannel
) {
}


