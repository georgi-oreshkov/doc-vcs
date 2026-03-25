package com.root.vcsbackend.user.api;

import com.root.vcsbackend.user.domain.UserProfileEntity;
import com.root.vcsbackend.user.persistence.UserProfileRepository;
import lombok.RequiredArgsConstructor;
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
        // TODO: implement — fetch or throw 404 AppException
        return null;
    }
}

