package com.root.vcsbackend.organization.persistence;

import com.root.vcsbackend.organization.domain.OrgMembershipEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrgMembershipRepository extends JpaRepository<OrgMembershipEntity, UUID> {

    Optional<OrgMembershipEntity> findByOrgIdAndUserId(UUID orgId, UUID userId);

    boolean existsByOrgIdAndUserId(UUID orgId, UUID userId);
}

