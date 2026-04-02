package com.root.vcsbackendworker.workerjob.api.outbound;

import com.root.vcsbackendworker.shared.messaging.MessageMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiffProcessingResultMessage {

    private MessageMetadata metadata;

    private UUID docId;
    private UUID versionId;

    private ProcessingStatus status;
    private FailureReason failureReason;

    private String actualChecksum;
    private String promotedS3Key;

    // Human-readable context for logs/observability; not for control flow.
    private String details;
}


