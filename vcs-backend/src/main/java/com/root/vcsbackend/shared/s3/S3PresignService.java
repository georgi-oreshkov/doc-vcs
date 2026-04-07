package com.root.vcsbackend.shared.s3;

import com.root.vcsbackend.shared.config.S3Properties;
import com.root.vcsbackend.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class S3PresignService {

    private final S3Presigner presigner;
    private final S3Properties s3Properties;

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
        var putRequest = PutObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(s3Key)
                .build();

        var presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(s3Properties.presignDurationMinutes()))
                .putObjectRequest(putRequest)
                .build();

        return presigner.presignPutObject(presignRequest).url().toString();
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
        var getRequest = GetObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(s3Key)
                .build();

        var presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(s3Properties.presignDurationMinutes()))
                .getObjectRequest(getRequest)
                .build();

        return presigner.presignGetObject(presignRequest).url().toString();
    }

    /** Fallback if all generateUploadUrl retries are exhausted. */
    @Recover
    public String recoverUpload(Exception ex, String s3Key) {
        throw new AppException(HttpStatus.SERVICE_UNAVAILABLE,
                "S3 pre-sign upload URL failed after retries for key: " + s3Key, ex);
    }

    /** Fallback if all generateDownloadUrl retries are exhausted. */
    @Recover
    public String recoverDownload(Exception ex, String s3Key) {
        throw new AppException(HttpStatus.SERVICE_UNAVAILABLE,
                "S3 pre-sign download URL failed after retries for key: " + s3Key, ex);
    }
}
