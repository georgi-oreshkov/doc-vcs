package com.root.vcsbackend.user.service;

import com.root.vcsbackend.model.UpdateUserProfileRequest;
import com.root.vcsbackend.shared.exception.AppException;
import com.root.vcsbackend.shared.security.JwtPrincipal;
import com.root.vcsbackend.user.domain.UserProfileEntity;
import com.root.vcsbackend.user.mapper.UserMapper;
import com.root.vcsbackend.user.persistence.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserProfileRepository userProfileRepository;
    private final UserMapper userMapper;

    /**
     * Returns the local profile for the current user.
     * Creates it from the JWT claims on first access (Keycloak sync).
     */
    @Transactional
    public UserProfileEntity getOrCreateProfile(JwtPrincipal principal) {
        return userProfileRepository.findById(principal.userId())
            .orElseGet(() -> userProfileRepository.save(
                UserProfileEntity.builder()
                    .id(principal.userId())
                    .email(principal.email())
                    .name(principal.name())
                    .build()
            ));
    }

    @Transactional
    public UserProfileEntity updateProfile(UUID userId, UpdateUserProfileRequest req) {
        UserProfileEntity entity = userProfileRepository.findById(userId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User profile not found"));
        userMapper.applyUpdate(req, entity);
        return userProfileRepository.save(entity);
    }
}
