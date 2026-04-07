package com.root.vcsbackend.shared.redis.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Task message sent to the worker to verify that applying a diff to the latest
 * version produces a document whose checksum matches the expected value.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class VerifyTaskMessage extends WorkerTaskMessage {

    /** S3 key to the latest (current) full version of the document. */
    private String latestVersionS3Key;

    /** S3 key for the incoming diff uploaded by the client. */
    private String diffS3Key;
}

