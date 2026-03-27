package com.root.vcsbackend.document.api;

import com.root.vcsbackend.document.domain.DocumentEntity;
import com.root.vcsbackend.document.persistence.DocumentRepository;
import com.root.vcsbackend.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

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
}
