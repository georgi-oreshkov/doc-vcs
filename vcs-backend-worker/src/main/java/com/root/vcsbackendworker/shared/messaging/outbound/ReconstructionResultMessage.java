package com.root.vcsbackendworker.shared.messaging.outbound;

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
public class ReconstructionResultMessage {

    private MessageMetadata metadata;

    private UUID docId;
    private UUID versionId;

    private ProcessingStatus status;
    private FailureReason failureReason;
    private String presignedDownloadUrl;
}