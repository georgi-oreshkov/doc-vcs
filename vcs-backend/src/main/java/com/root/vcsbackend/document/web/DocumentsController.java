package com.root.vcsbackend.document.web;

import com.root.vcsbackend.api.DocumentsApi;
import com.root.vcsbackend.document.mapper.DocumentMapper;
import com.root.vcsbackend.document.service.DocumentService;
import com.root.vcsbackend.model.CreateDocumentRequest;
import com.root.vcsbackend.model.Document;
import com.root.vcsbackend.model.PagedDocuments;
import com.root.vcsbackend.model.S3UploadResponse;
import com.root.vcsbackend.shared.security.SecurityHelper;
import com.root.vcsbackend.shared.web.PageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DocumentsController implements DocumentsApi {

    private final DocumentService documentService;
    private final DocumentMapper documentMapper;
    private final PageMapper pageMapper;
    private final SecurityHelper securityHelper;

    @Override
    public ResponseEntity<S3UploadResponse> createDocument(UUID orgId, CreateDocumentRequest req) {
        UUID callerId = securityHelper.currentUser().userId();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(documentService.createDocument(orgId, req, callerId));
    }

    @Override
    public ResponseEntity<Void> deleteDocument(UUID docId) {
        documentService.deleteDocument(docId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Document> getDocument(UUID docId) {
        return ResponseEntity.ok(documentMapper.toDto(documentService.getDocument(docId)));
    }

    @Override
    public ResponseEntity<List<Document>> getMyDocuments() {
        UUID callerId = securityHelper.currentUser().userId();
        List<Document> docs = documentService.getMyDocuments(callerId).stream()
            .map(documentMapper::toDto)
            .toList();
        return ResponseEntity.ok(docs);
    }

    @Override
    public ResponseEntity<PagedDocuments> listDocuments(UUID orgId, @Nullable UUID categoryId,
            @Nullable UUID authorId, @Nullable String status, @Nullable String name,
            Integer page, Integer size) {
        var result = documentService.listDocuments(orgId, categoryId, authorId, status, name, page, size);
        var paged = new PagedDocuments()
            .content(result.getContent().stream().map(documentMapper::toDto).toList())
            .meta(pageMapper.toPageMeta(result));
        return ResponseEntity.ok(paged);
    }

    @Override
    public ResponseEntity<Document> updateDocument(UUID docId, CreateDocumentRequest req) {
        return ResponseEntity.ok(documentMapper.toDto(documentService.updateDocument(docId, req)));
    }
}
