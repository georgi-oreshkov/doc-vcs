package com.root.vcsbackend.version.persistence;

import com.root.vcsbackend.version.domain.CommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<CommentEntity, UUID> {

    List<CommentEntity> findByVersionIdOrderByCreatedAtAsc(UUID versionId);
}

