package com.root.vcsbackend.version.service;

import com.root.vcsbackend.shared.s3.S3PresignService;
import com.root.vcsbackend.version.persistence.CommentRepository;
import com.root.vcsbackend.version.persistence.VersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class VersionService {

    private final VersionRepository versionRepository;
    private final CommentRepository commentRepository;
    private final S3PresignService s3PresignService;
    private final ApplicationEventPublisher events;

    // TODO: implement version operations
    // createVersion(UUID docId, CreateVersionRequest req, UUID callerId)
    // getVersion(UUID versionId)
    // listVersions(UUID docId)
    // approveVersion(UUID versionId, UUID callerId)  ← publishes event
    // rejectVersion(UUID versionId, UUID callerId)
    // addComment(UUID versionId, AddCommentRequest req, UUID callerId)
    // getDiff(UUID versionId, UUID compareToId)
}

