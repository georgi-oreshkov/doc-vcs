package com.root.vcsbackendworker.verifyTask.application;

import com.root.vcsbackendworker.shared.db.NotificationWriteGateway;
import com.root.vcsbackendworker.shared.db.VersionQueryGateway;
import com.root.vcsbackendworker.shared.db.VersionRow;
import com.root.vcsbackendworker.shared.diff.DiffApplicationException;
import com.root.vcsbackendworker.shared.diff.DiffApplicator;
import com.root.vcsbackendworker.shared.messaging.FailureReason;
import com.root.vcsbackendworker.shared.messaging.inbound.VerifyTaskMessage;
import com.root.vcsbackendworker.shared.reconstruct.ReconstructionException;
import com.root.vcsbackendworker.shared.reconstruct.Reconstructor;
import com.root.vcsbackendworker.shared.s3.S3DocumentStorage;
import com.root.vcsbackendworker.shared.s3.S3KeyTemplates;
import com.root.vcsbackendworker.shared.verify.ChecksumVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyDiffUseCase {

    private final S3DocumentStorage s3;
    private final DiffApplicator diffApplicator;
    private final ChecksumVerifier checksumVerifier;
    private final NotificationWriteGateway notificationWriteGateway;
    private final VersionQueryGateway versionQueryGateway;
    private final Reconstructor reconstructor;

    public void handle(VerifyTaskMessage task) {
        log.info("Verify task started: docId={}, version={}", task.getDocId(), task.getNewVersionNumber());

        // 0. Fetch the latest version and run reconstruction if it is a diff (ensures the base version is in S3)
        byte[] baseBytes;
        Optional<VersionRow> lastVersionOpt = versionQueryGateway.findByDocIdAndVersionNumber(task.getDocId(),task.getNewVersionNumber()-1);
        if (lastVersionOpt.isEmpty()){
            log.error("Target version not found in DB: docId={} version={}", task.getDocId(), task.getNewVersionNumber()-1);
            recordFailure(task, FailureReason.SOURCE_NOT_FOUND);
            return;
        }
        VersionRow lastVersionRow = lastVersionOpt.get();
        if ("DIFF".equals(lastVersionRow.getStorageType())) {
            log.debug("Target version is a diff, running reconstruction first");
            try {
                baseBytes =  reconstructor.reconstruct(task.getDocId(), task.getNewVersionNumber()-1, lastVersionRow.getChecksum());
            }catch (ReconstructionException e) {
                log.error("Reconstruction failed for doc id={} version={}", task.getDocId(), task.getNewVersionNumber()-1, e);
                recordFailure(task, FailureReason.RECONSTRUCTION_FAILED);
                return;
            }
        } else {
            String lastVersionKey = S3KeyTemplates.permanentVersion(task.getDocId(), task.getNewVersionNumber()-1);
            try {
                baseBytes = s3.fetchBytes(lastVersionKey);
            } catch (NoSuchKeyException e) {
                log.error("Base version not found in S3: key={}", lastVersionKey, e);
                recordFailure(task, FailureReason.SOURCE_NOT_FOUND);
                return;
            } catch (Exception e) {
                log.error("Failed to fetch base version from S3: key={}", lastVersionKey, e);
                recordFailure(task, FailureReason.STORAGE_ERROR);
                return;
            }
        }

        String stagingKey = S3KeyTemplates.stagingDiff(task.getDocId(),task.getNewVersionNumber());
        // 2. Fetch the diff bytes
        byte[] diffBytes;
        try {
            diffBytes = s3.fetchBytes(stagingKey);
        } catch (NoSuchKeyException e) {
            log.error("Diff not found in S3: key={}", stagingKey, e);
            recordFailure(task, FailureReason.SOURCE_NOT_FOUND);
            return;
        } catch (Exception e) {
            log.error("Failed to fetch diff from S3: key={}", stagingKey, e);
            recordFailure(task, FailureReason.STORAGE_ERROR);
            return;
        }

        // 3. Apply the diff
        byte[] resultBytes;
        try {
            resultBytes = diffApplicator.apply(baseBytes, diffBytes);
        } catch (DiffApplicationException e) {
            log.error("Diff application failed: docId={}, version={}", task.getDocId(), task.getNewVersionNumber(), e);
            recordFailure(task, FailureReason.DIFF_APPLY_FAILED);
            return;
        }

        // 4. Verify checksum
        if (!checksumVerifier.matches(resultBytes, task.getExpectedChecksum())) {
            log.warn("Checksum mismatch: docId={}, version={}, expected={}",
                    task.getDocId(), task.getNewVersionNumber(), task.getExpectedChecksum());
            recordFailure(task, FailureReason.CHECKSUM_MISMATCH);
            return;
        }

        // 5. Upload reconstruction to tmp and move diff to permanent S3 location
        String permanentKey = S3KeyTemplates.permanentVersion(task.getDocId(), task.getNewVersionNumber());
        try {
            s3.moveObject(stagingKey, permanentKey);
        } catch (Exception e) {
            log.error("Failed to move diff to S3: key={}", permanentKey, e);
            recordFailure(task, FailureReason.STORAGE_ERROR);
            return;
        }

        String reconstructKey = S3KeyTemplates.tempReconstruction(task.getDocId(), task.getNewVersionNumber());
        try {
            s3.uploadBytes(reconstructKey, resultBytes);
        }catch (Exception e) {
            log.error("Failed to upload reconstruction: key={}", reconstructKey, e);
        }

        // 7. Update version checksum + insert notification (single DB transaction)
        notificationWriteGateway.recordVerificationSuccess(
                task.getRecipientId(), task.getDocId(), task.getVersionId(), task.getExpectedChecksum(), task.getNewVersionNumber());

        log.info("Verify task succeeded: docId={}, version={}, permanentKey={}",
                task.getDocId(), task.getNewVersionNumber(), permanentKey);
    }

    // ── helpers ──────────────────────────────────────────────────────────────


    private void recordFailure(VerifyTaskMessage task, FailureReason reason) {
        notificationWriteGateway.recordFailure(
                task.getRecipientId(), task.getDocId(), task.getVersionId(),
                task.getTaskType().name(), reason);
    }
}
