package com.root.vcsbackend.version.api;

import java.util.UUID;

/**
 * Lightweight projection exposed from the version module API.
 * Avoids leaking {@code VersionEntity} (an internal domain type) across module boundaries.
 */
public record VersionSummary(UUID id, String s3Key) {}

