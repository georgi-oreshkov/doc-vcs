package com.root.vcsbackend.version.api;

import com.root.vcsbackend.shared.exception.AppException;
import com.root.vcsbackend.version.domain.VersionEntity;
import com.root.vcsbackend.version.domain.VersionStatus;
import com.root.vcsbackend.version.persistence.VersionRepository;
import com.root.vcsbackend.version.service.VersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Public API of the version module. Only this class should be referenced by other modules.
 * Used by: request module (to resolve a version before creating a fork request),
 *          document module (to create the initial version on document creation).
 */
@Component
@RequiredArgsConstructor
public class VersionFacade {

    private final VersionRepository versionRepository;
    private final VersionService versionService; 

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

    /**
     * Creates version 1 for a newly created document. Called by DocumentService.
     * Returns a {@link VersionSummary} so no internal domain type crosses the module boundary.
     */
    @Transactional
    public VersionSummary createInitialVersion(UUID docId) {
        VersionEntity version = VersionEntity.builder()
            .docId(docId)
            .versionNumber(1)
            .status(VersionStatus.DRAFT)
            .isDraft(true)
            .build();
        VersionEntity saved = versionRepository.save(version);
        return new VersionSummary(saved.getId(), saved.getVersionNumber());
    }

    // --- ADDED FOR APPROVALS TAB INTEGRATION ---
    public void approveVersion(UUID docId, UUID versionId, UUID callerId) {
        versionService.approveVersion(docId, versionId, callerId);
    }

    public void rejectVersion(UUID docId, UUID versionId, UUID callerId, String reason) {
        versionService.rejectVersion(docId, versionId, callerId, null);
    }
}