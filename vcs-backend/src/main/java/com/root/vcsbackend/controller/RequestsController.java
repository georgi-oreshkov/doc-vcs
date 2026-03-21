package com.root.vcsbackend.controller;

import com.root.vcsbackend.api.RequestsApi;
import com.root.vcsbackend.model.ActionRequestRequest;
import com.root.vcsbackend.model.CreateForkRequestRequest;
import com.root.vcsbackend.model.ForkRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class RequestsController implements RequestsApi {

    @Override
    public ResponseEntity<Void> actionRequest(UUID requestId, ActionRequestRequest actionRequestRequest) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Void> cancelRequest(UUID requestId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<ForkRequest> createForkRequest(CreateForkRequestRequest createForkRequestRequest) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<List<ForkRequest>> listRequests(@Nullable String type, @Nullable String status) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}

