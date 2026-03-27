package com.root.vcsbackend.document;

import com.root.vcsbackend.document.domain.DocumentEntity;
import com.root.vcsbackend.document.persistence.DocumentRepository;
import com.root.vcsbackend.shared.security.DocumentOrgLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Implements DocumentOrgLookup (defined in shared) using the document module's
 * own repository. This keeps shared free of compile dependencies on document.
 */
@Component
@RequiredArgsConstructor
class DocumentOrgLookupAdapter implements DocumentOrgLookup {

    private final DocumentRepository documentRepository;

    @Override
    public Optional<UUID> findOrgId(UUID docId) {
        return documentRepository.findById(docId)
            .map(DocumentEntity::getOrgId);
    }
}

