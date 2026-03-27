package com.root.vcsbackend.document.api;

import com.root.vcsbackend.document.domain.DocumentEntity;
import com.root.vcsbackend.document.persistence.DocumentRepository;
import com.root.vcsbackend.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Public API of the document module. Only this class should be used by other modules.
 */
@Component
@RequiredArgsConstructor
public class DocumentFacade {

    private final DocumentRepository documentRepository;

    public DocumentEntity resolveDocument(UUID docId) {
        return documentRepository.findById(docId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                "Document not found: " + docId));
    }

    public UUID resolveOrgId(UUID docId) {
        return resolveDocument(docId).getOrgId();
    }

    /** Validates that a document exists; throws 404 if not. Avoids exposing DocumentEntity. */
    public void requireExists(UUID docId) {
        resolveDocument(docId);
    }

    /** Returns the authorId without exposing DocumentEntity across module boundaries. */
    public UUID getAuthorId(UUID docId) {
        return resolveDocument(docId).getAuthorId();
    }

    /** Called by VersionService after creating a new version. */
    @Transactional
    public void updateLatestVersionId(UUID docId, UUID versionId) {
        DocumentEntity doc = resolveDocument(docId);
        doc.setLatestVersionId(versionId);
        documentRepository.save(doc);
    }

    /** Called by VersionService after a version is approved. */
    @Transactional
    public void updateLatestApprovedVersionId(UUID docId, UUID versionId) {
        DocumentEntity doc = resolveDocument(docId);
        doc.setLatestApprovedVersionId(versionId);
        documentRepository.save(doc);
    }
}
