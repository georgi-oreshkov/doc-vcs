package com.root.vcsbackend.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

@Configuration
@EnableRetry
public class RetryConfig {
    // Spring Retry is enabled globally for the application.
    // Use @Retryable on any Spring-managed bean method to add automatic retry behavior.
    // Use @Recover to define a fallback method when all retries are exhausted.
}

