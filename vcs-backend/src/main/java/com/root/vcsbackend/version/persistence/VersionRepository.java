package com.root.vcsbackend.version.persistence;

import com.root.vcsbackend.version.domain.VersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VersionRepository extends JpaRepository<VersionEntity, UUID> {

    List<VersionEntity> findByDocIdOrderByVersionNumberDesc(UUID docId);
}

