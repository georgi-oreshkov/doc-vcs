package com.root.vcsbackendworker.reconstructTask.application;

import com.root.vcsbackendworker.shared.db.NotificationWriteGateway;
import com.root.vcsbackendworker.shared.db.VersionQueryGateway;
import com.root.vcsbackendworker.shared.db.VersionRow;
import com.root.vcsbackendworker.shared.messaging.FailureReason;
import com.root.vcsbackendworker.shared.messaging.MessageMetadata;
import com.root.vcsbackendworker.shared.messaging.inbound.ReconstructTaskMessage;
import com.root.vcsbackendworker.shared.messaging.inbound.WorkerTaskType;
import com.root.vcsbackendworker.shared.reconstruct.ReconstructionException;
import com.root.vcsbackendworker.shared.reconstruct.Reconstructor;
import com.root.vcsbackendworker.shared.s3.S3DocumentStorage;
import com.root.vcsbackendworker.shared.s3.S3KeyTemplates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconstructDocumentUseCaseTest {

    @Mock VersionQueryGateway versionQueryGateway;
    @Mock S3DocumentStorage s3;
    @Mock NotificationWriteGateway notificationWriteGateway;
    @Mock Reconstructor reconstructor;
    @InjectMocks ReconstructDocumentUseCase useCase;

    static final UUID DOC_ID = UUID.randomUUID();
    static final UUID VERSION_ID = UUID.randomUUID();
    static final UUID RECIPIENT_ID = UUID.randomUUID();
    static final int TARGET_VERSION = 3;
    static final String CHECKSUM = "expectedchecksum";
    static final String PRESIGNED_URL = "https://minio/presigned";

    // Derived S3 keys via templates
    static final String TEMP_KEY = S3KeyTemplates.tempReconstruction(DOC_ID, TARGET_VERSION);
    static final String TARGET_SNAPSHOT_KEY = S3KeyTemplates.permanentVersion(DOC_ID, TARGET_VERSION);

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
                .metadata(MessageMetadata.builder().build())
                .build();
    }

    // ── DB lookup failure ─────────────────────────────────────────────────────

    @Test
    void handle_targetVersionNotInDb_recordsSourceNotFound() {
        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.empty());

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "RECONSTRUCT_DOCUMENT", FailureReason.SOURCE_NOT_FOUND);
        verifyNoInteractions(s3, reconstructor);
    }

    // ── target is SNAPSHOT ────────────────────────────────────────────────────

    @Test
    void handle_targetIsSnapshot_fetchesDirectlyAndRecordsSuccess() {
        VersionRow snapshot = snapshotRow(VERSION_ID, TARGET_VERSION);
        byte[] bytes = "snapshot content".getBytes();

        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.of(snapshot));
        when(s3.fetchBytes(TARGET_SNAPSHOT_KEY)).thenReturn(bytes);
        when(s3.generatePresignedDownloadUrl(TEMP_KEY)).thenReturn(PRESIGNED_URL);

        useCase.handle(validTask);

        verifyNoInteractions(reconstructor);
        verify(s3).uploadBytes(TEMP_KEY, bytes);
        verify(notificationWriteGateway).recordReconstructionSuccess(RECIPIENT_ID, DOC_ID, VERSION_ID, PRESIGNED_URL);
    }

    @Test
    void handle_targetIsSnapshot_uploadsToCorrectTempKey() {
        VersionRow snapshot = snapshotRow(VERSION_ID, TARGET_VERSION);
        byte[] bytes = "snapshot content".getBytes();

        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.of(snapshot));
        when(s3.fetchBytes(TARGET_SNAPSHOT_KEY)).thenReturn(bytes);
        when(s3.generatePresignedDownloadUrl(TEMP_KEY)).thenReturn(PRESIGNED_URL);

        useCase.handle(validTask);

        verify(s3).uploadBytes(eq(TEMP_KEY), eq(bytes));
    }

    @Test
    void handle_targetIsSnapshot_s3NotFound_recordsSourceNotFound() {
        VersionRow snapshot = snapshotRow(VERSION_ID, TARGET_VERSION);

        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.of(snapshot));
        when(s3.fetchBytes(TARGET_SNAPSHOT_KEY)).thenThrow(NoSuchKeyException.builder().build());

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "RECONSTRUCT_DOCUMENT", FailureReason.SOURCE_NOT_FOUND);
    }

    @Test
    void handle_targetIsSnapshot_s3GenericError_recordsStorageError() {
        VersionRow snapshot = snapshotRow(VERSION_ID, TARGET_VERSION);

        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.of(snapshot));
        when(s3.fetchBytes(TARGET_SNAPSHOT_KEY)).thenThrow(new RuntimeException("connection reset"));

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "RECONSTRUCT_DOCUMENT", FailureReason.STORAGE_ERROR);
    }

    // ── target is DIFF — delegates to Reconstructor ───────────────────────────

    @Test
    void handle_targetIsDiff_delegatesToReconstructorAndRecordsSuccess() {
        VersionRow target = diffRow(VERSION_ID, TARGET_VERSION);
        byte[] resultBytes = "reconstructed".getBytes();

        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.of(target));
        when(reconstructor.reconstruct(DOC_ID, TARGET_VERSION, CHECKSUM)).thenReturn(resultBytes);
        when(s3.generatePresignedDownloadUrl(TEMP_KEY)).thenReturn(PRESIGNED_URL);

        useCase.handle(validTask);

        verify(reconstructor).reconstruct(DOC_ID, TARGET_VERSION, CHECKSUM);
        verify(s3).uploadBytes(TEMP_KEY, resultBytes);
        verify(notificationWriteGateway).recordReconstructionSuccess(RECIPIENT_ID, DOC_ID, VERSION_ID, PRESIGNED_URL);
    }

    @Test
    void handle_targetIsDiff_reconstructionFails_recordsReconstructionFailed() {
        VersionRow target = diffRow(VERSION_ID, TARGET_VERSION);

        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.of(target));
        when(reconstructor.reconstruct(DOC_ID, TARGET_VERSION, CHECKSUM))
                .thenThrow(new ReconstructionException("no snapshot", null));

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "RECONSTRUCT_DOCUMENT", FailureReason.RECONSTRUCTION_FAILED);
        verify(s3, never()).uploadBytes(any(), any());
    }

    // ── upload and presign failures ───────────────────────────────────────────

    @Test
    void handle_uploadFails_recordsStorageError() {
        VersionRow snapshot = snapshotRow(VERSION_ID, TARGET_VERSION);
        byte[] bytes = "content".getBytes();

        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.of(snapshot));
        when(s3.fetchBytes(TARGET_SNAPSHOT_KEY)).thenReturn(bytes);
        doThrow(new RuntimeException("upload failed")).when(s3).uploadBytes(any(), any());

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "RECONSTRUCT_DOCUMENT", FailureReason.STORAGE_ERROR);
    }

    @Test
    void handle_presignFails_recordsStorageError() {
        VersionRow snapshot = snapshotRow(VERSION_ID, TARGET_VERSION);
        byte[] bytes = "content".getBytes();

        when(versionQueryGateway.findById(VERSION_ID)).thenReturn(Optional.of(snapshot));
        when(s3.fetchBytes(TARGET_SNAPSHOT_KEY)).thenReturn(bytes);
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

    private VersionRow snapshotRow(UUID id, int versionNumber) {
        return VersionRow.builder()
                .id(id).docId(DOC_ID)
                .versionNumber(versionNumber)
                .storageType("SNAPSHOT")
                .build();
    }

    private VersionRow diffRow(UUID id, int versionNumber) {
        return VersionRow.builder()
                .id(id).docId(DOC_ID)
                .versionNumber(versionNumber)
                .storageType("DIFF")
                .build();
    }
}

