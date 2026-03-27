package com.root.vcsbackend.shared.s3;

import lombok.RequiredArgsConstructor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3PresignService {

    // TODO: inject software.amazon.awssdk.services.s3.presigner.S3Presigner

    /**
     * Generates a pre-signed PUT URL for uploading a document version.
     * Retried up to 3 times with exponential back-off on any SDK exception.
     */
    @Retryable(
        retryFor = Exception.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 300, multiplier = 2.0)
    )
    public String generateUploadUrl(String s3Key) {
        // TODO: implement — S3Presigner.presignPutObject(...)
        return null;
    }

    /**
     * Generates a pre-signed GET URL for downloading a document version.
     * Retried up to 3 times with exponential back-off on any SDK exception.
     */
    @Retryable(
        retryFor = Exception.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 300, multiplier = 2.0)
    )
    public String generateDownloadUrl(String s3Key) {
        // TODO: implement — S3Presigner.presignGetObject(...)
        return null;
    }

    /** Fallback if all generateUploadUrl retries are exhausted. */
    @Recover
    public String recoverUpload(Exception ex, String s3Key) {
        throw new RuntimeException("S3 pre-sign upload URL failed after retries for key: " + s3Key, ex);
    }

    /** Fallback if all generateDownloadUrl retries are exhausted. */
    @Recover
    public String recoverDownload(Exception ex, String s3Key) {
        throw new RuntimeException("S3 pre-sign download URL failed after retries for key: " + s3Key, ex);
    }
}
