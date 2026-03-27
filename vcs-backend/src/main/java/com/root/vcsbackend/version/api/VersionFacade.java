package com.root.vcsbackend.version.api;

import com.root.vcsbackend.shared.exception.AppException;
import com.root.vcsbackend.version.domain.VersionEntity;
import com.root.vcsbackend.version.persistence.VersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Public API of the version module. Only this class should be referenced by other modules.
 * Used by: request module (to resolve a version before creating a fork request).
 */
@Component
@RequiredArgsConstructor
public class VersionFacade {

    private final VersionRepository versionRepository;

    public VersionEntity resolveVersion(UUID versionId) {
        return versionRepository.findById(versionId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                "Version not found: " + versionId));
    }

    public boolean existsByDocId(UUID versionId, UUID docId) {
        return versionRepository.findById(versionId)
            .map(v -> v.getDocId().equals(docId))
            .orElse(false);
    }
}

