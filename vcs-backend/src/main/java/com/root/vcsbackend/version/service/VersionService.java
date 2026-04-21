package com.root.vcsbackend.version.service;

import com.root.vcsbackend.document.api.DocumentFacade;
import com.root.vcsbackend.model.CreateVersionRequest;
import com.root.vcsbackend.model.DiffResponse;
import com.root.vcsbackend.model.GetVersionDownloadUrl200Response;
import com.root.vcsbackend.model.RejectVersionRequest;
import com.root.vcsbackend.model.S3UploadResponse;
import com.root.vcsbackend.notification.api.NotificationEvent;
import com.root.vcsbackend.organization.api.OrganizationFacade;
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
import com.root.vcsbackend.version.web.MinioWebhookController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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
    private final OrgRoleLookup orgRoleLookup;
    private final DiffTaskPublisher diffTaskPublisher;
    private final OrganizationFacade organizationFacade; // NEW: Added to fetch reviewers

    @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
    public S3UploadResponse createVersion(UUID docId, CreateVersionRequest req, UUID callerId) {
        documentFacade.requireExists(docId);

        int nextNumber = versionRepository.findTopByDocIdOrderByVersionNumberDesc(docId)
            .map(v -> v.getVersionNumber() + 1)
            .orElse(1);

        VersionEntity version = versionMapper.toEntity(req, docId, nextNumber);
        
        // NEW: Force every newly uploaded version to be a DRAFT by default
        version.setIsDraft(true);
        version.setStatus(VersionStatus.DRAFT);
        
        version = versionRepository.save(version);
        documentFacade.updateLatestVersionId(docId, version.getId());

        UUID orgId = documentFacade.resolveOrgId(docId);
        Map<String, Object> uploadPayload = Map.of(
                "documentId", docId,
                "organizationId", orgId,
                "versionId", version.getId(),
                "message", "New draft version uploaded successfully."
        );
        events.publishEvent(new NotificationEvent(this, callerId, "VERSION_UPLOADED", uploadPayload));

        String s3Key = (nextNumber > 1 && req.getIsDiff())? S3KeyTemplates.stagingDiff(docId,nextNumber) : S3KeyTemplates.permanentVersion(docId, nextNumber);
        return new S3UploadResponse().uploadUrl(java.net.URI.create(s3PresignService.generateUploadUrl(s3Key)));
    }

    // NEW METHOD: Used by authors to request a review on a draft version
    @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
    public void requestReview(UUID docId, UUID versionId, UUID callerId) {
        VersionEntity version = resolveAndValidate(docId, versionId);
        if (!Boolean.TRUE.equals(version.getIsDraft()) || version.getStatus() != VersionStatus.DRAFT) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Only DRAFT versions can be submitted for review.");
        }

        UUID orgId = documentFacade.resolveOrgId(docId);

        // Update version to PENDING (reviewing)
        version.setIsDraft(false);
        version.setStatus(VersionStatus.PENDING);
        versionRepository.save(version);

        // Update document status
        documentFacade.updateStatusToPendingReview(docId);

        // Notify all ADMINS and REVIEWERS in the org
        Map<String, Object> reviewPayload = Map.of(
                "documentId", docId,
                "organizationId", orgId,
                "versionId", version.getId(),
                "message", "A new version has been submitted and is waiting for your review."
        );

        organizationFacade.getReviewersAndAdmins(orgId).forEach(reviewerId -> {
            // Do not send a review request to the author themselves if they happen to be an admin
            if (!reviewerId.equals(callerId)) {
                events.publishEvent(new NotificationEvent(this, reviewerId, "VERSION_PENDING_REVIEW", reviewPayload));
            }
        });
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
        
        UUID orgId = documentFacade.resolveOrgId(docId);
        requireRole(orgId, callerId, "REVIEWER", "ADMIN");

        version.setStatus(VersionStatus.APPROVED);
        version.setIsDraft(false);
        versionRepository.save(version);
        documentFacade.updateLatestApprovedVersionId(docId, versionId);
        documentFacade.updateStatusToApproved(docId);

        UUID authorId = documentFacade.getAuthorId(docId);
        events.publishEvent(new NotificationEvent(this, authorId, "VERSION_APPROVED",
            Map.of(
                "documentId", docId, 
                "organizationId", orgId, 
                "versionId", versionId,
                "message", "Your version was approved."
            )));
    }

    @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
    public void rejectVersion(UUID docId, UUID versionId, UUID callerId, RejectVersionRequest rejectReq) {
        VersionEntity version = resolveAndValidate(docId, versionId);
        if (version.getStatus() != VersionStatus.PENDING) {
            throw new AppException(HttpStatus.CONFLICT, "Only PENDING versions can be rejected");
        }
        
        UUID orgId = documentFacade.resolveOrgId(docId);
        requireRole(orgId, callerId, "REVIEWER", "ADMIN");

        version.setStatus(VersionStatus.REJECTED);
        versionRepository.save(version);
        documentFacade.updateStatusToRejected(docId);

        if (rejectReq != null && rejectReq.getReason() != null && !rejectReq.getReason().isBlank()) {
            commentRepository.save(CommentEntity.builder()
                .versionId(versionId).authorId(callerId).content(rejectReq.getReason()).build());
        }

        UUID authorId = documentFacade.getAuthorId(docId);
        events.publishEvent(new NotificationEvent(this, authorId, "VERSION_REJECTED",
            Map.of(
                "documentId", docId, 
                "organizationId", orgId, 
                "versionId", versionId,
                "message", "Your version was rejected."
            )));
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

    @Transactional(readOnly = true)
    public void handleStagingDiffUploaded(UUID docId, int versionNumber) {
        VersionEntity version = versionRepository.findByDocIdAndVersionNumber(docId, versionNumber)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Version not found: docId=%s versionNumber=%d".formatted(docId, versionNumber)));

        if (version.getStorageType() != StorageType.DIFF) {
            return;
        }

        if (version.getCreatedBy() == null) {
            return;
        }

        diffTaskPublisher.publish(
                VerifyTaskMessage.builder()
                        .taskType(WorkerTaskType.VERIFY_DIFF)
                        .docId(docId)
                        .versionId(version.getId())
                        .recipientId(version.getCreatedBy())
                        .expectedChecksum(version.getChecksum())
                        .newVersionNumber(versionNumber)
                        .build());
    }

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
        documentFacade.updateStatusToPendingReview(docId); 
        return rollback;
    }

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