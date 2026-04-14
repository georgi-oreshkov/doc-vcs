package com.root.vcsbackend.document.api;

import java.util.UUID;

/**
 * Lightweight projection of a document exposed by the document module's public API.
 * <p>
 * Follows the same pattern as {@code VersionSummary} in the version module —
 * avoids leaking {@code DocumentEntity} (an internal domain type) across
 * Spring Modulith module boundaries.
 *
 * <p>Fields exposed here are those legitimately needed by other modules:
 * <ul>
 *   <li>{@code id}                      — document identifier</li>
 *   <li>{@code orgId}                   — owning organization (used for RBAC)</li>
 *   <li>{@code authorId}                — creator (used for notifications)</li>
 *   <li>{@code name}                    — human-readable label</li>
 *   <li>{@code latestVersionId}         — current head version (maybe null)</li>
 *   <li>{@code latestApprovedVersionId} — last approved version (maybe null)</li>
 * </ul>
 */
public record DocumentSummary(
        UUID id,
        UUID orgId,
        UUID authorId,
        String name,
        UUID latestVersionId,
        UUID latestApprovedVersionId
) {}

