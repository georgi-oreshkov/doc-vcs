package com.root.vcsbackendworker.reconstruct.application;

import com.root.vcsbackendworker.shared.db.VersionQueryGateway;
import com.root.vcsbackendworker.shared.db.VersionRow;
import com.root.vcsbackendworker.shared.diff.DiffApplicationException;
import com.root.vcsbackendworker.shared.diff.DiffApplicator;
import com.root.vcsbackendworker.shared.messaging.WorkerResultPublisher;
import com.root.vcsbackendworker.shared.messaging.inbound.ReconstructTaskMessage;
import com.root.vcsbackendworker.shared.messaging.outbound.FailureReason;
import com.root.vcsbackendworker.shared.messaging.outbound.ProcessingStatus;
import com.root.vcsbackendworker.shared.messaging.outbound.ReconstructionResultMessage;
import com.root.vcsbackendworker.shared.s3.S3DocumentStorage;
import com.root.vcsbackendworker.verify.domain.ChecksumVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconstructDocumentUseCase {


    private final VersionQueryGateway versionQueryGateway;
    private final S3DocumentStorage s3;
    private final DiffApplicator diffApplicator;
    private final ChecksumVerifier checksumVerifier;
    private final WorkerResultPublisher resultPublisher;

    public void handle(ReconstructTaskMessage task) {
        log.info("Reconstruct task started: docId={}, versionId={}, targetVersion={}",
                task.getDocId(), task.getVersionId(), task.getTargetVersionNumber());

        // 1. Resolve the target version row from the database
        Optional<VersionRow> targetRowOpt = versionQueryGateway.findById(task.getVersionId());
        if (targetRowOpt.isEmpty()) {
            log.error("Target version not found in DB: versionId={}", task.getVersionId());
            resultPublisher.publish(failureResult(task, FailureReason.SOURCE_NOT_FOUND));
            return;
        }
        VersionRow targetRow = targetRowOpt.get();

        // 2. If the target itself is a snapshot, fetch it directly — no diff chain needed
        byte[] resultBytes;
        if ("SNAPSHOT".equals(targetRow.getStorageType())) {
            log.debug("Target version is a snapshot, fetching directly: key={}", targetRow.getS3Key());
            try {
                resultBytes = s3.fetchBytes(targetRow.getS3Key());
            } catch (NoSuchKeyException e) {
                log.error("Snapshot not found in S3: key={}", targetRow.getS3Key(), e);
                resultPublisher.publish(failureResult(task, FailureReason.SOURCE_NOT_FOUND));
                return;
            } catch (Exception e) {
                log.error("Failed to fetch snapshot from S3: key={}", targetRow.getS3Key(), e);
                resultPublisher.publish(failureResult(task, FailureReason.STORAGE_ERROR));
                return;
            }
        } else {
            // 3. Find the latest snapshot preceding the target version
            Optional<VersionRow> snapshotOpt = versionQueryGateway
                    .findLastSnapshotBefore(task.getDocId(), task.getTargetVersionNumber());
            if (snapshotOpt.isEmpty()) {
                log.error("No snapshot found before targetVersion={} for docId={}", task.getTargetVersionNumber(), task.getDocId());
                resultPublisher.publish(failureResult(task, FailureReason.SOURCE_NOT_FOUND));
                return;
            }
            VersionRow snapshot = snapshotOpt.get();

            // 4. Fetch the snapshot bytes as the starting point
            byte[] current;
            try {
                current = s3.fetchBytes(snapshot.getS3Key());
            } catch (NoSuchKeyException e) {
                log.error("Snapshot not found in S3: key={}", snapshot.getS3Key(), e);
                resultPublisher.publish(failureResult(task, FailureReason.SOURCE_NOT_FOUND));
                return;
            } catch (Exception e) {
                log.error("Failed to fetch snapshot from S3: key={}", snapshot.getS3Key(), e);
                resultPublisher.publish(failureResult(task, FailureReason.STORAGE_ERROR));
                return;
            }

            // 5. Fetch and apply each diff in order, discarding intermediate bytes as we go
            List<VersionRow> diffs = versionQueryGateway.findDiffVersionsBetween(
                    task.getDocId(), snapshot.getVersionNumber(), task.getTargetVersionNumber());

            log.debug("Applying {} diffs from snapshot v{} to target v{}",
                    diffs.size(), snapshot.getVersionNumber(), task.getTargetVersionNumber());

            for (VersionRow diff : diffs) {
                byte[] diffBytes;
                try {
                    diffBytes = s3.fetchBytes(diff.getS3Key());
                } catch (NoSuchKeyException e) {
                    log.error("Diff not found in S3: key={}, versionNumber={}", diff.getS3Key(), diff.getVersionNumber(), e);
                    resultPublisher.publish(failureResult(task, FailureReason.SOURCE_NOT_FOUND));
                    return;
                } catch (Exception e) {
                    log.error("Failed to fetch diff from S3: key={}", diff.getS3Key(), e);
                    resultPublisher.publish(failureResult(task, FailureReason.STORAGE_ERROR));
                    return;
                }

                try {
                    current = diffApplicator.apply(current, diffBytes);
                } catch (DiffApplicationException e) {
                    log.error("Failed to apply diff v{} for docId={}", diff.getVersionNumber(), task.getDocId(), e);
                    resultPublisher.publish(failureResult(task, FailureReason.DIFF_APPLY_FAILED));
                    return;
                }

                log.debug("Applied diff v{}", diff.getVersionNumber());
            }

            resultBytes = current;
        }

        // 6. Verify checksum — compute once, compare directly (avoids hashing twice)
        String actualChecksum = checksumVerifier.sha256Hex(resultBytes);
        if (!actualChecksum.equalsIgnoreCase(task.getExpectedChecksum())) {
            log.warn("Checksum mismatch after reconstruction: docId={}, expected={}, actual={}",
                    task.getDocId(), task.getExpectedChecksum(), actualChecksum);
            resultPublisher.publish(failureResult(task, FailureReason.CHECKSUM_MISMATCH));
            return;
        }

        // 7. Upload to a temporary S3 key and generate a presigned GET URL
        String tempKey = toTempKey(task.getDocId().toString(), task.getTargetVersionNumber());
        try {
            s3.uploadBytes(tempKey, resultBytes);
        } catch (Exception e) {
            log.error("Failed to upload reconstructed document: key={}", tempKey, e);
            resultPublisher.publish(failureResult(task, FailureReason.STORAGE_ERROR));
            return;
        }

        String presignedUrl;
        try {
            presignedUrl = s3.generatePresignedDownloadUrl(tempKey);
        } catch (Exception e) {
            log.error("Failed to generate presigned URL: key={}", tempKey, e);
            resultPublisher.publish(failureResult(task, FailureReason.STORAGE_ERROR));
            return;
        }

        log.info("Reconstruct task succeeded: docId={}, versionId={}, tempKey={}",
                task.getDocId(), task.getVersionId(), tempKey);
        resultPublisher.publish(successResult(task, presignedUrl));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Temporary key for the assembled document, kept separate from permanent version keys.
     * e.g. {@code tmp/{docId}/v4}
     */
    private String toTempKey(String docId, int versionNumber) {
        return "tmp/" + docId + "/v" + versionNumber;
    }

    private ReconstructionResultMessage successResult(ReconstructTaskMessage task, String presignedUrl) {
        return ReconstructionResultMessage.builder()
                .metadata(resultPublisher.buildMetadata(task))
                .docId(task.getDocId())
                .versionId(task.getVersionId())
                .status(ProcessingStatus.SUCCEEDED)
                .presignedDownloadUrl(presignedUrl)
                .build();
    }

    private ReconstructionResultMessage failureResult(ReconstructTaskMessage task, FailureReason reason) {
        return ReconstructionResultMessage.builder()
                .metadata(resultPublisher.buildMetadata(task))
                .docId(task.getDocId())
                .versionId(task.getVersionId())
                .status(ProcessingStatus.FAILED)
                .failureReason(reason)
                .build();
    }
}


