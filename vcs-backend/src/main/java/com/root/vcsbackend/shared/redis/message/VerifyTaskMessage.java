package com.root.vcsbackend.shared.redis.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Task message sent to the worker to verify that applying a diff to the latest
 * version produces a document whose checksum matches the expected value.
 * <p>
 * The worker derives the actual S3 keys from {@code docId} + {@code newVersionNumber}
 * using {@code S3KeyTemplates} — no explicit keys are sent.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class VerifyTaskMessage extends WorkerTaskMessage {

    /** The version number being created (the new diff version). */
    private Integer newVersionNumber;
}

