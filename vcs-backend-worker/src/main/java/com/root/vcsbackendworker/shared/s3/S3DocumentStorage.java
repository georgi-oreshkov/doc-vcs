package com.root.vcsbackendworker.shared.s3;

import com.root.vcsbackendworker.shared.config.S3Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3DocumentStorage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    /**
     * Downloads the object at {@code s3Key} from the configured bucket and returns its raw bytes.
     */
    public byte[] fetchBytes(String s3Key) {
        log.debug("Fetching S3 object: bucket={}, key={}", s3Properties.bucket(), s3Key);

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(s3Key)
                .build();

        byte[] bytes = s3Client.getObjectAsBytes(request).asByteArray();
        log.debug("Fetched {} bytes from S3 key={}", bytes.length, s3Key);
        return bytes;
    }

    /**
     * Uploads {@code bytes} to the configured bucket under {@code s3Key}.
     * Overwrites any existing object at that key.
     */
    public void uploadBytes(String s3Key, byte[] bytes) {
        log.debug("Uploading {} bytes to S3: bucket={}, key={}", bytes.length, s3Properties.bucket(), s3Key);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(s3Key)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(bytes));
        log.info("Uploaded {} bytes to S3 key={}", bytes.length, s3Key);
    }

    /**
     * Generates a pre-signed GET URL for the object at {@code s3Key}, valid for
     * {@code app.s3.presign-duration-minutes} minutes.
     */
    public String generatePresignedDownloadUrl(String s3Key) {
        log.debug("Generating presigned GET URL: bucket={}, key={}", s3Properties.bucket(), s3Key);

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(s3Properties.presignDurationMinutes()))
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(s3Properties.bucket())
                        .key(s3Key)
                        .build())
                .build();

        String url = s3Presigner.presignGetObject(presignRequest).url().toString();
        log.debug("Presigned URL generated for key={}", s3Key);
        return url;
    }
}
