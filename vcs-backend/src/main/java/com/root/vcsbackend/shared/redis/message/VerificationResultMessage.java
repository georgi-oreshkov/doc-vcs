package com.root.vcsbackend.shared.redis.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Result message received from the worker after verifying a diff.
 * Published on the {@code vcs.diff.results} Redis channel.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationResultMessage {

    private MessageMetadata metadata;
    private UUID docId;
    private UUID versionId;
    private ProcessingStatus status;
    private FailureReason failureReason;
    private String actualChecksum;
}

