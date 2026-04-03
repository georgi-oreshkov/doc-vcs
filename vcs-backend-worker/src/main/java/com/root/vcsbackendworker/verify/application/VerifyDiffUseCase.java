package com.root.vcsbackendworker.verify.application;

import com.root.vcsbackendworker.shared.diff.DiffApplicationException;
import com.root.vcsbackendworker.shared.diff.DiffApplicator;
import com.root.vcsbackendworker.shared.messaging.WorkerResultPublisher;
import com.root.vcsbackendworker.shared.messaging.inbound.VerifyTaskMessage;
import com.root.vcsbackendworker.shared.messaging.outbound.FailureReason;
import com.root.vcsbackendworker.shared.messaging.outbound.ProcessingStatus;
import com.root.vcsbackendworker.shared.messaging.outbound.VerificationResultMessage;
import com.root.vcsbackendworker.shared.s3.S3DocumentStorage;
import com.root.vcsbackendworker.verify.domain.ChecksumVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyDiffUseCase {


    private final S3DocumentStorage s3;
    private final DiffApplicator diffApplicator;
    private final ChecksumVerifier checksumVerifier;
    private final WorkerResultPublisher resultPublisher;

    public void handle(VerifyTaskMessage task) {
        log.info("Verify task started: docId={}, versionId={}", task.getDocId(), task.getVersionId());

        // 1. Fetch the current latest (base) version bytes
        byte[] baseBytes;
        try {
            baseBytes = s3.fetchBytes(task.getLatestVersionS3Key());
        } catch (NoSuchKeyException e) {
            log.error("Base version not found in S3: key={}", task.getLatestVersionS3Key(), e);
            resultPublisher.publish(failureResult(task, FailureReason.SOURCE_NOT_FOUND, null));
            return;
        } catch (Exception e) {
            log.error("Failed to fetch base version from S3: key={}", task.getLatestVersionS3Key(), e);
            resultPublisher.publish(failureResult(task, FailureReason.STORAGE_ERROR, null));
            return;
        }

        // 2. Fetch the diff bytes
        byte[] diffBytes;
        try {
            diffBytes = s3.fetchBytes(task.getDiffS3Key());
        } catch (NoSuchKeyException e) {
            log.error("Diff not found in S3: key={}", task.getDiffS3Key(), e);
            resultPublisher.publish(failureResult(task, FailureReason.SOURCE_NOT_FOUND, null));
            return;
        } catch (Exception e) {
            log.error("Failed to fetch diff from S3: key={}", task.getDiffS3Key(), e);
            resultPublisher.publish(failureResult(task, FailureReason.STORAGE_ERROR, null));
            return;
        }

        // 3. Apply the diff
        byte[] resultBytes;
        try {
            resultBytes = diffApplicator.apply(baseBytes, diffBytes);
        } catch (DiffApplicationException e) {
            log.error("Diff application failed: docId={}, versionId={}", task.getDocId(), task.getVersionId(), e);
            resultPublisher.publish(failureResult(task, FailureReason.DIFF_APPLY_FAILED, null));
            return;
        }

        // 4. Verify checksum — compute once, compare directly (avoids hashing twice)
        String actualChecksum = checksumVerifier.sha256Hex(resultBytes);
        if (!actualChecksum.equalsIgnoreCase(task.getExpectedChecksum())) {
            log.warn("Checksum mismatch: docId={}, versionId={}, expected={}, actual={}",
                    task.getDocId(), task.getVersionId(), task.getExpectedChecksum(), actualChecksum);
            resultPublisher.publish(failureResult(task, FailureReason.CHECKSUM_MISMATCH, actualChecksum));
            return;
        }

        // 5. Upload to permanent S3 location (strip .diff suffix from the diff key)
        String permanentKey = toPermanentKey(task.getDiffS3Key());
        try {
            s3.uploadBytes(permanentKey, resultBytes);
        } catch (Exception e) {
            log.error("Failed to upload result to S3: key={}", permanentKey, e);
            resultPublisher.publish(failureResult(task, FailureReason.STORAGE_ERROR, actualChecksum));
            return;
        }

        // 6. Publish success result
        log.info("Verify task succeeded: docId={}, versionId={}, permanentKey={}", task.getDocId(), task.getVersionId(), permanentKey);
        resultPublisher.publish(successResult(task, actualChecksum));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Derives the permanent S3 key from the diff key by stripping the {@code .diff} suffix.
     * e.g. {@code documents/{docId}/v4.diff} → {@code documents/{docId}/v4}
     */
    private String toPermanentKey(String diffS3Key) {
        if (diffS3Key.endsWith(".diff")) {
            return diffS3Key.substring(0, diffS3Key.length() - ".diff".length());
        }
        return diffS3Key;
    }

    private VerificationResultMessage successResult(VerifyTaskMessage task, String actualChecksum) {
        return VerificationResultMessage.builder()
                .metadata(resultPublisher.buildMetadata(task))
                .docId(task.getDocId())
                .versionId(task.getVersionId())
                .status(ProcessingStatus.SUCCEEDED)
                .actualChecksum(actualChecksum)
                .build();
    }

    private VerificationResultMessage failureResult(VerifyTaskMessage task, FailureReason reason, String actualChecksum) {
        return VerificationResultMessage.builder()
                .metadata(resultPublisher.buildMetadata(task))
                .docId(task.getDocId())
                .versionId(task.getVersionId())
                .status(ProcessingStatus.FAILED)
                .failureReason(reason)
                .actualChecksum(actualChecksum)
                .build();
    }
}


