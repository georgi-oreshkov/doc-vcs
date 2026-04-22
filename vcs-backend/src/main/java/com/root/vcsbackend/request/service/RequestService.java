package com.root.vcsbackend.request.service;

import com.root.vcsbackend.document.api.DocumentFacade;
import com.root.vcsbackend.model.ActionRequestRequest;
import com.root.vcsbackend.model.CreateForkRequestRequest;
import com.root.vcsbackend.notification.api.NotificationEvent;
import com.root.vcsbackend.organization.api.OrganizationFacade;
import com.root.vcsbackend.request.domain.ForkRequestEntity;
import com.root.vcsbackend.request.domain.ForkRequestEntity.RequestStatus;
import com.root.vcsbackend.request.mapper.RequestMapper;
import com.root.vcsbackend.request.persistence.ForkRequestRepository;
import com.root.vcsbackend.shared.exception.AppException;
import com.root.vcsbackend.version.api.ReviewRequestedEvent;
import com.root.vcsbackend.version.api.VersionFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class RequestService {

    private final ForkRequestRepository forkRequestRepository;
    private final ApplicationEventPublisher events;
    private final DocumentFacade documentFacade;
    private final VersionFacade versionFacade;
    private final OrganizationFacade organizationFacade;
    private final RequestMapper requestMapper;

    // 1. LISTEN TO REVIEW REQUESTS AND CREATE A CARD IN THE APPROVALS TAB
    @EventListener
    public void onReviewRequested(ReviewRequestedEvent event) {
        ForkRequestEntity req = new ForkRequestEntity();
        req.setDocId(event.docId());
        req.setVersionId(event.versionId());
        req.setRequesterId(event.requesterId());
        req.setStatus(RequestStatus.PENDING);
        forkRequestRepository.save(req);
    }

    @PreAuthorize("@orgRoleEvaluator.isDocumentMember(#req.docId, authentication)")
    public ForkRequestEntity createForkRequest(CreateForkRequestRequest req, UUID requesterId) {
        documentFacade.requireExists(req.getDocId());
        if (!versionFacade.existsByDocId(req.getVersionId(), req.getDocId())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Version does not belong to the document");
        }
        ForkRequestEntity entity = requestMapper.toEntity(req, requesterId);
        entity = forkRequestRepository.save(entity);

        UUID authorId = documentFacade.getAuthorId(req.getDocId());
        UUID orgId = documentFacade.resolveOrgId(req.getDocId());
        events.publishEvent(new NotificationEvent(this, authorId, "FORK_REQUEST_CREATED",
            Map.of("documentId", req.getDocId(), "organizationId", orgId, "requestId", entity.getId(), "message", "A new fork request was created.")));
        return entity;
    }

    // 2. APPROVING THE REQUEST ALSO APPROVES THE VERSION
    @PreAuthorize("isAuthenticated()")
    public void actionRequest(UUID requestId, ActionRequestRequest req, UUID callerId) {
        ForkRequestEntity request = resolve(requestId);
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new AppException(HttpStatus.CONFLICT, "Only PENDING requests can be actioned");
        }

        UUID orgId = documentFacade.resolveOrgId(request.getDocId());
        if (!organizationFacade.hasRole(orgId, callerId, "ADMIN", "AUTHOR", "REVIEWER")) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not authorized to action requests");
        }

        RequestStatus newStatus = Boolean.TRUE.equals(req.getApprove()) ? RequestStatus.APPROVED : RequestStatus.REJECTED;
        request.setStatus(newStatus);
        forkRequestRepository.save(request);

        // This physically approves or rejects the file version!
        if (newStatus == RequestStatus.APPROVED) {
            versionFacade.approveVersion(request.getDocId(), request.getVersionId(), callerId);
        } else {
            versionFacade.rejectVersion(request.getDocId(), request.getVersionId(), callerId, "Rejected via approval tab");
        }

        String eventType = newStatus == RequestStatus.APPROVED ? "FORK_REQUEST_APPROVED" : "FORK_REQUEST_REJECTED";
        events.publishEvent(new NotificationEvent(this, request.getRequesterId(), eventType,
            Map.of("documentId", request.getDocId(), "organizationId", orgId, "requestId", requestId, "message", "Your review request was actioned.")));
    }

    @PreAuthorize("isAuthenticated()")
    public void cancelRequest(UUID requestId, UUID callerId) {
        ForkRequestEntity request = resolve(requestId);
        if (!request.getRequesterId().equals(callerId)) throw new AppException(HttpStatus.FORBIDDEN, "You can only cancel your own requests");
        if (request.getStatus() != RequestStatus.PENDING) throw new AppException(HttpStatus.CONFLICT, "Only PENDING requests can be cancelled");
        request.setStatus(RequestStatus.CANCELLED);
        forkRequestRepository.save(request);
    }

    // 3. SHOW REVIEWERS THEIR TEAM'S REQUESTS
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<ForkRequestEntity> listRequests(UUID callerId, String typeFilter, String statusFilter) {
        List<ForkRequestEntity> all = forkRequestRepository.findAll().stream()
            .filter(r -> {
                // Return if the user created it, OR if the user is an admin/reviewer in the doc's org
                if (r.getRequesterId().equals(callerId)) return true;
                try {
                    UUID orgId = documentFacade.resolveOrgId(r.getDocId());
                    return organizationFacade.hasRole(orgId, callerId, "ADMIN", "REVIEWER");
                } catch (Exception e) {
                    return false;
                }
            }).toList();

        if (statusFilter != null && !statusFilter.isBlank()) {
            try {
                RequestStatus status = RequestStatus.valueOf(statusFilter.strip().toUpperCase());
                return all.stream().filter(r -> r.getStatus() == status).toList();
            } catch (IllegalArgumentException e) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Invalid status: " + statusFilter);
            }
        }
        return all;
    }

    private ForkRequestEntity resolve(UUID requestId) {
        return forkRequestRepository.findById(requestId).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Request not found: " + requestId));
    }
}