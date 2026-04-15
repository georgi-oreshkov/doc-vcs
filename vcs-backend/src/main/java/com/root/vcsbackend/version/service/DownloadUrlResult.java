package com.root.vcsbackend.version.service;

import com.root.vcsbackend.model.GetVersionDownloadUrl200Response;

/**
 * Returned by {@link VersionService#getDownloadUrl}.
 * <p>
 * {@code reconstructionDispatched = true} means the version is a DIFF and a
 * {@code RECONSTRUCT_DOCUMENT} task was published to the worker. The controller
 * should respond with HTTP 202 so the client knows to wait for the
 * {@code DOCUMENT_RECONSTRUCTED} SSE notification.
 * <p>
 * {@code reconstructionDispatched = false} means the version is a full SNAPSHOT
 * and the {@code downloadUrl} is immediately usable (HTTP 200).
 */
public record DownloadUrlResult(
        GetVersionDownloadUrl200Response response,
        boolean reconstructionDispatched
) {}
