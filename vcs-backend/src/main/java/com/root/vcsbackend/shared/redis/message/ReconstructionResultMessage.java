package com.root.vcsbackend.shared.redis.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Result message received from the worker after reconstructing a full document
 * at a specific version. Published on the {@code vcs.diff.results} Redis channel.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconstructionResultMessage {

    private MessageMetadata metadata;
    private UUID docId;
    private UUID versionId;
    private ProcessingStatus status;
    private FailureReason failureReason;

    /** Pre-signed S3 GET URL for downloading the reconstructed document (only on success). */
    private String presignedDownloadUrl;
}

