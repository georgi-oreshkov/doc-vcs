package com.root.vcsbackendworker.reconstructTask.application;

import com.root.vcsbackendworker.shared.db.NotificationWriteGateway;
import com.root.vcsbackendworker.shared.db.VersionQueryGateway;
import com.root.vcsbackendworker.shared.db.VersionRow;
import com.root.vcsbackendworker.shared.messaging.FailureReason;
import com.root.vcsbackendworker.shared.messaging.inbound.ReconstructTaskMessage;
import com.root.vcsbackendworker.shared.reconstruct.ReconstructionException;
import com.root.vcsbackendworker.shared.reconstruct.Reconstructor;
import com.root.vcsbackendworker.shared.s3.S3DocumentStorage;
import com.root.vcsbackendworker.shared.s3.S3KeyTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconstructDocumentUseCase {

    private final VersionQueryGateway versionQueryGateway;
    private final S3DocumentStorage s3;
    private final NotificationWriteGateway notificationWriteGateway;
    private final Reconstructor reconstructor;

    public void handle(ReconstructTaskMessage task) {
        log.info("Reconstruct task started: docId={}, versionId={}, targetVersion={}",
                task.getDocId(), task.getVersionId(), task.getTargetVersionNumber());

        // 1. Resolve the target version row from the database
        Optional<VersionRow> targetRowOpt = versionQueryGateway.findById(task.getVersionId());
        if (targetRowOpt.isEmpty()) {
            log.error("Target version not found in DB: versionId={}", task.getVersionId());
            recordFailure(task, FailureReason.SOURCE_NOT_FOUND);
            return;
        }
        VersionRow targetRow = targetRowOpt.get();

        // 2. If the target itself is a snapshot, fetch it directly — no diff chain needed
        byte[] resultBytes;
        if ("SNAPSHOT".equals(targetRow.getStorageType())) {
            String snapshotKey = S3KeyTemplates.permanentVersion(task.getDocId(), targetRow.getVersionNumber());
            log.debug("Target version is a snapshot, fetching directly: key={}", snapshotKey);
            try {
                resultBytes = s3.fetchBytes(snapshotKey);
            } catch (NoSuchKeyException e) {
                log.error("Snapshot not found in S3: key={}", snapshotKey, e);
                recordFailure(task, FailureReason.SOURCE_NOT_FOUND);
                return;
            } catch (Exception e) {
                log.error("Failed to fetch snapshot from S3: key={}", snapshotKey, e);
                recordFailure(task, FailureReason.STORAGE_ERROR);
                return;
            }
        } else {
            try {
                resultBytes = reconstructor.reconstruct(task.getDocId(), task.getTargetVersionNumber(), task.getExpectedChecksum());
            } catch (ReconstructionException e) {
                log.error("Reconstruction failed for docId={}, targetVersion={}: {}",
                        task.getDocId(), task.getTargetVersionNumber(), e.getMessage());
                recordFailure(task, FailureReason.RECONSTRUCTION_FAILED);
                return;
            }
        }

        // 7. Upload to a temporary S3 key and generate a presigned GET URL
        String tempKey = S3KeyTemplates.tempReconstruction(task.getDocId(), task.getTargetVersionNumber());
        try {
            s3.uploadBytes(tempKey, resultBytes);
        } catch (Exception e) {
            log.error("Failed to upload reconstructed document: key={}", tempKey, e);
            recordFailure(task, FailureReason.STORAGE_ERROR);
            return;
        }

        String presignedUrl;
        try {
            presignedUrl = s3.generatePresignedDownloadUrl(tempKey);
        } catch (Exception e) {
            log.error("Failed to generate presigned URL: key={}", tempKey, e);
            recordFailure(task, FailureReason.STORAGE_ERROR);
            return;
        }

        // 8. Insert notification with presigned download URL
        notificationWriteGateway.recordReconstructionSuccess(
                task.getRecipientId(), task.getDocId(), task.getVersionId(), presignedUrl);

        log.info("Reconstruct task succeeded: docId={}, versionId={}, tempKey={}",
                task.getDocId(), task.getVersionId(), tempKey);
    }


    // ── helpers ──────────────────────────────────────────────────────────────


    private void recordFailure(ReconstructTaskMessage task, FailureReason reason) {
        notificationWriteGateway.recordFailure(
                task.getRecipientId(), task.getDocId(), task.getVersionId(),
                task.getTaskType().name(), reason);
    }
}
