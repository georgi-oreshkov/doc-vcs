package com.root.vcsbackendworker.verifyTask.application;

import com.root.vcsbackendworker.shared.db.NotificationWriteGateway;
import com.root.vcsbackendworker.shared.diff.DiffApplicationException;
import com.root.vcsbackendworker.shared.diff.DiffApplicator;
import com.root.vcsbackendworker.shared.messaging.FailureReason;
import com.root.vcsbackendworker.shared.messaging.inbound.VerifyTaskMessage;
import com.root.vcsbackendworker.shared.s3.S3DocumentStorage;
import com.root.vcsbackendworker.shared.verify.ChecksumVerifier;
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
    private final NotificationWriteGateway notificationWriteGateway;

    public void handle(VerifyTaskMessage task) {
        log.info("Verify task started: docId={}, versionId={}", task.getDocId(), task.getVersionId());

        // 1. Fetch the current latest (base) version bytes
        byte[] baseBytes;
        try {
            baseBytes = s3.fetchBytes(task.getLatestVersionS3Key());
        } catch (NoSuchKeyException e) {
            log.error("Base version not found in S3: key={}", task.getLatestVersionS3Key(), e);
            recordFailure(task, FailureReason.SOURCE_NOT_FOUND);
            return;
        } catch (Exception e) {
            log.error("Failed to fetch base version from S3: key={}", task.getLatestVersionS3Key(), e);
            recordFailure(task, FailureReason.STORAGE_ERROR);
            return;
        }

        // 2. Fetch the diff bytes
        byte[] diffBytes;
        try {
            diffBytes = s3.fetchBytes(task.getDiffS3Key());
        } catch (NoSuchKeyException e) {
            log.error("Diff not found in S3: key={}", task.getDiffS3Key(), e);
            recordFailure(task, FailureReason.SOURCE_NOT_FOUND);
            return;
        } catch (Exception e) {
            log.error("Failed to fetch diff from S3: key={}", task.getDiffS3Key(), e);
            recordFailure(task, FailureReason.STORAGE_ERROR);
            return;
        }

        // 3. Apply the diff
        byte[] resultBytes;
        try {
            resultBytes = diffApplicator.apply(baseBytes, diffBytes);
        } catch (DiffApplicationException e) {
            log.error("Diff application failed: docId={}, versionId={}", task.getDocId(), task.getVersionId(), e);
            recordFailure(task, FailureReason.DIFF_APPLY_FAILED);
            return;
        }

        // 4. Verify checksum
        if (!checksumVerifier.matches(resultBytes, task.getExpectedChecksum())) {
            log.warn("Checksum mismatch: docId={}, versionId={}, expected={}",
                    task.getDocId(), task.getVersionId(), task.getExpectedChecksum());
            recordFailure(task, FailureReason.CHECKSUM_MISMATCH);
            return;
        }

        // 5. Upload to permanent S3 location (strip .diff suffix from the diff key)
        String permanentKey = toPermanentKey(task.getDiffS3Key());
        try {
            s3.moveObject(task.getDiffS3Key(), permanentKey);
        } catch (Exception e) {
            log.error("Failed to move diff to S3: key={}", permanentKey, e);
            recordFailure(task, FailureReason.STORAGE_ERROR);
            return;
        }

        // 6. Update version checksum + insert notification (single DB transaction)
        notificationWriteGateway.recordVerificationSuccess(
                task.getRecipientId(), task.getDocId(), task.getVersionId(), task.getExpectedChecksum());

        log.info("Verify task succeeded: docId={}, versionId={}, permanentKey={}",
                task.getDocId(), task.getVersionId(), permanentKey);
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

    private void recordFailure(VerifyTaskMessage task, FailureReason reason) {
        notificationWriteGateway.recordFailure(
                task.getRecipientId(), task.getDocId(), task.getVersionId(),
                task.getTaskType().name(), reason);
    }
}
