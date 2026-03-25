package com.root.vcsbackend.controller;

import com.root.vcsbackend.api.AuthApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController implements AuthApi {

    @Override
    public ResponseEntity<Void> authLogin() {
        // OAuth2/Keycloak redirect is handled by Spring Security — this endpoint
        // typically never needs a custom body. Override if you need extra logic.
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}

