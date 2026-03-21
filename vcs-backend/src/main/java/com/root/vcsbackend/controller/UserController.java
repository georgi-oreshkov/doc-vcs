package com.root.vcsbackend.controller;

import com.root.vcsbackend.api.UserApi;
import com.root.vcsbackend.model.UpdateUserProfileRequest;
import com.root.vcsbackend.model.UserProfile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController implements UserApi {

    @Override
    public ResponseEntity<UserProfile> getUserProfile() {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<UserProfile> updateUserProfile(UpdateUserProfileRequest updateUserProfileRequest) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}

