package com.root.vcsbackend.version.service;

import com.root.vcsbackend.document.api.DocumentFacade;
import com.root.vcsbackend.model.CreateVersionRequest;
import com.root.vcsbackend.model.DiffResponse;
import com.root.vcsbackend.model.GetVersionDownloadUrl200Response;
import com.root.vcsbackend.model.RejectVersionRequest;
import com.root.vcsbackend.model.S3UploadResponse;
import com.root.vcsbackend.notification.api.NotificationEvent;
import com.root.vcsbackend.shared.exception.AppException;
import com.root.vcsbackend.shared.s3.S3PresignService;
import com.root.vcsbackend.shared.security.OrgRoleLookup;
import com.root.vcsbackend.version.domain.CommentEntity;
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

    @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
    public S3UploadResponse createVersion(UUID docId, CreateVersionRequest req, UUID callerId) {
        documentFacade.requireExists(docId);

        int nextNumber = versionRepository.findTopByDocIdOrderByVersionNumberDesc(docId)
            .map(v -> v.getVersionNumber() + 1)
            .orElse(1);

        String s3Key = "documents/" + docId + "/v" + nextNumber;
        VersionEntity version = versionMapper.toEntity(req, docId, nextNumber, s3Key);
        version = versionRepository.save(version);

        documentFacade.updateLatestVersionId(docId, version.getId());

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

    @Transactional(readOnly = true)
    @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
    public GetVersionDownloadUrl200Response getDownloadUrl(UUID docId, UUID versionId) {
        VersionEntity version = resolveAndValidate(docId, versionId);
        return new GetVersionDownloadUrl200Response()
            .downloadUrl(java.net.URI.create(s3PresignService.generateDownloadUrl(version.getS3Key())));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
    public DiffResponse getDiff(UUID docId, UUID fromId, UUID toId) {
        VersionEntity from = resolveAndValidate(docId, fromId);
        VersionEntity to   = resolveAndValidate(docId, toId);
        String summary = "v%d → v%d".formatted(from.getVersionNumber(), to.getVersionNumber());
        return new DiffResponse().fromVersionId(fromId).toVersionId(toId).diff(summary);
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
            .s3Key(target.getS3Key()).checksum(target.getChecksum())
            .build());

        documentFacade.updateLatestVersionId(docId, rollback.getId());
        return rollback;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
