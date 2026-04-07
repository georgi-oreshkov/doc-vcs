package com.root.vcsbackend.request.api;

import com.root.vcsbackend.request.domain.ForkRequestEntity.RequestStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight projection of a fork request exposed by the request module's public API.
 * <p>
 * Avoids leaking {@code ForkRequestEntity} (an internal domain type) across
 * Spring Modulith module boundaries — mirrors the pattern used by
 * {@code DocumentSummary} and {@code VersionSummary} in their respective modules.
 */
public record ForkRequestSummary(
        UUID id,
        UUID requesterId,
        UUID docId,
        UUID versionId,
        RequestStatus status,
        Instant createdAt
) {}

