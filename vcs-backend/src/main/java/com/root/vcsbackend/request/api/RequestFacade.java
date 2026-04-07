package com.root.vcsbackend.request.api;

import com.root.vcsbackend.request.domain.ForkRequestEntity;
import com.root.vcsbackend.request.persistence.ForkRequestRepository;
import com.root.vcsbackend.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Public API of the request module.
 * <p>
 * Only this class should be referenced by other modules — never
 * {@code ForkRequestEntity}, {@code ForkRequestRepository}, or
 * {@code RequestService} directly.
 * <p>
 * Exposes {@link ForkRequestSummary} records instead of raw JPA entities so that
 * no internal domain type crosses the Modulith module boundary.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestFacade {

    private final ForkRequestRepository forkRequestRepository;

    /**
     * Returns a summary of the fork request by ID.
     * Throws 404 if not found.
     */
    public ForkRequestSummary getRequest(UUID requestId) {
        return toSummary(resolve(requestId));
    }

    /**
     * Returns all fork requests associated with a document.
     * Useful for, e.g., checking whether an open fork request exists before
     * allowing a document deletion.
     */
    public List<ForkRequestSummary> listByDocument(UUID docId) {
        return forkRequestRepository.findByDocId(docId).stream()
                .map(RequestFacade::toSummary)
                .toList();
    }

    /**
     * Returns {@code true} if a PENDING fork request exists for the given document.
     * Convenience helper for guard-checks in other modules.
     */
    public boolean hasPendingRequest(UUID docId) {
        return forkRequestRepository.findByDocId(docId).stream()
                .anyMatch(r -> r.getStatus() == ForkRequestEntity.RequestStatus.PENDING);
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private ForkRequestEntity resolve(UUID requestId) {
        return forkRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Fork request not found: " + requestId));
    }

    private static ForkRequestSummary toSummary(ForkRequestEntity e) {
        return new ForkRequestSummary(
                e.getId(),
                e.getRequesterId(),
                e.getDocId(),
                e.getVersionId(),
                e.getStatus(),
                e.getCreatedAt()
        );
    }
}

