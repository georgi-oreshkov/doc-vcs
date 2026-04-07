package com.root.vcsbackend.request.web;

import com.root.vcsbackend.api.RequestsApi;
import com.root.vcsbackend.model.ActionRequestRequest;
import com.root.vcsbackend.model.CreateForkRequestRequest;
import com.root.vcsbackend.model.ForkRequest;
import com.root.vcsbackend.request.mapper.RequestMapper;
import com.root.vcsbackend.request.service.RequestService;
import com.root.vcsbackend.shared.security.SecurityHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RequestsController implements RequestsApi {

    private final RequestService requestService;
    private final RequestMapper requestMapper;
    private final SecurityHelper securityHelper;

    @Override
    public ResponseEntity<Void> actionRequest(UUID requestId, ActionRequestRequest req) {
        requestService.actionRequest(requestId, req, securityHelper.currentUser().userId());
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> cancelRequest(UUID requestId) {
        requestService.cancelRequest(requestId, securityHelper.currentUser().userId());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ForkRequest> createForkRequest(CreateForkRequestRequest req) {
        UUID callerId = securityHelper.currentUser().userId();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(requestMapper.toDto(requestService.createForkRequest(req, callerId)));
    }

    @Override
    public ResponseEntity<List<ForkRequest>> listRequests(@Nullable String type, @Nullable String status) {
        UUID callerId = securityHelper.currentUser().userId();
        List<ForkRequest> requests = requestService.listRequests(callerId, type, status).stream()
            .map(requestMapper::toDto)
            .toList();
        return ResponseEntity.ok(requests);
    }
}
