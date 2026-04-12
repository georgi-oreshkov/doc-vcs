package com.root.vcsbackendworker.reconstructTask.application;

import com.root.vcsbackendworker.shared.db.NotificationWriteGateway;
import com.root.vcsbackendworker.shared.db.VersionQueryGateway;
import com.root.vcsbackendworker.shared.db.VersionRow;
import com.root.vcsbackendworker.shared.diff.DiffApplicationException;
import com.root.vcsbackendworker.shared.diff.DiffApplicator;
import com.root.vcsbackendworker.shared.messaging.FailureReason;
import com.root.vcsbackendworker.shared.messaging.MessageMetadata;
import com.root.vcsbackendworker.shared.messaging.inbound.ReconstructTaskMessage;
import com.root.vcsbackendworker.shared.messaging.inbound.WorkerTaskType;
import com.root.vcsbackendworker.shared.s3.S3DocumentStorage;
import com.root.vcsbackendworker.shared.verify.ChecksumVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconstructDocumentUseCaseTest {

    @Mock VersionQueryGateway versionQueryGateway;
    @Mock S3DocumentStorage s3;
    @Mock DiffApplicator diffApplicator;
    @Mock ChecksumVerifier checksumVerifier;
    @Mock NotificationWriteGateway notificationWriteGateway;
    @InjectMocks ReconstructDocumentUseCase useCase;

    static final UUID DOC_ID = UUID.randomUUID();
    static final UUID VERSION_ID = UUID.randomUUID();
    static final UUID RECIPIENT_ID = UUID.randomUUID();
    static final int TARGET_VERSION = 3;
    static final String CHECKSUM = "expectedchecksum";
    static final String PRESIGNED_URL = "https://minio/presigned";

    ReconstructTaskMessage validTask;

    @BeforeEach
    void setUp() {
        validTask = ReconstructTaskMessage.builder()
                .docId(DOC_ID)
                .versionId(VERSION_ID)
                .recipientId(RECIPIENT_ID)
                .taskType(WorkerTaskType.RECONSTRUCT_DOCUMENT)
                .targetVersionNumber(TARGET_VERSION)
                .expectedChecksum(CHECKSUM)
                .metadata(MessageMetadata.builder().correlationId(UUID.randomUUID()).build())
                .build();
    }

    // ── DB lookup failure ─────────────────────────────────────────────────────

    @Test
    void handle_targetVersionNotInDb_recordsSourceNotFound() {
        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.empty());

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "RECONSTRUCT_DOCUMENT", FailureReason.SOURCE_NOT_FOUND);
        verifyNoInteractions(s3, diffApplicator);
    }

    // ── target is SNAPSHOT ────────────────────────────────────────────────────

    @Test
    void handle_targetIsSnapshot_fetchesDirectlyAndRecordsSuccess() {
        VersionRow snapshot = snapshotRow(VERSION_ID, TARGET_VERSION, "documents/" + DOC_ID + "/v3");
        byte[] bytes = "snapshot content".getBytes();

        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.of(snapshot));
        when(s3.fetchBytes(snapshot.getS3Key())).thenReturn(bytes);
        when(checksumVerifier.sha256Hex(bytes)).thenReturn(CHECKSUM);
        when(s3.generatePresignedDownloadUrl(any())).thenReturn(PRESIGNED_URL);

        useCase.handle(validTask);

        verifyNoInteractions(diffApplicator);
        verify(notificationWriteGateway).recordReconstructionSuccess(RECIPIENT_ID, DOC_ID, VERSION_ID, PRESIGNED_URL);
    }

    @Test
    void handle_targetIsSnapshot_uploadsToCorrectTempKey() {
        VersionRow snapshot = snapshotRow(VERSION_ID, TARGET_VERSION, "documents/" + DOC_ID + "/v3");
        byte[] bytes = "snapshot content".getBytes();
        String expectedTempKey = "tmp/" + DOC_ID + "/v" + TARGET_VERSION;

        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.of(snapshot));
        when(s3.fetchBytes(snapshot.getS3Key())).thenReturn(bytes);
        when(checksumVerifier.sha256Hex(bytes)).thenReturn(CHECKSUM);
        when(s3.generatePresignedDownloadUrl(expectedTempKey)).thenReturn(PRESIGNED_URL);

        useCase.handle(validTask);

        verify(s3).uploadBytes(eq(expectedTempKey), eq(bytes));
    }

    @Test
    void handle_targetIsSnapshot_s3NotFound_recordsSourceNotFound() {
        VersionRow snapshot = snapshotRow(VERSION_ID, TARGET_VERSION, "documents/" + DOC_ID + "/v3");

        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.of(snapshot));
        when(s3.fetchBytes(snapshot.getS3Key())).thenThrow(NoSuchKeyException.builder().build());

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "RECONSTRUCT_DOCUMENT", FailureReason.SOURCE_NOT_FOUND);
    }

    @Test
    void handle_targetIsSnapshot_s3GenericError_recordsStorageError() {
        VersionRow snapshot = snapshotRow(VERSION_ID, TARGET_VERSION, "documents/" + DOC_ID + "/v3");

        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.of(snapshot));
        when(s3.fetchBytes(snapshot.getS3Key())).thenThrow(new RuntimeException("connection reset"));

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "RECONSTRUCT_DOCUMENT", FailureReason.STORAGE_ERROR);
    }

    // ── target is DIFF — chain happy path ────────────────────────────────────

    @Test
    void handle_singleDiff_appliesAndRecordsSuccess() {
        VersionRow target = diffRow(VERSION_ID, TARGET_VERSION, "documents/" + DOC_ID + "/v3.diff");
        VersionRow base = snapshotRow(UUID.randomUUID(), 2, "documents/" + DOC_ID + "/v2");
        VersionRow diff = diffRow(UUID.randomUUID(), TARGET_VERSION, "documents/" + DOC_ID + "/v3.diff");

        byte[] baseBytes = "base".getBytes();
        byte[] diffBytes = "diff".getBytes();
        byte[] resultBytes = "result".getBytes();

        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.of(target));
        when(versionQueryGateway.findLastSnapshotBefore(DOC_ID, TARGET_VERSION)).thenReturn(Optional.of(base));
        when(versionQueryGateway.findDiffVersionsBetween(DOC_ID, 2, TARGET_VERSION)).thenReturn(List.of(diff));
        when(s3.fetchBytes(base.getS3Key())).thenReturn(baseBytes);
        when(s3.fetchBytes(diff.getS3Key())).thenReturn(diffBytes);
        when(diffApplicator.apply(baseBytes, diffBytes)).thenReturn(resultBytes);
        when(checksumVerifier.sha256Hex(resultBytes)).thenReturn(CHECKSUM);
        when(s3.generatePresignedDownloadUrl(any())).thenReturn(PRESIGNED_URL);

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordReconstructionSuccess(RECIPIENT_ID, DOC_ID, VERSION_ID, PRESIGNED_URL);
    }

    @Test
    void handle_multipleDiffs_appliedInAscendingOrder() {
        VersionRow target = diffRow(VERSION_ID, 4, "documents/" + DOC_ID + "/v4.diff");
        VersionRow base = snapshotRow(UUID.randomUUID(), 1, "documents/" + DOC_ID + "/v1");
        VersionRow diff2 = diffRow(UUID.randomUUID(), 2, "documents/" + DOC_ID + "/v2.diff");
        VersionRow diff3 = diffRow(UUID.randomUUID(), 3, "documents/" + DOC_ID + "/v3.diff");
        VersionRow diff4 = diffRow(UUID.randomUUID(), 4, "documents/" + DOC_ID + "/v4.diff");

        byte[] snapBytes = "snap".getBytes();
        byte[] diff2Bytes = "d2".getBytes();
        byte[] diff3Bytes = "d3".getBytes();
        byte[] diff4Bytes = "d4".getBytes();
        byte[] after2 = "after2".getBytes();
        byte[] after3 = "after3".getBytes();
        byte[] finalBytes = "final".getBytes();

        ReconstructTaskMessage task4 = ReconstructTaskMessage.builder()
                .docId(DOC_ID).versionId(VERSION_ID).targetVersionNumber(4)
                .recipientId(RECIPIENT_ID)
                .taskType(WorkerTaskType.RECONSTRUCT_DOCUMENT)
                .expectedChecksum(CHECKSUM)
                .metadata(MessageMetadata.builder().build())
                .build();

        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.of(target));
        when(versionQueryGateway.findLastSnapshotBefore(DOC_ID, 4)).thenReturn(Optional.of(base));
        when(versionQueryGateway.findDiffVersionsBetween(DOC_ID, 1, 4)).thenReturn(List.of(diff2, diff3, diff4));
        when(s3.fetchBytes(base.getS3Key())).thenReturn(snapBytes);
        when(s3.fetchBytes(diff2.getS3Key())).thenReturn(diff2Bytes);
        when(s3.fetchBytes(diff3.getS3Key())).thenReturn(diff3Bytes);
        when(s3.fetchBytes(diff4.getS3Key())).thenReturn(diff4Bytes);
        when(diffApplicator.apply(snapBytes, diff2Bytes)).thenReturn(after2);
        when(diffApplicator.apply(after2, diff3Bytes)).thenReturn(after3);
        when(diffApplicator.apply(after3, diff4Bytes)).thenReturn(finalBytes);
        when(checksumVerifier.sha256Hex(finalBytes)).thenReturn(CHECKSUM);
        when(s3.generatePresignedDownloadUrl(any())).thenReturn(PRESIGNED_URL);

        useCase.handle(task4);

        InOrder inOrder = inOrder(diffApplicator);
        inOrder.verify(diffApplicator).apply(snapBytes, diff2Bytes);
        inOrder.verify(diffApplicator).apply(after2, diff3Bytes);
        inOrder.verify(diffApplicator).apply(after3, diff4Bytes);
    }

    // ── target is DIFF — failure paths ───────────────────────────────────────

    @Test
    void handle_noSnapshotFound_recordsSourceNotFound() {
        VersionRow target = diffRow(VERSION_ID, TARGET_VERSION, "documents/" + DOC_ID + "/v3.diff");

        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.of(target));
        when(versionQueryGateway.findLastSnapshotBefore(DOC_ID, TARGET_VERSION)).thenReturn(Optional.empty());

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "RECONSTRUCT_DOCUMENT", FailureReason.SOURCE_NOT_FOUND);
        verifyNoInteractions(s3, diffApplicator);
    }

    @Test
    void handle_snapshotFetchFails_recordsStorageError() {
        VersionRow target = diffRow(VERSION_ID, TARGET_VERSION, "documents/" + DOC_ID + "/v3.diff");
        VersionRow base = snapshotRow(UUID.randomUUID(), 1, "documents/" + DOC_ID + "/v1");

        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.of(target));
        when(versionQueryGateway.findLastSnapshotBefore(DOC_ID, TARGET_VERSION)).thenReturn(Optional.of(base));
        when(s3.fetchBytes(base.getS3Key())).thenThrow(new RuntimeException("S3 down"));

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "RECONSTRUCT_DOCUMENT", FailureReason.STORAGE_ERROR);
    }

    @Test
    void handle_diffFetchFailsMidChain_stopsAndRecordsSourceNotFound() {
        VersionRow target = diffRow(VERSION_ID, TARGET_VERSION, "documents/" + DOC_ID + "/v3.diff");
        VersionRow base = snapshotRow(UUID.randomUUID(), 1, "documents/" + DOC_ID + "/v1");
        VersionRow diff2 = diffRow(UUID.randomUUID(), 2, "documents/" + DOC_ID + "/v2.diff");
        VersionRow diff3 = diffRow(UUID.randomUUID(), TARGET_VERSION, "documents/" + DOC_ID + "/v3.diff");

        byte[] snapBytes = "snap".getBytes();
        byte[] diff2Bytes = "d2".getBytes();

        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.of(target));
        when(versionQueryGateway.findLastSnapshotBefore(DOC_ID, TARGET_VERSION)).thenReturn(Optional.of(base));
        when(versionQueryGateway.findDiffVersionsBetween(DOC_ID, 1, TARGET_VERSION)).thenReturn(List.of(diff2, diff3));
        when(s3.fetchBytes(base.getS3Key())).thenReturn(snapBytes);
        when(s3.fetchBytes(diff2.getS3Key())).thenReturn(diff2Bytes);
        when(s3.fetchBytes(diff3.getS3Key())).thenThrow(NoSuchKeyException.builder().build());

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "RECONSTRUCT_DOCUMENT", FailureReason.SOURCE_NOT_FOUND);
    }

    @Test
    void handle_diffApplicationFailsMidChain_stopsAndRecordsDiffApplyFailed() {
        VersionRow target = diffRow(VERSION_ID, TARGET_VERSION, "documents/" + DOC_ID + "/v3.diff");
        VersionRow base = snapshotRow(UUID.randomUUID(), 1, "documents/" + DOC_ID + "/v1");
        VersionRow diff = diffRow(UUID.randomUUID(), TARGET_VERSION, "documents/" + DOC_ID + "/v3.diff");

        byte[] snapBytes = "snap".getBytes();
        byte[] diffBytes = "diff".getBytes();

        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.of(target));
        when(versionQueryGateway.findLastSnapshotBefore(DOC_ID, TARGET_VERSION)).thenReturn(Optional.of(base));
        when(versionQueryGateway.findDiffVersionsBetween(DOC_ID, 1, TARGET_VERSION)).thenReturn(List.of(diff));
        when(s3.fetchBytes(base.getS3Key())).thenReturn(snapBytes);
        when(s3.fetchBytes(diff.getS3Key())).thenReturn(diffBytes);
        when(diffApplicator.apply(snapBytes, diffBytes))
                .thenThrow(new DiffApplicationException("corrupt", new RuntimeException()));

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "RECONSTRUCT_DOCUMENT", FailureReason.DIFF_APPLY_FAILED);
        verify(s3, never()).uploadBytes(any(), any());
    }

    // ── checksum and upload ───────────────────────────────────────────────────

    @Test
    void handle_checksumMismatch_recordsChecksumMismatch() {
        VersionRow snapshot = snapshotRow(VERSION_ID, TARGET_VERSION, "documents/" + DOC_ID + "/v3");
        byte[] bytes = "content".getBytes();

        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.of(snapshot));
        when(s3.fetchBytes(snapshot.getS3Key())).thenReturn(bytes);
        when(checksumVerifier.sha256Hex(bytes)).thenReturn("actuallydifferent");

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "RECONSTRUCT_DOCUMENT", FailureReason.CHECKSUM_MISMATCH);
        verify(s3, never()).uploadBytes(any(), any());
    }

    @Test
    void handle_uploadFails_recordsStorageError() {
        VersionRow snapshot = snapshotRow(VERSION_ID, TARGET_VERSION, "documents/" + DOC_ID + "/v3");
        byte[] bytes = "content".getBytes();

        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.of(snapshot));
        when(s3.fetchBytes(snapshot.getS3Key())).thenReturn(bytes);
        when(checksumVerifier.sha256Hex(bytes)).thenReturn(CHECKSUM);
        doThrow(new RuntimeException("upload failed")).when(s3).uploadBytes(any(), any());

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "RECONSTRUCT_DOCUMENT", FailureReason.STORAGE_ERROR);
    }

    @Test
    void handle_presignFails_recordsStorageError() {
        VersionRow snapshot = snapshotRow(VERSION_ID, TARGET_VERSION, "documents/" + DOC_ID + "/v3");
        byte[] bytes = "content".getBytes();

        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.of(snapshot));
        when(s3.fetchBytes(snapshot.getS3Key())).thenReturn(bytes);
        when(checksumVerifier.sha256Hex(bytes)).thenReturn(CHECKSUM);
        when(s3.generatePresignedDownloadUrl(any())).thenThrow(new RuntimeException("presign failed"));

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "RECONSTRUCT_DOCUMENT", FailureReason.STORAGE_ERROR);
    }

    // ── early return guarantee ────────────────────────────────────────────────

    @Test
    void handle_anyFailure_recordsExactlyOnce() {
        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.empty());

        useCase.handle(validTask);

        verify(notificationWriteGateway, times(1)).recordFailure(any(), any(), any(), any(), any());
        verify(notificationWriteGateway, never()).recordReconstructionSuccess(any(), any(), any(), any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private VersionRow snapshotRow(UUID id, int versionNumber, String s3Key) {
        return VersionRow.builder()
                .id(id).docId(DOC_ID)
                .versionNumber(versionNumber)
                .storageType("SNAPSHOT")
                .s3Key(s3Key)
                .build();
    }

    private VersionRow diffRow(UUID id, int versionNumber, String s3Key) {
        return VersionRow.builder()
                .id(id).docId(DOC_ID)
                .versionNumber(versionNumber)
                .storageType("DIFF")
                .s3Key(s3Key)
                .build();
    }
}

