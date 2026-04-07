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
 * <p>
 * No internal domain type ({@link DocumentEntity}) is returned from any public method —
 * callers that need document data receive a {@link DocumentSummary} record instead.
 */
@Component
@RequiredArgsConstructor
public class DocumentFacade {

    private final DocumentRepository documentRepository;

    // ── Read ─────────────────────────────────────────────────────────────────

    /** Validates that a document exists; throws 404 if not. */
    public void requireExists(UUID docId) {
        resolveDocument(docId);
    }

    /**
     * Returns a lightweight summary of the document for cross-module use.
     * Exposes only the fields other modules legitimately need.
     */
    public DocumentSummary getDocumentSummary(UUID docId) {
        DocumentEntity doc = resolveDocument(docId);
        return new DocumentSummary(
                doc.getId(),
                doc.getOrgId(),
                doc.getAuthorId(),
                doc.getName(),
                doc.getLatestVersionId(),
                doc.getLatestApprovedVersionId()
        );
    }

    /** Returns the owning org ID without exposing {@link DocumentEntity}. */
    public UUID resolveOrgId(UUID docId) {
        return resolveDocument(docId).getOrgId();
    }

    /** Returns the author ID without exposing {@link DocumentEntity}. */
    public UUID getAuthorId(UUID docId) {
        return resolveDocument(docId).getAuthorId();
    }

    // ── Write ────────────────────────────────────────────────────────────────

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

    /** Called when a non-draft version is submitted for review. */
    @Transactional
    public void updateStatusToPendingReview(UUID docId) {
        DocumentEntity doc = resolveDocument(docId);
        doc.setStatus(DocumentEntity.DocumentStatus.PENDING_REVIEW);
        documentRepository.save(doc);
    }

    /** Called when a version is approved. */
    @Transactional
    public void updateStatusToApproved(UUID docId) {
        DocumentEntity doc = resolveDocument(docId);
        doc.setStatus(DocumentEntity.DocumentStatus.APPROVED);
        documentRepository.save(doc);
    }

    /** Called when a version is rejected. */
    @Transactional
    public void updateStatusToRejected(UUID docId) {
        DocumentEntity doc = resolveDocument(docId);
        doc.setStatus(DocumentEntity.DocumentStatus.REJECTED);
        documentRepository.save(doc);
    }

    // ── Internal helper — must NOT be called by other modules ────────────────

    /**
     * Private: returns the full {@link DocumentEntity} for use only within this facade.
     * Never expose this to other modules — use {@link #getDocumentSummary} instead.
     */
    private DocumentEntity resolveDocument(UUID docId) {
        return documentRepository.findById(docId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                "Document not found: " + docId));
    }
}
