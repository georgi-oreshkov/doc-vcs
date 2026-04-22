package com.root.vcsbackend.user.persistence;

import com.root.vcsbackend.user.domain.UserProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, UUID> {

    Optional<UserProfileEntity> findByEmail(String email);

    @Query("SELECT u FROM UserProfileEntity u WHERE LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<UserProfileEntity> searchByNameOrEmail(@Param("q") String q);
}

