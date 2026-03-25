package com.root.vcsbackend.user.persistence;

import com.root.vcsbackend.user.domain.UserProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, UUID> {

    Optional<UserProfileEntity> findByEmail(String email);
}

