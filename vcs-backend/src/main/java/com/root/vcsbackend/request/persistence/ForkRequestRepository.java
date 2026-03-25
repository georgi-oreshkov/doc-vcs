package com.root.vcsbackend.request.persistence;

import com.root.vcsbackend.request.domain.ForkRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ForkRequestRepository extends JpaRepository<ForkRequestEntity, UUID> {

    List<ForkRequestEntity> findByDocId(UUID docId);

    List<ForkRequestEntity> findByRequesterId(UUID requesterId);
}

