package com.root.vcsbackendworker.shared.messaging.inbound;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.root.vcsbackendworker.shared.messaging.MessageMetadata;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Polymorphic base for all worker task messages arriving via Redis.
 * Jackson resolves the concrete subtype from the {@code taskType} field.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "taskType",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = VerifyTaskMessage.class, name = "VERIFY_DIFF"),
        @JsonSubTypes.Type(value = ReconstructTaskMessage.class, name = "RECONSTRUCT_DOCUMENT")
})
public abstract class WorkerTaskMessage {

    private MessageMetadata metadata;
    private WorkerTaskType taskType;
    private UUID docId;
    private UUID versionId;

    // SHA-256 checksum of the expected new full document after applying the diff.
    private String expectedChecksum;
}

