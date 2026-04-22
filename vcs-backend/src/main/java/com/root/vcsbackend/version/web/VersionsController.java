package com.root.vcsbackend.version.web;

import com.root.vcsbackend.api.VersionsApi;
import com.root.vcsbackend.model.AddCommentRequest;
import com.root.vcsbackend.model.Comment;
import com.root.vcsbackend.model.CreateVersionRequest;
import com.root.vcsbackend.model.DiffResponse;
import com.root.vcsbackend.model.GetVersionDownloadUrl200Response;
import com.root.vcsbackend.model.PagedVersions;
import com.root.vcsbackend.model.PendingReviewItem;
import com.root.vcsbackend.model.RejectVersionRequest;
import com.root.vcsbackend.model.S3UploadResponse;
import com.root.vcsbackend.model.Version;
import com.root.vcsbackend.shared.security.SecurityHelper;
import com.root.vcsbackend.shared.web.PageMapper;
import com.root.vcsbackend.version.mapper.VersionMapper;
import com.root.vcsbackend.version.service.VersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class VersionsController implements VersionsApi {

    private final VersionService versionService;
    private final VersionMapper versionMapper;
    private final PageMapper pageMapper;
    private final SecurityHelper securityHelper;

    // NEW: Request Review endpoint
    @PostMapping("/documents/{docId}/versions/{versionId}/request-review")
    public ResponseEntity<Void> requestReview(@PathVariable UUID docId, @PathVariable UUID versionId) {
        UUID callerId = securityHelper.currentUser().userId();
        versionService.requestReview(docId, versionId, callerId);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Comment> addComment(UUID docId, UUID versionId, AddCommentRequest req) {
        UUID callerId = securityHelper.currentUser().userId();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(versionMapper.toCommentDto(
                versionService.addComment(docId, versionId, req.getContent(), callerId)));
    }

    @Override
    public ResponseEntity<Void> approveVersion(UUID docId, UUID versionId) {
        versionService.approveVersion(docId, versionId, securityHelper.currentUser().userId());
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<S3UploadResponse> createVersion(UUID docId, CreateVersionRequest req) {
        UUID callerId = securityHelper.currentUser().userId();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(versionService.createVersion(docId, req, callerId));
    }

    @Override
    public ResponseEntity<Version> getVersion(UUID docId, UUID versionId) {
        return ResponseEntity.ok(versionMapper.toDto(versionService.getVersion(docId, versionId)));
    }

    @Override
    public ResponseEntity<DiffResponse> getVersionDiff(UUID from, UUID to, UUID docId) {
        UUID callerId = securityHelper.currentUser().userId();
        return ResponseEntity.ok(versionService.getDiff(docId, from, to, callerId));
    }

    @Override
    public ResponseEntity<GetVersionDownloadUrl200Response> getVersionDownloadUrl(UUID docId, UUID versionId) {
        UUID callerId = securityHelper.currentUser().userId();
        var result = versionService.getDownloadUrl(docId, versionId, callerId);
        HttpStatus status = result.reconstructionDispatched() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }

    @Override
    public ResponseEntity<List<Comment>> listComments(UUID docId, UUID versionId) {
        List<Comment> comments = versionService.listComments(docId, versionId).stream()
            .map(versionMapper::toCommentDto)
            .toList();
        return ResponseEntity.ok(comments);
    }

    @Override
    public ResponseEntity<PagedVersions> listVersions(UUID docId, Integer page, Integer size) {
        var result = versionService.listVersions(docId, page, size);
        var paged = new PagedVersions()
            .content(result.getContent().stream().map(versionMapper::toDto).toList())
            .meta(pageMapper.toPageMeta(result));
        return ResponseEntity.ok(paged);
    }

    @Override
    public ResponseEntity<Void> rejectVersion(UUID docId, UUID versionId, @Nullable RejectVersionRequest req) {
        versionService.rejectVersion(docId, versionId, securityHelper.currentUser().userId(), req);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<List<PendingReviewItem>> listPendingReviewVersions() {
        UUID callerId = securityHelper.currentUser().userId();
        return ResponseEntity.ok(versionService.listPendingReviewVersions(callerId));
    }

    @Override
    public ResponseEntity<Version> rollbackVersion(UUID docId, UUID versionId) {
        UUID callerId = securityHelper.currentUser().userId();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(versionMapper.toDto(versionService.rollbackVersion(docId, versionId, callerId)));
    }
}