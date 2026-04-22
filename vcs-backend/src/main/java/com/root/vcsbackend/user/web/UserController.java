package com.root.vcsbackend.user.web;

import com.root.vcsbackend.api.UserApi;
import com.root.vcsbackend.model.UpdateUserProfileRequest;
import com.root.vcsbackend.model.UserProfile;
import com.root.vcsbackend.model.UserSearchResult;
import com.root.vcsbackend.shared.security.JwtPrincipal;
import com.root.vcsbackend.shared.security.SecurityHelper;
import com.root.vcsbackend.user.mapper.UserMapper;
import com.root.vcsbackend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @GetMapping("/users/search")
    public ResponseEntity<List<UserSearchResult>> searchUsers(@RequestParam String q) {
        return ResponseEntity.ok(
            userService.searchUsers(q).stream()
                .map(u -> {
                    UserSearchResult r = new UserSearchResult();
                    r.setId(u.getId());
                    r.setName(u.getName());
                    r.setEmail(u.getEmail());
                    return r;
                })
                .toList()
        );
    }
}
