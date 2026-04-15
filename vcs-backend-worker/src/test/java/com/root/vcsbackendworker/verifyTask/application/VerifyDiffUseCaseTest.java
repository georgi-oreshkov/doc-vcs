package com.root.vcsbackendworker.verifyTask.application;

import com.root.vcsbackendworker.shared.db.NotificationWriteGateway;
import com.root.vcsbackendworker.shared.db.VersionQueryGateway;
import com.root.vcsbackendworker.shared.db.VersionRow;
import com.root.vcsbackendworker.shared.diff.DiffApplicationException;
import com.root.vcsbackendworker.shared.diff.DiffApplicator;
import com.root.vcsbackendworker.shared.messaging.FailureReason;
import com.root.vcsbackendworker.shared.messaging.MessageMetadata;
import com.root.vcsbackendworker.shared.messaging.inbound.VerifyTaskMessage;
import com.root.vcsbackendworker.shared.messaging.inbound.WorkerTaskType;
import com.root.vcsbackendworker.shared.reconstruct.ReconstructionException;
import com.root.vcsbackendworker.shared.reconstruct.Reconstructor;
import com.root.vcsbackendworker.shared.s3.S3DocumentStorage;
import com.root.vcsbackendworker.shared.s3.S3KeyTemplates;
import com.root.vcsbackendworker.shared.verify.ChecksumVerifier;
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
class VerifyDiffUseCaseTest {

    @Mock S3DocumentStorage s3;
    @Mock DiffApplicator diffApplicator;
    @Mock ChecksumVerifier checksumVerifier;
    @Mock NotificationWriteGateway notificationWriteGateway;
    @Mock VersionQueryGateway versionQueryGateway;
    @Mock Reconstructor reconstructor;
    @InjectMocks VerifyDiffUseCase useCase;

    static final UUID DOC_ID = UUID.randomUUID();
    static final UUID VERSION_ID = UUID.randomUUID();
    static final UUID RECIPIENT_ID = UUID.randomUUID();
    static final int NEW_VERSION = 2;
    static final int PREV_VERSION = NEW_VERSION - 1;

    // Derived S3 keys via templates
    static final String PREV_PERMANENT_KEY = S3KeyTemplates.permanentVersion(DOC_ID, PREV_VERSION);
    static final String STAGING_KEY = S3KeyTemplates.stagingDiff(DOC_ID, NEW_VERSION);
    static final String PERMANENT_KEY = S3KeyTemplates.permanentVersion(DOC_ID, NEW_VERSION);
    static final String RECONSTRUCT_KEY = S3KeyTemplates.tempReconstruction(DOC_ID, NEW_VERSION);

    static final byte[] BASE_BYTES = "base content".getBytes();
    static final byte[] DIFF_BYTES = "diff content".getBytes();
    static final byte[] RESULT_BYTES = "result content".getBytes();
    static final String CHECKSUM = "abc123checksum";
    static final String PREV_CHECKSUM = "prev_checksum";

    VerifyTaskMessage validTask;

    @BeforeEach
    void setUp() {
        validTask = VerifyTaskMessage.builder()
                .docId(DOC_ID)
                .versionId(VERSION_ID)
                .recipientId(RECIPIENT_ID)
                .taskType(WorkerTaskType.VERIFY_DIFF)
                .newVersionNumber(NEW_VERSION)
                .expectedChecksum(CHECKSUM)
                .metadata(MessageMetadata.builder().build())
                .build();
    }

    // ── happy path — previous version is SNAPSHOT ──────────────────────────────

    @Test
    void handle_prevIsSnapshot_happyPath_movesAndRecordsSuccess() {
        stubPrevVersionSnapshot();
        when(s3.fetchBytes(PREV_PERMANENT_KEY)).thenReturn(BASE_BYTES);
        when(s3.fetchBytes(STAGING_KEY)).thenReturn(DIFF_BYTES);
        when(diffApplicator.apply(BASE_BYTES, DIFF_BYTES)).thenReturn(RESULT_BYTES);
        when(checksumVerifier.matches(RESULT_BYTES, CHECKSUM)).thenReturn(true);

        useCase.handle(validTask);

        verify(s3).moveObject(STAGING_KEY, PERMANENT_KEY);
        verify(s3).uploadBytes(RECONSTRUCT_KEY, RESULT_BYTES);
        verify(notificationWriteGateway).recordVerificationSuccess(
                RECIPIENT_ID, DOC_ID, VERSION_ID, NEW_VERSION);
    }

    // ── happy path — previous version is DIFF (triggers reconstruction) ────────

    @Test
    void handle_prevIsDiff_happyPath_reconstructsThenVerifies() {
        stubPrevVersionDiff();
        when(reconstructor.reconstruct(DOC_ID, PREV_VERSION, PREV_CHECKSUM)).thenReturn(BASE_BYTES);
        when(s3.fetchBytes(STAGING_KEY)).thenReturn(DIFF_BYTES);
        when(diffApplicator.apply(BASE_BYTES, DIFF_BYTES)).thenReturn(RESULT_BYTES);
        when(checksumVerifier.matches(RESULT_BYTES, CHECKSUM)).thenReturn(true);

        useCase.handle(validTask);

        verify(reconstructor).reconstruct(DOC_ID, PREV_VERSION, PREV_CHECKSUM);
        verify(s3).moveObject(STAGING_KEY, PERMANENT_KEY);
        verify(notificationWriteGateway).recordVerificationSuccess(
                RECIPIENT_ID, DOC_ID, VERSION_ID, NEW_VERSION);
    }

    // ── previous version lookup failures ───────────────────────────────────────

    @Test
    void handle_prevVersionNotInDb_recordsSourceNotFound() {
        when(versionQueryGateway.findByDocIdAndVersionNumber(DOC_ID, PREV_VERSION))
                .thenReturn(Optional.empty());

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "VERIFY_DIFF", FailureReason.SOURCE_NOT_FOUND);
        verifyNoInteractions(s3, diffApplicator, reconstructor);
    }

    @Test
    void handle_prevIsDiff_reconstructionFails_recordsReconstructionFailed() {
        stubPrevVersionDiff();
        when(reconstructor.reconstruct(DOC_ID, PREV_VERSION, PREV_CHECKSUM))
                .thenThrow(new ReconstructionException("snapshot missing", null));

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "VERIFY_DIFF", FailureReason.RECONSTRUCTION_FAILED);
        verify(s3, never()).fetchBytes(any());
    }

    // ── base version fetch failures (SNAPSHOT path) ────────────────────────────

    @Test
    void handle_baseVersionNotFound_recordsSourceNotFound() {
        stubPrevVersionSnapshot();
        when(s3.fetchBytes(PREV_PERMANENT_KEY)).thenThrow(NoSuchKeyException.builder().build());

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "VERIFY_DIFF", FailureReason.SOURCE_NOT_FOUND);
        verifyNoInteractions(diffApplicator);
    }

    @Test
    void handle_baseVersionS3Error_recordsStorageError() {
        stubPrevVersionSnapshot();
        when(s3.fetchBytes(PREV_PERMANENT_KEY)).thenThrow(new RuntimeException("S3 unavailable"));

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "VERIFY_DIFF", FailureReason.STORAGE_ERROR);
        verifyNoInteractions(diffApplicator);
    }

    // ── staging diff fetch failures ────────────────────────────────────────────

    @Test
    void handle_diffNotFound_recordsSourceNotFound() {
        stubPrevVersionSnapshot();
        when(s3.fetchBytes(PREV_PERMANENT_KEY)).thenReturn(BASE_BYTES);
        when(s3.fetchBytes(STAGING_KEY)).thenThrow(NoSuchKeyException.builder().build());

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "VERIFY_DIFF", FailureReason.SOURCE_NOT_FOUND);
        verifyNoInteractions(diffApplicator);
    }

    @Test
    void handle_diffS3Error_recordsStorageError() {
        stubPrevVersionSnapshot();
        when(s3.fetchBytes(PREV_PERMANENT_KEY)).thenReturn(BASE_BYTES);
        when(s3.fetchBytes(STAGING_KEY)).thenThrow(new RuntimeException("S3 unavailable"));

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "VERIFY_DIFF", FailureReason.STORAGE_ERROR);
        verifyNoInteractions(diffApplicator);
    }

    // ── diff application failure ───────────────────────────────────────────────

    @Test
    void handle_diffApplicationFails_recordsDiffApplyFailed() {
        stubPrevVersionSnapshot();
        when(s3.fetchBytes(PREV_PERMANENT_KEY)).thenReturn(BASE_BYTES);
        when(s3.fetchBytes(STAGING_KEY)).thenReturn(DIFF_BYTES);
        when(diffApplicator.apply(BASE_BYTES, DIFF_BYTES))
                .thenThrow(new DiffApplicationException("corrupt diff", new RuntimeException()));

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "VERIFY_DIFF", FailureReason.DIFF_APPLY_FAILED);
        verify(s3, never()).moveObject(any(), any());
    }

    // ── checksum mismatch ──────────────────────────────────────────────────────

    @Test
    void handle_checksumMismatch_recordsChecksumMismatch() {
        stubPrevVersionSnapshot();
        when(s3.fetchBytes(PREV_PERMANENT_KEY)).thenReturn(BASE_BYTES);
        when(s3.fetchBytes(STAGING_KEY)).thenReturn(DIFF_BYTES);
        when(diffApplicator.apply(BASE_BYTES, DIFF_BYTES)).thenReturn(RESULT_BYTES);
        when(checksumVerifier.matches(RESULT_BYTES, CHECKSUM)).thenReturn(false);

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "VERIFY_DIFF", FailureReason.CHECKSUM_MISMATCH);
        verify(s3, never()).moveObject(any(), any());
    }

    // ── move failure ───────────────────────────────────────────────────────────

    @Test
    void handle_moveFails_recordsStorageError() {
        stubPrevVersionSnapshot();
        when(s3.fetchBytes(PREV_PERMANENT_KEY)).thenReturn(BASE_BYTES);
        when(s3.fetchBytes(STAGING_KEY)).thenReturn(DIFF_BYTES);
        when(diffApplicator.apply(BASE_BYTES, DIFF_BYTES)).thenReturn(RESULT_BYTES);
        when(checksumVerifier.matches(RESULT_BYTES, CHECKSUM)).thenReturn(true);
        doThrow(new RuntimeException("move failed")).when(s3).moveObject(STAGING_KEY, PERMANENT_KEY);

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "VERIFY_DIFF", FailureReason.STORAGE_ERROR);
        verify(notificationWriteGateway, never()).recordVerificationSuccess(any(), any(), any(), any());
    }

    // ── cache upload failure is non-fatal ──────────────────────────────────────

    @Test
    void handle_cacheUploadFails_stillRecordsSuccess() {
        stubPrevVersionSnapshot();
        when(s3.fetchBytes(PREV_PERMANENT_KEY)).thenReturn(BASE_BYTES);
        when(s3.fetchBytes(STAGING_KEY)).thenReturn(DIFF_BYTES);
        when(diffApplicator.apply(BASE_BYTES, DIFF_BYTES)).thenReturn(RESULT_BYTES);
        when(checksumVerifier.matches(RESULT_BYTES, CHECKSUM)).thenReturn(true);
        doThrow(new RuntimeException("upload failed")).when(s3).uploadBytes(RECONSTRUCT_KEY, RESULT_BYTES);

        useCase.handle(validTask);

        verify(s3).moveObject(STAGING_KEY, PERMANENT_KEY);
        verify(notificationWriteGateway).recordVerificationSuccess(
                RECIPIENT_ID, DOC_ID, VERSION_ID, NEW_VERSION);
    }

    // ── early return guarantees ────────────────────────────────────────────────

    @Test
    void handle_anyFailure_recordsExactlyOnce() {
        when(versionQueryGateway.findByDocIdAndVersionNumber(DOC_ID, PREV_VERSION))
                .thenReturn(Optional.empty());

        useCase.handle(validTask);

        verify(notificationWriteGateway, times(1)).recordFailure(any(), any(), any(), any(), any());
        verify(notificationWriteGateway, never()).recordVerificationSuccess(any(), any(), any(), any());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private void stubPrevVersionSnapshot() {
        VersionRow prevRow = VersionRow.builder()
                .id(UUID.randomUUID()).docId(DOC_ID)
                .versionNumber(PREV_VERSION)
                .storageType("SNAPSHOT")
                .checksum(PREV_CHECKSUM)
                .s3Key(PREV_PERMANENT_KEY)
                .build();
        when(versionQueryGateway.findByDocIdAndVersionNumber(DOC_ID, PREV_VERSION))
                .thenReturn(Optional.of(prevRow));
    }

    private void stubPrevVersionDiff() {
        VersionRow prevRow = VersionRow.builder()
                .id(UUID.randomUUID()).docId(DOC_ID)
                .versionNumber(PREV_VERSION)
                .storageType("DIFF")
                .checksum(PREV_CHECKSUM)
                .s3Key(S3KeyTemplates.permanentVersion(DOC_ID, PREV_VERSION))
                .build();
        when(versionQueryGateway.findByDocIdAndVersionNumber(DOC_ID, PREV_VERSION))
                .thenReturn(Optional.of(prevRow));
    }
}
