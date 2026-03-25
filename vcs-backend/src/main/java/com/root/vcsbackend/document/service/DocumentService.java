package com.root.vcsbackend.document.service;

import com.root.vcsbackend.document.persistence.CategoryRepository;
import com.root.vcsbackend.document.persistence.DocumentRepository;
import com.root.vcsbackend.shared.s3.S3PresignService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final CategoryRepository categoryRepository;
    private final S3PresignService s3PresignService;
    private final ApplicationEventPublisher events;

    // TODO: implement document operations
    // @PreAuthorize("@orgRoleEvaluator.hasRole(#orgId, authentication, 'AUTHOR', 'ADMIN')")
    // createDocument(UUID orgId, CreateDocumentRequest req, UUID callerId)

    // @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#docId, authentication)")
    // @Transactional(readOnly = true)
    // getDocument(UUID docId)

    // listDocuments(UUID orgId, ...)
    // updateDocument(UUID docId, ...)
    // deleteDocument(UUID docId, ...)
}

