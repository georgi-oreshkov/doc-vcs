package com.root.vcsbackend.version.persistence;

import com.root.vcsbackend.version.domain.VersionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VersionRepository extends JpaRepository<VersionEntity, UUID> {

    List<VersionEntity> findByDocIdOrderByVersionNumberDesc(UUID docId);

    Page<VersionEntity> findByDocIdOrderByVersionNumberDesc(UUID docId, Pageable pageable);

    Optional<VersionEntity> findTopByDocIdOrderByVersionNumberDesc(UUID docId);
}
