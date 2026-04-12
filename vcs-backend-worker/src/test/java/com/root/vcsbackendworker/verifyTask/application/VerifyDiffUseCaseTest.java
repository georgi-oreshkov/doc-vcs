package com.root.vcsbackendworker.verifyTask.application;

import com.root.vcsbackendworker.shared.db.NotificationWriteGateway;
import com.root.vcsbackendworker.shared.diff.DiffApplicationException;
import com.root.vcsbackendworker.shared.diff.DiffApplicator;
import com.root.vcsbackendworker.shared.messaging.FailureReason;
import com.root.vcsbackendworker.shared.messaging.MessageMetadata;
import com.root.vcsbackendworker.shared.messaging.inbound.VerifyTaskMessage;
import com.root.vcsbackendworker.shared.messaging.inbound.WorkerTaskType;
import com.root.vcsbackendworker.shared.s3.S3DocumentStorage;
import com.root.vcsbackendworker.shared.verify.ChecksumVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerifyDiffUseCaseTest {

    @Mock S3DocumentStorage s3;
    @Mock DiffApplicator diffApplicator;
    @Mock ChecksumVerifier checksumVerifier;
    @Mock NotificationWriteGateway notificationWriteGateway;
    @InjectMocks VerifyDiffUseCase useCase;

    static final UUID DOC_ID = UUID.randomUUID();
    static final UUID VERSION_ID = UUID.randomUUID();
    static final UUID RECIPIENT_ID = UUID.randomUUID();
    static final String BASE_KEY = "documents/" + DOC_ID + "/v1";
    static final String DIFF_KEY = "documents/" + DOC_ID + "/v2.diff";
    static final String PERMANENT_KEY = "documents/" + DOC_ID + "/v2";
    static final byte[] BASE_BYTES = "base content".getBytes();
    static final byte[] DIFF_BYTES = "diff content".getBytes();
    static final byte[] RESULT_BYTES = "result content".getBytes();
    static final String CHECKSUM = "abc123checksum";

    VerifyTaskMessage validTask;

    @BeforeEach
    void setUp() {
        validTask = VerifyTaskMessage.builder()
                .docId(DOC_ID)
                .versionId(VERSION_ID)
                .recipientId(RECIPIENT_ID)
                .taskType(WorkerTaskType.VERIFY_DIFF)
                .metadata(MessageMetadata.builder().correlationId(UUID.randomUUID()).build())
                .latestVersionS3Key(BASE_KEY)
                .diffS3Key(DIFF_KEY)
                .expectedChecksum(CHECKSUM)
                .build();
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void handle_happyPath_uploadsAndRecordsSuccess() {
        when(s3.fetchBytes(BASE_KEY)).thenReturn(BASE_BYTES);
        when(s3.fetchBytes(DIFF_KEY)).thenReturn(DIFF_BYTES);
        when(diffApplicator.apply(BASE_BYTES, DIFF_BYTES)).thenReturn(RESULT_BYTES);
        when(checksumVerifier.sha256Hex(RESULT_BYTES)).thenReturn(CHECKSUM);

        useCase.handle(validTask);

        verify(s3).uploadBytes(PERMANENT_KEY, RESULT_BYTES);
        verify(notificationWriteGateway).recordVerificationSuccess(RECIPIENT_ID, DOC_ID, VERSION_ID, CHECKSUM);
    }

    @Test
    void handle_happyPath_stripsDiffSuffixForPermanentKey() {
        when(s3.fetchBytes(BASE_KEY)).thenReturn(BASE_BYTES);
        when(s3.fetchBytes(DIFF_KEY)).thenReturn(DIFF_BYTES);
        when(diffApplicator.apply(BASE_BYTES, DIFF_BYTES)).thenReturn(RESULT_BYTES);
        when(checksumVerifier.sha256Hex(RESULT_BYTES)).thenReturn(CHECKSUM);

        useCase.handle(validTask);

        verify(s3).uploadBytes(eq(PERMANENT_KEY), any());
        verify(s3, never()).uploadBytes(eq(DIFF_KEY), any());
    }

    // ── base version fetch failures ───────────────────────────────────────────

    @Test
    void handle_baseVersionNotFound_recordsSourceNotFound() {
        when(s3.fetchBytes(BASE_KEY)).thenThrow(NoSuchKeyException.builder().build());

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "VERIFY_DIFF", FailureReason.SOURCE_NOT_FOUND);
        verifyNoInteractions(diffApplicator);
    }

    @Test
    void handle_baseVersionS3Error_recordsStorageError() {
        when(s3.fetchBytes(BASE_KEY)).thenThrow(new RuntimeException("S3 unavailable"));

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "VERIFY_DIFF", FailureReason.STORAGE_ERROR);
        verifyNoInteractions(diffApplicator);
    }

    // ── diff fetch failures ───────────────────────────────────────────────────

    @Test
    void handle_diffNotFound_recordsSourceNotFound() {
        when(s3.fetchBytes(BASE_KEY)).thenReturn(BASE_BYTES);
        when(s3.fetchBytes(DIFF_KEY)).thenThrow(NoSuchKeyException.builder().build());

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "VERIFY_DIFF", FailureReason.SOURCE_NOT_FOUND);
        verifyNoInteractions(diffApplicator);
    }

    @Test
    void handle_diffS3Error_recordsStorageError() {
        when(s3.fetchBytes(BASE_KEY)).thenReturn(BASE_BYTES);
        when(s3.fetchBytes(DIFF_KEY)).thenThrow(new RuntimeException("S3 unavailable"));

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "VERIFY_DIFF", FailureReason.STORAGE_ERROR);
        verifyNoInteractions(diffApplicator);
    }

    // ── diff application failure ──────────────────────────────────────────────

    @Test
    void handle_diffApplicationFails_recordsDiffApplyFailed() {
        when(s3.fetchBytes(BASE_KEY)).thenReturn(BASE_BYTES);
        when(s3.fetchBytes(DIFF_KEY)).thenReturn(DIFF_BYTES);
        when(diffApplicator.apply(BASE_BYTES, DIFF_BYTES))
                .thenThrow(new DiffApplicationException("corrupt diff", new RuntimeException()));

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "VERIFY_DIFF", FailureReason.DIFF_APPLY_FAILED);
        verify(s3, never()).uploadBytes(any(), any());
    }

    // ── checksum mismatch ─────────────────────────────────────────────────────

    @Test
    void handle_checksumMismatch_recordsChecksumMismatch() {
        when(s3.fetchBytes(BASE_KEY)).thenReturn(BASE_BYTES);
        when(s3.fetchBytes(DIFF_KEY)).thenReturn(DIFF_BYTES);
        when(diffApplicator.apply(BASE_BYTES, DIFF_BYTES)).thenReturn(RESULT_BYTES);
        when(checksumVerifier.sha256Hex(RESULT_BYTES)).thenReturn("wronghash");

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "VERIFY_DIFF", FailureReason.CHECKSUM_MISMATCH);
        verify(s3, never()).uploadBytes(any(), any());
    }

    // ── upload failure ────────────────────────────────────────────────────────

    @Test
    void handle_uploadFails_recordsStorageError() {
        when(s3.fetchBytes(BASE_KEY)).thenReturn(BASE_BYTES);
        when(s3.fetchBytes(DIFF_KEY)).thenReturn(DIFF_BYTES);
        when(diffApplicator.apply(BASE_BYTES, DIFF_BYTES)).thenReturn(RESULT_BYTES);
        when(checksumVerifier.sha256Hex(RESULT_BYTES)).thenReturn(CHECKSUM);
        doThrow(new RuntimeException("upload failed")).when(s3).uploadBytes(any(), any());

        useCase.handle(validTask);

        verify(notificationWriteGateway).recordFailure(RECIPIENT_ID, DOC_ID, VERSION_ID,
                "VERIFY_DIFF", FailureReason.STORAGE_ERROR);
    }

    // ── early return guarantees ───────────────────────────────────────────────

    @Test
    void handle_anyFailure_recordsExactlyOnce() {
        when(s3.fetchBytes(BASE_KEY)).thenThrow(NoSuchKeyException.builder().build());

        useCase.handle(validTask);

        verify(notificationWriteGateway, times(1)).recordFailure(any(), any(), any(), any(), any());
        verify(notificationWriteGateway, never()).recordVerificationSuccess(any(), any(), any(), any());
    }
}
