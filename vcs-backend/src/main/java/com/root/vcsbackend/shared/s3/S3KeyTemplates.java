package com.root.vcsbackend.shared.s3;

import java.util.UUID;

/**
 * Canonical S3 key templates shared between vcs-backend and vcs-backend-worker.
 * Keys are deterministic from (docId, versionNumber) — no DB lookup required.
 */
public final class S3KeyTemplates {
    private S3KeyTemplates() {}

    /** Permanent version storage: {@code documents/{docId}/v{versionNumber}} */
    public static String permanentVersion(UUID docId, int versionNumber) {
        return "documents/" + docId + "/v" + versionNumber;
    }

    /** Staging diff awaiting worker verification: {@code tmp/{docId}/v{versionNumber}.diff} */
    public static String stagingDiff(UUID docId, int versionNumber) {
        return "tmp/" + docId + "/v" + versionNumber + ".diff";
    }

    /** Temporary reconstructed document for download: {@code tmp/{docId}/v{versionNumber}} */
    public static String tempReconstruction(UUID docId, int versionNumber) {
        return "tmp/" + docId + "/v" + versionNumber;
    }
}