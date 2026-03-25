package com.root.vcsbackend.document.web;

import com.root.vcsbackend.api.DocumentsApi;
import com.root.vcsbackend.document.service.DocumentService;
import com.root.vcsbackend.model.CreateDocumentRequest;
import com.root.vcsbackend.model.Document;
import com.root.vcsbackend.model.PagedDocuments;
import com.root.vcsbackend.model.S3UploadResponse;
import com.root.vcsbackend.shared.security.CurrentUser;
import com.root.vcsbackend.shared.security.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DocumentsController implements DocumentsApi {

    private final DocumentService documentService;

    @Override
    public ResponseEntity<S3UploadResponse> createDocument(UUID orgId, CreateDocumentRequest createDocumentRequest) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Void> deleteDocument(UUID docId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Document> getDocument(UUID docId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<List<Document>> getMyDocuments() {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<PagedDocuments> listDocuments(
            UUID orgId,
            @Nullable UUID categoryId,
            @Nullable UUID authorId,
            @Nullable String status,
            @Nullable String name,
            Integer page,
            Integer size) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Document> updateDocument(UUID docId, CreateDocumentRequest createDocumentRequest) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
