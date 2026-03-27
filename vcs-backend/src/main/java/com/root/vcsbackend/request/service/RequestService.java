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
import com.root.vcsbackend.version.api.VersionFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
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

    public ForkRequestEntity createForkRequest(CreateForkRequestRequest req, UUID requesterId) {
        // Validate doc and version exist and version belongs to the doc
        documentFacade.requireExists(req.getDocId());
        if (!versionFacade.existsByDocId(req.getVersionId(), req.getDocId())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Version does not belong to the document");
        }
        ForkRequestEntity entity = requestMapper.toEntity(req, requesterId);
        entity = forkRequestRepository.save(entity);

        // Notify org admins (we notify the doc author here as a proxy for the org)
        UUID authorId = documentFacade.getAuthorId(req.getDocId());
        events.publishEvent(new NotificationEvent(this, authorId, "FORK_REQUEST_CREATED",
            Map.of("requestId", entity.getId(), "docId", req.getDocId())));

        return entity;
    }

    public void actionRequest(UUID requestId, ActionRequestRequest req, UUID callerId) {
        ForkRequestEntity request = resolve(requestId);
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new AppException(HttpStatus.CONFLICT, "Only PENDING requests can be actioned");
        }

        UUID orgId = documentFacade.resolveOrgId(request.getDocId());
        if (!organizationFacade.hasRole(orgId, callerId, "ADMIN", "AUTHOR")) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only ADMIN or AUTHOR can action requests");
        }

        RequestStatus newStatus = Boolean.TRUE.equals(req.getApprove())
            ? RequestStatus.APPROVED : RequestStatus.REJECTED;
        request.setStatus(newStatus);
        forkRequestRepository.save(request);

        String eventType = newStatus == RequestStatus.APPROVED
            ? "FORK_REQUEST_APPROVED" : "FORK_REQUEST_REJECTED";
        events.publishEvent(new NotificationEvent(this, request.getRequesterId(), eventType,
            Map.of("requestId", requestId)));
    }

    public void cancelRequest(UUID requestId, UUID callerId) {
        ForkRequestEntity request = resolve(requestId);
        if (!request.getRequesterId().equals(callerId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You can only cancel your own requests");
        }
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new AppException(HttpStatus.CONFLICT, "Only PENDING requests can be cancelled");
        }
        request.setStatus(RequestStatus.CANCELLED);
        forkRequestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public List<ForkRequestEntity> listRequests(UUID callerId, String statusFilter) {
        // Show requester's own requests
        List<ForkRequestEntity> all = forkRequestRepository.findByRequesterId(callerId);

        if (statusFilter != null && !statusFilter.isBlank()) {
            try {
                RequestStatus status = RequestStatus.valueOf(statusFilter.toUpperCase());
                return all.stream().filter(r -> r.getStatus() == status).toList();
            } catch (IllegalArgumentException e) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Invalid status: " + statusFilter);
            }
        }
        return all;
    }

    private ForkRequestEntity resolve(UUID requestId) {
        return forkRequestRepository.findById(requestId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Request not found: " + requestId));
    }
}
