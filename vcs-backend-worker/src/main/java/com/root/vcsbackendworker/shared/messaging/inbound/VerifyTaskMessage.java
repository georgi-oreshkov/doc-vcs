package com.root.vcsbackendworker.shared.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class VerifyTaskMessage extends WorkerTaskMessage {

    // S3 key to the latest (current) full version of the document.
    private String latestVersionS3Key;

    // S3 key for the incoming diff uploaded by the client.
    private String diffS3Key;

    // SHA-256 checksum of the expected new full document after applying the diff.
    private String expectedChecksum;
}
