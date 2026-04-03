package com.root.vcsbackendworker.verify.application;

import com.root.vcsbackendworker.shared.diff.DiffApplicationException;
import com.root.vcsbackendworker.shared.diff.DiffApplicator;
import com.root.vcsbackendworker.shared.messaging.MessageMetadata;
import com.root.vcsbackendworker.shared.messaging.WorkerResultPublisher;
import com.root.vcsbackendworker.shared.messaging.inbound.VerifyTaskMessage;
import com.root.vcsbackendworker.shared.messaging.outbound.FailureReason;
import com.root.vcsbackendworker.shared.messaging.outbound.ProcessingStatus;
import com.root.vcsbackendworker.shared.messaging.outbound.VerificationResultMessage;
import com.root.vcsbackendworker.shared.s3.S3DocumentStorage;
import com.root.vcsbackendworker.verify.domain.ChecksumVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerifyDiffUseCaseTest {

    @Mock S3DocumentStorage s3;
    @Mock DiffApplicator diffApplicator;
    @Mock ChecksumVerifier checksumVerifier;
    @Mock WorkerResultPublisher resultPublisher;
    @InjectMocks VerifyDiffUseCase useCase;

    @Captor ArgumentCaptor<VerificationResultMessage> resultCaptor;

    static final UUID DOC_ID = UUID.randomUUID();
    static final UUID VERSION_ID = UUID.randomUUID();
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
                .metadata(MessageMetadata.builder().correlationId(UUID.randomUUID()).build())
                .latestVersionS3Key(BASE_KEY)
                .diffS3Key(DIFF_KEY)
                .expectedChecksum(CHECKSUM)
                .build();

        when(resultPublisher.buildMetadata(any())).thenReturn(MessageMetadata.builder().build());
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void handle_happyPath_publishesSucceeded() {
        when(s3.fetchBytes(BASE_KEY)).thenReturn(BASE_BYTES);
        when(s3.fetchBytes(DIFF_KEY)).thenReturn(DIFF_BYTES);
        when(diffApplicator.apply(BASE_BYTES, DIFF_BYTES)).thenReturn(RESULT_BYTES);
        when(checksumVerifier.sha256Hex(RESULT_BYTES)).thenReturn(CHECKSUM);

        useCase.handle(validTask);

        verify(s3).uploadBytes(PERMANENT_KEY, RESULT_BYTES);
        verify(resultPublisher).publish(resultCaptor.capture());
        VerificationResultMessage result = resultCaptor.getValue();
        assertThat(result.getStatus()).isEqualTo(ProcessingStatus.SUCCEEDED);
        assertThat(result.getActualChecksum()).isEqualTo(CHECKSUM);
        assertThat(result.getDocId()).isEqualTo(DOC_ID);
        assertThat(result.getVersionId()).isEqualTo(VERSION_ID);
        assertThat(result.getFailureReason()).isNull();
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
    void handle_baseVersionNotFound_publishesSourceNotFound() {
        when(s3.fetchBytes(BASE_KEY)).thenThrow(NoSuchKeyException.builder().build());

        useCase.handle(validTask);

        verify(resultPublisher).publish(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(resultCaptor.getValue().getFailureReason()).isEqualTo(FailureReason.SOURCE_NOT_FOUND);
        verifyNoInteractions(diffApplicator);
    }

    @Test
    void handle_baseVersionS3Error_publishesStorageError() {
        when(s3.fetchBytes(BASE_KEY)).thenThrow(new RuntimeException("S3 unavailable"));

        useCase.handle(validTask);

        verify(resultPublisher).publish(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getFailureReason()).isEqualTo(FailureReason.STORAGE_ERROR);
        verifyNoInteractions(diffApplicator);
    }

    // ── diff fetch failures ───────────────────────────────────────────────────

    @Test
    void handle_diffNotFound_publishesSourceNotFound() {
        when(s3.fetchBytes(BASE_KEY)).thenReturn(BASE_BYTES);
        when(s3.fetchBytes(DIFF_KEY)).thenThrow(NoSuchKeyException.builder().build());

        useCase.handle(validTask);

        verify(resultPublisher).publish(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getFailureReason()).isEqualTo(FailureReason.SOURCE_NOT_FOUND);
        verifyNoInteractions(diffApplicator);
    }

    @Test
    void handle_diffS3Error_publishesStorageError() {
        when(s3.fetchBytes(BASE_KEY)).thenReturn(BASE_BYTES);
        when(s3.fetchBytes(DIFF_KEY)).thenThrow(new RuntimeException("S3 unavailable"));

        useCase.handle(validTask);

        verify(resultPublisher).publish(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getFailureReason()).isEqualTo(FailureReason.STORAGE_ERROR);
        verifyNoInteractions(diffApplicator);
    }

    // ── diff application failure ──────────────────────────────────────────────

    @Test
    void handle_diffApplicationFails_publishesDiffApplyFailed() {
        when(s3.fetchBytes(BASE_KEY)).thenReturn(BASE_BYTES);
        when(s3.fetchBytes(DIFF_KEY)).thenReturn(DIFF_BYTES);
        when(diffApplicator.apply(BASE_BYTES, DIFF_BYTES))
                .thenThrow(new DiffApplicationException("corrupt diff", new RuntimeException()));

        useCase.handle(validTask);

        verify(resultPublisher).publish(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getFailureReason()).isEqualTo(FailureReason.DIFF_APPLY_FAILED);
        verify(s3, never()).uploadBytes(any(), any());
    }

    // ── checksum mismatch ─────────────────────────────────────────────────────

    @Test
    void handle_checksumMismatch_publishesChecksumMismatchWithActualHash() {
        when(s3.fetchBytes(BASE_KEY)).thenReturn(BASE_BYTES);
        when(s3.fetchBytes(DIFF_KEY)).thenReturn(DIFF_BYTES);
        when(diffApplicator.apply(BASE_BYTES, DIFF_BYTES)).thenReturn(RESULT_BYTES);
        when(checksumVerifier.sha256Hex(RESULT_BYTES)).thenReturn("wronghash");

        useCase.handle(validTask);

        verify(resultPublisher).publish(resultCaptor.capture());
        VerificationResultMessage result = resultCaptor.getValue();
        assertThat(result.getFailureReason()).isEqualTo(FailureReason.CHECKSUM_MISMATCH);
        assertThat(result.getActualChecksum()).isEqualTo("wronghash");
        verify(s3, never()).uploadBytes(any(), any());
    }

    // ── upload failure ────────────────────────────────────────────────────────

    @Test
    void handle_uploadFails_publishesStorageError() {
        when(s3.fetchBytes(BASE_KEY)).thenReturn(BASE_BYTES);
        when(s3.fetchBytes(DIFF_KEY)).thenReturn(DIFF_BYTES);
        when(diffApplicator.apply(BASE_BYTES, DIFF_BYTES)).thenReturn(RESULT_BYTES);
        when(checksumVerifier.sha256Hex(RESULT_BYTES)).thenReturn(CHECKSUM);
        doThrow(new RuntimeException("upload failed")).when(s3).uploadBytes(any(), any());

        useCase.handle(validTask);

        verify(resultPublisher).publish(resultCaptor.capture());
        VerificationResultMessage result = resultCaptor.getValue();
        assertThat(result.getFailureReason()).isEqualTo(FailureReason.STORAGE_ERROR);
        // actualChecksum is still populated even on upload failure
        assertThat(result.getActualChecksum()).isEqualTo(CHECKSUM);
    }

    // ── early return guarantees ───────────────────────────────────────────────

    @Test
    void handle_anyFailure_publishesExactlyOnce() {
        when(s3.fetchBytes(BASE_KEY)).thenThrow(NoSuchKeyException.builder().build());

        useCase.handle(validTask);

        verify(resultPublisher, times(1)).publish(any(VerificationResultMessage.class));
    }
}

