package com.root.vcsbackend.version.service;

import com.root.vcsbackend.document.api.DocumentFacade;
import com.root.vcsbackend.model.CreateVersionRequest;
import com.root.vcsbackend.model.DiffResponse;
import com.root.vcsbackend.model.GetVersionDownloadUrl200Response;
import com.root.vcsbackend.model.RejectVersionRequest;
import com.root.vcsbackend.model.S3UploadResponse;
import com.root.vcsbackend.notification.api.NotificationEvent;
import com.root.vcsbackend.shared.exception.AppException;
import com.root.vcsbackend.shared.redis.DiffTaskPublisher;
import com.root.vcsbackend.shared.redis.message.ReconstructTaskMessage;
import com.root.vcsbackend.shared.redis.message.VerifyTaskMessage;
import com.root.vcsbackend.shared.redis.message.WorkerTaskType;
import com.root.vcsbackend.shared.s3.S3KeyTemplates;
import com.root.vcsbackend.shared.s3.S3PresignService;
import com.root.vcsbackend.shared.security.OrgRoleLookup;
import com.root.vcsbackend.version.domain.CommentEntity;
import com.root.vcsbackend.version.domain.StorageType;
import com.root.vcsbackend.version.domain.VersionEntity;
import com.root.vcsbackend.version.domain.VersionStatus;
import com.root.vcsbackend.version.mapper.VersionMapper;
import com.root.vcsbackend.version.persistence.CommentRepository;
import com.root.vcsbackend.version.persistence.VersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class VersionService {

    private final VersionRepository versionRepository;
    private final CommentRepository commentRepository;
    private final S3PresignService s3PresignService;
    private final ApplicationEventPublisher events;
    private final DocumentFacade documentFacade;
    private final VersionMapper versionMapper;
    /** Used for fine-grained role checks inside approve/reject/rollback. */
    private final OrgRoleLookup orgRoleLookup;
    /** Publishes worker tasks (verify / reconstruct) to Redis. */
    private final DiffTaskPublisher diffTaskPublisher;

    @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
    public S3UploadResponse createVersion(UUID docId, CreateVersionRequest req, UUID callerId) {
        documentFacade.requireExists(docId);

        int nextNumber = versionRepository.findTopByDocIdOrderByVersionNumberDesc(docId)
            .map(v -> v.getVersionNumber() + 1)
            .orElse(1);

        VersionEntity version = versionMapper.toEntity(req, docId, nextNumber);
        version = versionRepository.save(version);

        documentFacade.updateLatestVersionId(docId, version.getId());

        // Submitting a non-draft version puts the document into review
        if (Boolean.FALSE.equals(version.getIsDraft())) {
            documentFacade.updateStatusToPendingReview(docId);

            // If the request carries a checksum and a previous version exists,
            // ask the worker to verify the diff against the previous version.
            if (hasPreviousVersion(docId, nextNumber) && req.getChecksum() != null) {
                diffTaskPublisher.publish(
                        VerifyTaskMessage.builder()
                                .taskType(WorkerTaskType.VERIFY_DIFF)
                                .docId(docId)
                                .versionId(version.getId())
                                .recipientId(callerId)
                                .expectedChecksum(req.getChecksum())
                                .newVersionNumber(nextNumber)
                                .build());
            }
        }

        String s3Key = S3KeyTemplates.permanentVersion(docId, nextNumber);
        return new S3UploadResponse().uploadUrl(java.net.URI.create(s3PresignService.generateUploadUrl(s3Key)));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
    public VersionEntity getVersion(UUID docId, UUID versionId) {
        return resolveAndValidate(docId, versionId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
    public Page<VersionEntity> listVersions(UUID docId, int page, int size) {
        documentFacade.requireExists(docId);
        return versionRepository.findByDocIdOrderByVersionNumberDesc(docId, PageRequest.of(page, size));
    }

    @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
    public void approveVersion(UUID docId, UUID versionId, UUID callerId) {
        VersionEntity version = resolveAndValidate(docId, versionId);
        if (version.getStatus() != VersionStatus.PENDING) {
            throw new AppException(HttpStatus.CONFLICT, "Only PENDING versions can be approved");
        }
        requireRole(documentFacade.resolveOrgId(docId), callerId, "REVIEWER", "ADMIN");

        version.setStatus(VersionStatus.APPROVED);
        version.setIsDraft(false);
        versionRepository.save(version);
        documentFacade.updateLatestApprovedVersionId(docId, versionId);
        documentFacade.updateStatusToApproved(docId);

        UUID authorId = documentFacade.getAuthorId(docId);
        events.publishEvent(new NotificationEvent(this, authorId, "VERSION_APPROVED",
            Map.of("docId", docId, "versionId", versionId)));
    }

    @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
    public void rejectVersion(UUID docId, UUID versionId, UUID callerId, RejectVersionRequest rejectReq) {
        VersionEntity version = resolveAndValidate(docId, versionId);
        if (version.getStatus() != VersionStatus.PENDING) {
            throw new AppException(HttpStatus.CONFLICT, "Only PENDING versions can be rejected");
        }
        requireRole(documentFacade.resolveOrgId(docId), callerId, "REVIEWER", "ADMIN");

        version.setStatus(VersionStatus.REJECTED);
        versionRepository.save(version);
        documentFacade.updateStatusToRejected(docId);

        if (rejectReq != null && rejectReq.getReason() != null && !rejectReq.getReason().isBlank()) {
            commentRepository.save(CommentEntity.builder()
                .versionId(versionId).authorId(callerId).content(rejectReq.getReason()).build());
        }

        UUID authorId = documentFacade.getAuthorId(docId);
        events.publishEvent(new NotificationEvent(this, authorId, "VERSION_REJECTED",
            Map.of("docId", docId, "versionId", versionId)));
    }

    @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
    public CommentEntity addComment(UUID docId, UUID versionId, String content, UUID authorId) {
        resolveAndValidate(docId, versionId);
        return commentRepository.save(CommentEntity.builder()
            .versionId(versionId).authorId(authorId).content(content).build());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
    public List<CommentEntity> listComments(UUID docId, UUID versionId) {
        resolveAndValidate(docId, versionId);
        return commentRepository.findByVersionIdOrderByCreatedAtAsc(versionId);
    }

    /**
     * Returns a presigned download URL for the version's content.
     * <p>
     * If the version is stored as a full {@code SNAPSHOT}, the URL points to the
     * full document and the client can download it immediately.
     * <p>
     * If the version is stored as a {@code DIFF}, this method triggers an async
     * reconstruction via the worker. The reconstructed document's presigned URL
     * will arrive via SSE ({@code DOCUMENT_RECONSTRUCTED} notification). In the
     * meantime the returned URL points to the raw diff file in S3.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
    public DownloadUrlResult getDownloadUrl(UUID docId, UUID versionId, UUID callerId) {
        VersionEntity version = resolveAndValidate(docId, versionId);

        boolean reconstructionDispatched = version.getStorageType() == StorageType.DIFF;
        if (reconstructionDispatched) {
            diffTaskPublisher.publish(
                    ReconstructTaskMessage.builder()
                            .taskType(WorkerTaskType.RECONSTRUCT_DOCUMENT)
                            .docId(docId)
                            .versionId(versionId)
                            .recipientId(callerId)
                            .expectedChecksum(version.getChecksum())
                            .targetVersionNumber(version.getVersionNumber())
                            .build());
        }

        var response = new GetVersionDownloadUrl200Response()
                .downloadUrl(reconstructionDispatched?
                        java.net.URI.create(""):
                        java.net.URI.create(
                        s3PresignService.generateDownloadUrl(
                                S3KeyTemplates.permanentVersion(docId, version.getVersionNumber()))));

        return new DownloadUrlResult(response, reconstructionDispatched);
    }


    /**
     * Computes a diff response between two versions of the same document.
     * <p>
     * For each version:
     * <ul>
     *   <li><b>SNAPSHOT</b> — a presigned download URL is generated directly.</li>
     *   <li><b>DIFF</b> — an async reconstruction is triggered via Redis so the
     *       worker assembles the full document. The presigned URL will arrive via
     *       SSE ({@code DOCUMENT_RECONSTRUCTED}). Meanwhile, the raw diff URL is
     *       returned.</li>
     * </ul>
     * The {@code diff} field in the response contains a JSON object with
     * {@code fromUrl} and {@code toUrl} presigned download URLs and metadata so
     * the client can fetch both versions and render the diff in the UI.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
    public DiffResponse getDiff(UUID docId, UUID fromId, UUID toId, UUID callerId) {
        VersionEntity from = resolveAndValidate(docId, fromId);
        VersionEntity to   = resolveAndValidate(docId, toId);

        String fromUrl = presignOrReconstruct(docId, from, callerId);
        String toUrl   = presignOrReconstruct(docId, to, callerId);

        String diffPayload = """
                {"fromUrl":"%s","toUrl":"%s","fromVersion":%d,"toVersion":%d,"fromStorageType":"%s","toStorageType":"%s"}"""
                .formatted(
                        fromUrl, toUrl,
                        from.getVersionNumber(), to.getVersionNumber(),
                        from.getStorageType(), to.getStorageType());

        return new DiffResponse()
                .fromVersionId(fromId)
                .toVersionId(toId)
                .diff(diffPayload);
    }

    @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
    public VersionEntity rollbackVersion(UUID docId, UUID targetVersionId, UUID callerId) {
        VersionEntity target = resolveAndValidate(docId, targetVersionId);
        requireRole(documentFacade.resolveOrgId(docId), callerId, "AUTHOR", "ADMIN");

        int nextNumber = versionRepository.findTopByDocIdOrderByVersionNumberDesc(docId)
            .map(v -> v.getVersionNumber() + 1).orElse(1);

        VersionEntity rollback = versionRepository.save(VersionEntity.builder()
            .docId(docId).versionNumber(nextNumber)
            .status(VersionStatus.PENDING).isDraft(false)
            .checksum(target.getChecksum())
            .storageType(target.getStorageType())
            .build());

        documentFacade.updateLatestVersionId(docId, rollback.getId());
        documentFacade.updateStatusToPendingReview(docId); // rollback always submits for review
        return rollback;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns a presigned download URL for a version's S3 object.
     * If the version is stored as a DIFF, also triggers async reconstruction
     * so the full-document URL will arrive via SSE later.
     */
    private String presignOrReconstruct(UUID docId, VersionEntity version, UUID callerId) {
        if (version.getStorageType() == StorageType.DIFF) {
            diffTaskPublisher.publish(
                    ReconstructTaskMessage.builder()
                            .taskType(WorkerTaskType.RECONSTRUCT_DOCUMENT)
                            .docId(docId)
                            .versionId(version.getId())
                            .recipientId(callerId)
                            .expectedChecksum(version.getChecksum())
                            .targetVersionNumber(version.getVersionNumber())
                            .build());
        }
        return s3PresignService.generateDownloadUrl(
                S3KeyTemplates.permanentVersion(docId, version.getVersionNumber()));
    }

    /**
     * Returns {@code true} if there is at least one version before {@code currentNumber}
     * for the given document.
     */
    private boolean hasPreviousVersion(UUID docId, int currentNumber) {
        if (currentNumber <= 1) return false;
        return versionRepository.findTopByDocIdAndVersionNumberLessThanOrderByVersionNumberDesc(docId, currentNumber)
                .isPresent();
    }

    private VersionEntity resolveAndValidate(UUID docId, UUID versionId) {
        VersionEntity v = versionRepository.findById(versionId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Version not found: " + versionId));
        if (!v.getDocId().equals(docId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Version does not belong to this document");
        }
        return v;
    }

    private void requireRole(UUID orgId, UUID userId, String... roles) {
        boolean ok = orgRoleLookup.findRole(orgId, userId)
            .map(role -> Arrays.asList(roles).contains(role))
            .orElse(false);
        if (!ok) {
            throw new AppException(HttpStatus.FORBIDDEN,
                "Required role: " + Arrays.toString(roles));
        }
    }
}
