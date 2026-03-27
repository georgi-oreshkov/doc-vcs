package com.root.vcsbackend.user.api;

import com.root.vcsbackend.shared.exception.AppException;
import com.root.vcsbackend.user.domain.UserProfileEntity;
import com.root.vcsbackend.user.persistence.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Public API of the user module. Only this class should be used by other modules.
 */
@Component
@RequiredArgsConstructor
public class UserFacade {

    private final UserProfileRepository userProfileRepository;

    public UserProfileEntity resolveUser(UUID userId) {
        return userProfileRepository.findById(userId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                "User not found: " + userId));
    }
}
