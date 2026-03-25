package com.root.vcsbackend.request.service;

import com.root.vcsbackend.request.persistence.ForkRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class RequestService {

    private final ForkRequestRepository forkRequestRepository;
    private final ApplicationEventPublisher events;

    // TODO: implement request operations
    // createForkRequest(UUID docId, CreateForkRequestRequest req, UUID requesterId)
    // actionRequest(UUID requestId, ActionRequestRequest req, UUID callerId) ← approve/reject
    // listRequests(UUID docId)
}

