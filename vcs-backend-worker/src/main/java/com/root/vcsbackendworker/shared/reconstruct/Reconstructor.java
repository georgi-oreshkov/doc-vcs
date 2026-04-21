package com.root.vcsbackendworker.shared.reconstruct;

import com.root.vcsbackendworker.shared.db.VersionQueryGateway;
import com.root.vcsbackendworker.shared.db.VersionRow;
import com.root.vcsbackendworker.shared.diff.DiffApplicationException;
import com.root.vcsbackendworker.shared.diff.DiffApplicator;
import com.root.vcsbackendworker.shared.s3.S3DocumentStorage;
import com.root.vcsbackendworker.shared.s3.S3KeyTemplates;
import com.root.vcsbackendworker.shared.verify.ChecksumVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class Reconstructor {

    private final VersionQueryGateway versionQueryGateway;
    private final S3DocumentStorage s3;
    private final DiffApplicator diffApplicator;
    private final ChecksumVerifier checksumVerifier;

    /**
     * Reconstructs a document from the chain of diffs.
     * <p>
     * @param docId UUID of the document being reconstructed
     * @param targetVersionNumber version number of the target version to reconstruct
     * @return raw bytes of the reconstructed document
     * @throws ReconstructionException if the patch cannot be applied cleanly
     */

    public byte[] reconstruct (UUID docId, Integer targetVersionNumber, String expectedChecksum){
        // 3. Find the latest snapshot preceding the target version
        Optional<VersionRow> snapshotOpt = versionQueryGateway
                .findLastSnapshotBefore(docId, targetVersionNumber);
        if (snapshotOpt.isEmpty()) {
            log.error("No snapshot found before targetVersion={} for docId={}", targetVersionNumber, docId);
            throw new ReconstructionException("No snapshot found in db", null);
        }
        VersionRow snapshot = snapshotOpt.get();

        // 4. Fetch the snapshot bytes as the starting point
        byte[] current;
        String snapshotKey = S3KeyTemplates.permanentVersion(docId, snapshot.getVersionNumber());
        try {
            current = s3.fetchBytes(snapshotKey);
        } catch (NoSuchKeyException e) {
            log.error("Snapshot not found in S3: key={}", snapshotKey, e);
            throw new ReconstructionException("Snapshot no found in s3",e);

        } catch (Exception e) {
            log.error("Failed to fetch snapshot from S3: key={}", snapshotKey, e);
            throw new ReconstructionException("Failed to fetch snapshot from S3",e);
        }

        // 5. Fetch and apply each diff in order, discarding intermediate bytes as we go
        List<VersionRow> diffs = versionQueryGateway.findDiffVersionsBetween(
                docId, snapshot.getVersionNumber(), targetVersionNumber);

        log.debug("Applying {} diffs from snapshot v{} to target v{}",
                diffs.size(), snapshot.getVersionNumber(), targetVersionNumber);

        for (VersionRow diff : diffs) {
            byte[] diffBytes;
            String diffKey = S3KeyTemplates.permanentVersion(docId, diff.getVersionNumber());
            try {
                diffBytes = s3.fetchBytes(diffKey);
            } catch (NoSuchKeyException e) {
                log.error("Diff not found in S3: key={}, versionNumber={}", diffKey, diff.getVersionNumber(), e);
                throw new ReconstructionException("Diff not found in S3",e);
            } catch (Exception e) {
                log.error("Failed to fetch diff from S3: key={}", diffKey, e);
                throw new ReconstructionException("Failed to fetch diff from S3",e);
            }

            try {
                current = diffApplicator.apply(current, diffBytes);
            } catch (DiffApplicationException e) {
                log.error("Failed to apply diff v{} for docId={}", diff.getVersionNumber(), docId, e);
                throw new ReconstructionException("Diff failed to apply",e);
            }

            log.debug("Applied diff v{}", diff.getVersionNumber());
        }
        if (!checksumVerifier.matches(current, expectedChecksum)) {
            log.warn("Checksum mismatch after reconstruction: docId={}, expected={}",
                    docId, expectedChecksum);
            throw new ReconstructionException("Checksum mismatch",null);
        }
        return current;
    }
}
