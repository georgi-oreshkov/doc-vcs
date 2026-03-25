package com.root.vcsbackend.version.web;

import com.root.vcsbackend.api.VersionsApi;
import com.root.vcsbackend.model.AddCommentRequest;
import com.root.vcsbackend.model.Comment;
import com.root.vcsbackend.model.CreateVersionRequest;
import com.root.vcsbackend.model.DiffResponse;
import com.root.vcsbackend.model.GetVersionDownloadUrl200Response;
import com.root.vcsbackend.model.PagedVersions;
import com.root.vcsbackend.model.RejectVersionRequest;
import com.root.vcsbackend.model.S3UploadResponse;
import com.root.vcsbackend.model.Version;
import com.root.vcsbackend.shared.security.CurrentUser;
import com.root.vcsbackend.shared.security.JwtPrincipal;
import com.root.vcsbackend.version.service.VersionService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class VersionsController implements VersionsApi {

    private final VersionService versionService;

    @Override
    public ResponseEntity<Comment> addComment(UUID docId, UUID versionId, AddCommentRequest addCommentRequest) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Void> approveVersion(UUID docId, UUID versionId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<S3UploadResponse> createVersion(UUID docId, CreateVersionRequest createVersionRequest) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Version> getVersion(UUID docId, UUID versionId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<DiffResponse> getVersionDiff(UUID from, UUID to, UUID docId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<GetVersionDownloadUrl200Response> getVersionDownloadUrl(UUID docId, UUID versionId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<List<Comment>> listComments(UUID docId, UUID versionId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<PagedVersions> listVersions(UUID docId, Integer page, Integer size) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Void> rejectVersion(UUID docId, UUID versionId, @Nullable RejectVersionRequest rejectVersionRequest) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Version> rollbackVersion(UUID docId, UUID versionId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
