package com.root.vcsbackendworker.infrastructure.s3;

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


