package com.root.vcsbackend.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code app.s3.*} block from application.properties.
 * <p>
 * In production, override via environment variables:
 * APP_S3_ENDPOINT, APP_S3_BUCKET, APP_S3_REGION,
 * APP_S3_ACCESS-KEY, APP_S3_SECRET-KEY, APP_S3_PRESIGN-DURATION-MINUTES
 */
@ConfigurationProperties(prefix = "app.s3")
public record S3Properties(
        String endpoint,
        String publicEndpoint,
        String bucket,
        String region,
        String accessKey,
        String secretKey,
        int presignDurationMinutes
) {}

