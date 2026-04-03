package com.root.vcsbackendworker.shared.messaging;

public enum FailureReason {
    CHECKSUM_MISMATCH,
    DIFF_APPLY_FAILED,
    SOURCE_NOT_FOUND,
    INVALID_MESSAGE,
    STORAGE_ERROR,
    INTERNAL_ERROR
}

