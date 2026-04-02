package com.root.vcsbackendworker.messaging.contract.inbound;

import com.root.vcsbackendworker.messaging.contract.common.MessageMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiffJobMessage {

    private MessageMetadata metadata;

    private UUID docId;
    private UUID versionId;

    private String oldS3Key;
    private String diffS3Key;

    // SHA-256 checksum of the reconstructed new version expected by the publisher.
    private String expectedChecksum;

    @Builder.Default
    private String checksumAlgorithm = "SHA-256";
}

