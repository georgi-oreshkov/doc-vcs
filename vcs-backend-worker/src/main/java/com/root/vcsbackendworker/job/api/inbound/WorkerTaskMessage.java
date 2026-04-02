package com.root.vcsbackendworker.workerjob.api.inbound;

import com.root.vcsbackendworker.shared.messaging.MessageMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerTaskMessage {

    private MessageMetadata metadata;

    private UUID docId;
    private UUID versionId;

    private String oldS3Key;
    private String diffS3Key;
    private List<String> diffS3Keys;
    private String outputS3Key;

    // SHA-256 checksum of the reconstructed new version expected by the publisher.
    private String expectedChecksum;

    @Builder.Default
    private String checksumAlgorithm = "SHA-256";

    @Builder.Default
    private WorkerTaskType taskType = WorkerTaskType.VERIFY_DIFF;
}


