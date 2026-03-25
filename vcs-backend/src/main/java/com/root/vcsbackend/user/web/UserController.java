package com.root.vcsbackend.user.web;

import com.root.vcsbackend.api.UserApi;
import com.root.vcsbackend.model.UpdateUserProfileRequest;
import com.root.vcsbackend.model.UserProfile;
import com.root.vcsbackend.shared.security.CurrentUser;
import com.root.vcsbackend.shared.security.JwtPrincipal;
import com.root.vcsbackend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController implements UserApi {

    private final UserService userService;

    @Override
    public ResponseEntity<UserProfile> getUserProfile() {
        // TODO: implement — call userService, map result
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<UserProfile> updateUserProfile(UpdateUserProfileRequest updateUserProfileRequest) {
        // TODO: implement
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
