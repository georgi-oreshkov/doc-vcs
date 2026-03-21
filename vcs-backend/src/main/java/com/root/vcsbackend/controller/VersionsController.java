package com.root.vcsbackend.controller;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class VersionsController implements VersionsApi {

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

