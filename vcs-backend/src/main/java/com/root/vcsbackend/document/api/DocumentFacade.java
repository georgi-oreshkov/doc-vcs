package com.root.vcsbackend.document.api;

import com.root.vcsbackend.document.domain.DocumentEntity;
import com.root.vcsbackend.document.persistence.DocumentRepository;
import lombok.RequiredArgsConstructor;
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
        // TODO: implement — fetch or throw 404 AppException
        return null;
    }
}

