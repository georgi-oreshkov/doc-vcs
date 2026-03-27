package com.root.vcsbackend.user.web;

import com.root.vcsbackend.api.UserApi;
import com.root.vcsbackend.model.UpdateUserProfileRequest;
import com.root.vcsbackend.model.UserProfile;
import com.root.vcsbackend.shared.security.JwtPrincipal;
import com.root.vcsbackend.shared.security.SecurityHelper;
import com.root.vcsbackend.user.mapper.UserMapper;
import com.root.vcsbackend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController implements UserApi {

    private final UserService userService;
    private final UserMapper userMapper;
    private final SecurityHelper securityHelper;

    @Override
    public ResponseEntity<UserProfile> getUserProfile() {
        JwtPrincipal principal = securityHelper.currentUser();
        return ResponseEntity.ok(userMapper.toDto(userService.getOrCreateProfile(principal)));
    }

    @Override
    public ResponseEntity<UserProfile> updateUserProfile(UpdateUserProfileRequest req) {
        JwtPrincipal principal = securityHelper.currentUser();
        return ResponseEntity.ok(userMapper.toDto(userService.updateProfile(principal.userId(), req)));
    }
}
