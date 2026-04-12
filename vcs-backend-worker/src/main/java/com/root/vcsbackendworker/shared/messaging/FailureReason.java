package com.root.vcsbackendworker.shared.messaging;

/**
 * Enumerates reasons a worker task can fail.
 * Persisted as the {@code failureReason} field inside notification payloads.
 */
public enum FailureReason {
    CHECKSUM_MISMATCH,
    DIFF_APPLY_FAILED,
    SOURCE_NOT_FOUND,
    INVALID_MESSAGE,
    STORAGE_ERROR,
    INTERNAL_ERROR
}

