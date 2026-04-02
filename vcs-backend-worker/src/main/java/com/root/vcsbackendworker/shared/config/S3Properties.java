package com.root.vcsbackendworker.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.s3")
public record S3Properties(
        String endpoint,
        String bucket,
        String region,
        String accessKey,
        String secretKey
) {
}

